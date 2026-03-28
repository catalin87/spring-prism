/*
 * Copyright (c) 2026 Catalin Dordea and Spring Prism Contributors
 *
 * Licensed under the EUPL, Version 1.2 or later (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package io.github.catalin87.prism.examples.demo;

import io.github.catalin87.prism.boot.autoconfigure.PrismRuntimeMetrics;
import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/** Generic tokenize-and-restore engine used by the enterprise demo lab. */
@Component
final class LabRedactionEngine {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[a-zA-Z0-9_-]+>");

  private final PrismRuntimeMetrics runtimeMetrics;

  LabRedactionEngine(PrismRuntimeMetrics runtimeMetrics) {
    this.runtimeMetrics = runtimeMetrics;
  }

  TokenizeResult tokenize(
      String text,
      List<PrismRulePack> rulePacks,
      PrismVault vault,
      PrismFailureMode failureMode,
      String integration,
      String nodeId) {
    if (text == null || text.isBlank()) {
      return new TokenizeResult(text == null ? "" : text, List.of(), List.of(), 0);
    }

    long startedAt = System.nanoTime();
    List<DetectedMatch> matches = new ArrayList<>();
    List<LabTraceEvent> events = new ArrayList<>();
    try {
      for (PrismRulePack rulePack : rulePacks) {
        for (PiiDetector detector : rulePack.getDetectors()) {
          if (!detector.mayMatch(text)) {
            continue;
          }
          try {
            List<PiiCandidate> detected = detector.detect(text);
            if (!detected.isEmpty()) {
              runtimeMetrics.onDetected(
                  rulePack.getName(), detector.getEntityType(), detected.size());
              events.add(
                  traceEvent(
                      nodeId,
                      "detect",
                      "info",
                      "Detected " + detector.getEntityType(),
                      rulePack.getName() + " matched " + detected.size() + " candidate(s)."));
              for (PiiCandidate candidate : detected) {
                matches.add(
                    new DetectedMatch(rulePack.getName(), detector.getEntityType(), candidate));
              }
            }
          } catch (RuntimeException exception) {
            runtimeMetrics.onDetectionError(rulePack.getName(), detector.getEntityType());
            if (failureMode == PrismFailureMode.FAIL_CLOSED) {
              runtimeMetrics.onRequestBlocked(integration);
              throw protectionException(
                  PrismProtectionPhase.DETECT,
                  PrismProtectionReason.DETECTOR_FAILURE,
                  integration,
                  failureMode,
                  exception);
            }
          }
        }
      }
      if (matches.isEmpty()) {
        return new TokenizeResult(text, List.of(), events, 0);
      }

      matches.sort(
          Comparator.comparingInt((DetectedMatch match) -> match.candidate().start()).reversed());
      List<DetectedMatch> deduplicated = deduplicate(matches);
      StringBuilder sanitized = new StringBuilder(text);
      int tokenizedCount = 0;
      for (DetectedMatch match : deduplicated) {
        try {
          PrismToken token = vault.tokenize(match.candidate().text(), match.candidate().label());
          sanitized.replace(match.candidate().start(), match.candidate().end(), token.key());
          tokenizedCount++;
          events.add(
              traceEvent(
                  nodeId,
                  "tokenize",
                  "info",
                  "Tokenized " + match.entityType(),
                  match.rulePackName() + " stored a Prism token in the shared vault."));
        } catch (RuntimeException exception) {
          if (failureMode == PrismFailureMode.FAIL_CLOSED) {
            runtimeMetrics.onRequestBlocked(integration);
            throw protectionException(
                PrismProtectionPhase.TOKENIZE,
                PrismProtectionReason.VAULT_UNAVAILABLE,
                integration,
                failureMode,
                exception);
          }
        }
      }

      if (tokenizedCount > 0) {
        runtimeMetrics.onTokenized(tokenizedCount);
      }
      return new TokenizeResult(
          sanitized.toString(),
          deduplicated.stream().map(match -> match.rulePackName()).distinct().toList(),
          events,
          tokenizedCount);
    } finally {
      runtimeMetrics.onScanDuration(integration, System.nanoTime() - startedAt);
    }
  }

  RestoreResult restore(
      String text,
      PrismVault vault,
      PrismFailureMode failureMode,
      String integration,
      String nodeId) {
    if (text == null || text.isBlank()) {
      return new RestoreResult(text == null ? "" : text, List.of(), 0);
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    StringBuilder restored = new StringBuilder(text.length());
    List<LabTraceEvent> events = new ArrayList<>();
    Map<String, String> cache = new HashMap<>();
    int appendPosition = 0;
    int restoredCount = 0;
    long startedAt = System.nanoTime();
    try {
      while (matcher.find()) {
        restored.append(text, appendPosition, matcher.start());
        String tokenKey = matcher.group();
        String original;
        try {
          original =
              cache.containsKey(tokenKey)
                  ? cache.get(tokenKey)
                  : cacheDetokenize(cache, tokenKey, vault);
        } catch (RuntimeException exception) {
          if (failureMode == PrismFailureMode.FAIL_CLOSED) {
            runtimeMetrics.onResponseBlocked(integration);
            throw protectionException(
                PrismProtectionPhase.RESTORE,
                PrismProtectionReason.RESTORE_FAILURE,
                integration,
                failureMode,
                exception);
          }
          original = null;
        }
        if (original != null) {
          restored.append(original);
          restoredCount++;
          events.add(
              traceEvent(
                  nodeId,
                  "restore",
                  "success",
                  "Restored Prism token",
                  "Fetched original value from the shared Prism vault."));
        } else {
          restored.append(tokenKey);
        }
        appendPosition = matcher.end();
      }
      restored.append(text, appendPosition, text.length());
      if (restoredCount > 0) {
        runtimeMetrics.onDetokenized(restoredCount);
      }
      return new RestoreResult(restored.toString(), events, restoredCount);
    } finally {
      runtimeMetrics.onVaultDetokenizeDuration(integration, System.nanoTime() - startedAt);
    }
  }

  private static List<DetectedMatch> deduplicate(List<DetectedMatch> matches) {
    List<DetectedMatch> deduplicated = new ArrayList<>();
    int previousStart = Integer.MAX_VALUE;
    int previousEnd = Integer.MAX_VALUE;
    for (DetectedMatch match : matches) {
      if (match.candidate().end() <= previousStart || match.candidate().start() >= previousEnd) {
        deduplicated.add(match);
        previousStart = match.candidate().start();
        previousEnd = match.candidate().end();
      }
    }
    return deduplicated;
  }

  private static String cacheDetokenize(
      Map<String, String> cache, String tokenKey, PrismVault vault) {
    String value = vault.detokenize(tokenKey);
    cache.put(tokenKey, value);
    return value;
  }

  private static PrismProtectionException protectionException(
      PrismProtectionPhase phase,
      PrismProtectionReason reason,
      String integration,
      PrismFailureMode failureMode,
      RuntimeException exception) {
    return new PrismProtectionException(
        phase,
        reason,
        integration,
        failureMode,
        "Request blocked: Privacy constraints could not be enforced.",
        exception);
  }

  private static LabTraceEvent traceEvent(
      String nodeId, String phase, String level, String title, String detail) {
    return new LabTraceEvent(Instant.now().toString(), nodeId, phase, level, title, detail);
  }

  record TokenizeResult(
      @NonNull String sanitizedText,
      @NonNull List<String> matchedRulePacks,
      @NonNull List<LabTraceEvent> traceEvents,
      int tokenizedCount) {}

  record RestoreResult(
      @NonNull String restoredText, @NonNull List<LabTraceEvent> traceEvents, int restoredCount) {}

  private record DetectedMatch(
      @NonNull String rulePackName, @NonNull String entityType, @NonNull PiiCandidate candidate) {}
}
