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
package io.github.catalin87.prism.langchain4j;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.PrismVaultAvailability;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Internal utility that tokenizes outbound LangChain4j text and restores Prism tokens in inbound
 * responses.
 */
final class PrismTextScanner {

  private static final int LARGE_TEXT_THRESHOLD = 8_192;
  private static final int SEGMENT_SIZE = 4_096;
  private static final int SEGMENT_OVERLAP = 256;
  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[a-zA-Z0-9_-]+>");
  private static final String TOKEN_PREFIX = "<PRISM_";
  private static final String INTEGRATION = PrismMetricsSink.LANGCHAIN4J_INTEGRATION;
  private static final Duration PREFLIGHT_TIMEOUT = Duration.ofMillis(50);

  private final List<PrismRulePack> rulePacks;
  private final PrismVault vault;
  private final ObservationRegistry observationRegistry;
  private final PrismMetricsSink metricsSink;
  private final PrismFailureMode failureMode;

  PrismTextScanner(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode) {
    this(
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        strictMode ? PrismFailureMode.FAIL_CLOSED : PrismFailureMode.FAIL_SAFE);
  }

  PrismTextScanner(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      @NonNull PrismFailureMode failureMode) {
    this.rulePacks = List.copyOf(rulePacks);
    this.vault = vault;
    this.observationRegistry = observationRegistry;
    this.metricsSink = metricsSink;
    this.failureMode = failureMode;
  }

  @Nullable String tokenize(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return text;
    }
    runPreflightIfRequired();

    List<PiiCandidate> allCandidates = new ArrayList<>();
    long scanStartedAt = System.nanoTime();
    try {
      for (PrismRulePack pack : rulePacks) {
        for (PiiDetector detector : pack.getDetectors()) {
          if (!detector.mayMatch(text)) {
            continue;
          }
          try {
            List<PiiCandidate> detected = detectCandidates(detector, text);
            if (!detected.isEmpty()) {
              metricsSink.onDetected(pack.getName(), detector.getEntityType(), detected.size());
              allCandidates.addAll(detected);
            }
          } catch (Exception e) {
            metricsSink.onDetectionError(pack.getName(), detector.getEntityType());
            if (failureMode == PrismFailureMode.FAIL_CLOSED) {
              metricsSink.onRequestBlocked(INTEGRATION);
              throw protectionException(
                  PrismProtectionPhase.DETECT, PrismProtectionReason.DETECTOR_FAILURE, e);
            }
            observationRegistry.observationConfig().observationHandler(null);
          }
        }
      }
    } finally {
      metricsSink.onScanDuration(INTEGRATION, System.nanoTime() - scanStartedAt);
    }

    if (allCandidates.isEmpty()) {
      return text;
    }

    allCandidates.sort((a, b) -> Integer.compare(b.start(), a.start()));
    List<PiiCandidate> deduplicated = deduplicate(allCandidates);

    int redactedCount = 0;
    long tokenizeStartedAt = System.nanoTime();
    String sanitized;
    try {
      ReplacementResult replacementResult = tokenizeCandidates(text, deduplicated);
      redactedCount = replacementResult.replacementCount();
      sanitized = replacementResult.text();
    } catch (RuntimeException exception) {
      if (failureMode == PrismFailureMode.FAIL_CLOSED) {
        metricsSink.onRequestBlocked(INTEGRATION);
        throw protectionException(
            PrismProtectionPhase.TOKENIZE,
            inferVaultReason(exception, PrismProtectionReason.POLICY_BLOCK),
            exception);
      }
      sanitized = text;
    } finally {
      metricsSink.onVaultTokenizeDuration(INTEGRATION, System.nanoTime() - tokenizeStartedAt);
    }

    if (redactedCount > 0) {
      metricsSink.onTokenized(redactedCount);
      observationRegistry.observationConfig().observationHandler(null);
    }

    return sanitized;
  }

  @NonNull String detokenize(@NonNull String text) {
    if (text.isBlank()) {
      return text;
    }
    if (!containsPossibleToken(text)) {
      return text;
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder(text.length());
    Map<String, String> detokenizeCache = new HashMap<>();
    int detokenizedCount = 0;
    long detokenizeStartedAt = System.nanoTime();
    try {
      int appendPosition = 0;
      while (matcher.find()) {
        result.append(text, appendPosition, matcher.start());
        String tokenKey = text.substring(matcher.start(), matcher.end());
        String original =
            detokenizeCache.containsKey(tokenKey)
                ? detokenizeCache.get(tokenKey)
                : detokenizeCache(tokenKey, detokenizeCache);
        if (original != null) {
          detokenizedCount++;
        }
        result.append(original != null ? original : tokenKey);
        appendPosition = matcher.end();
      }
      if (appendPosition == 0) {
        return text;
      }
      result.append(text, appendPosition, text.length());
    } catch (RuntimeException exception) {
      if (failureMode == PrismFailureMode.FAIL_CLOSED) {
        metricsSink.onResponseBlocked(INTEGRATION);
        throw protectionException(
            PrismProtectionPhase.DETOKENIZE,
            inferVaultReason(exception, PrismProtectionReason.RESTORE_FAILURE),
            exception);
      }
      return text;
    } finally {
      metricsSink.onVaultDetokenizeDuration(INTEGRATION, System.nanoTime() - detokenizeStartedAt);
    }
    if (detokenizedCount > 0) {
      metricsSink.onDetokenized(detokenizedCount);
    }
    return result.toString();
  }

  private static List<PiiCandidate> deduplicate(List<PiiCandidate> sorted) {
    List<PiiCandidate> result = new ArrayList<>();
    int lastStart = Integer.MAX_VALUE;
    for (PiiCandidate candidate : sorted) {
      if (candidate.end() <= lastStart) {
        result.add(candidate);
        lastStart = candidate.start();
      }
    }
    return result;
  }

  private ReplacementResult tokenizeCandidates(String text, List<PiiCandidate> deduplicated) {
    StringBuilder result = new StringBuilder(text.length());
    Map<TokenCacheKey, PrismToken> tokenizeCache = new HashMap<>();
    int currentIndex = 0;
    int replacementCount = 0;
    for (int index = deduplicated.size() - 1; index >= 0; index--) {
      PiiCandidate candidate = deduplicated.get(index);
      result.append(text, currentIndex, candidate.start());
      PrismToken token =
          tokenizeCache.computeIfAbsent(
              new TokenCacheKey(candidate.text(), candidate.label()),
              ignored -> vault.tokenize(candidate.text(), candidate.label()));
      result.append(token.key());
      currentIndex = candidate.end();
      replacementCount++;
    }
    result.append(text, currentIndex, text.length());
    return new ReplacementResult(result.toString(), replacementCount);
  }

  private static boolean containsPossibleToken(String text) {
    return text.indexOf(TOKEN_PREFIX) >= 0;
  }

  private static List<PiiCandidate> detectCandidates(PiiDetector detector, String text) {
    if (text.length() < LARGE_TEXT_THRESHOLD) {
      return detector.detect(text);
    }

    List<PiiCandidate> detected = new ArrayList<>();
    for (TextSegment segment : segmentText(text)) {
      if (!detector.mayMatch(segment.text())) {
        continue;
      }
      List<PiiCandidate> segmentCandidates = detector.detect(segment.text());
      if (segmentCandidates.isEmpty()) {
        continue;
      }
      for (PiiCandidate candidate : segmentCandidates) {
        detected.add(
            new PiiCandidate(
                candidate.text(),
                candidate.start() + segment.offset(),
                candidate.end() + segment.offset(),
                candidate.label()));
      }
    }
    return detected;
  }

  private static List<TextSegment> segmentText(String text) {
    List<TextSegment> segments = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + SEGMENT_SIZE, text.length());
      if (end < text.length()) {
        end = alignSegmentEnd(text, start, end);
      }
      segments.add(new TextSegment(start, text.substring(start, end)));
      if (end == text.length()) {
        break;
      }
      start = Math.max(end - SEGMENT_OVERLAP, start + 1);
    }
    return segments;
  }

  private static int alignSegmentEnd(String text, int start, int proposedEnd) {
    int searchLimit = Math.min(text.length(), proposedEnd + 128);
    for (int index = proposedEnd; index < searchLimit; index++) {
      char current = text.charAt(index);
      if (Character.isWhitespace(current) || current == '.' || current == ',') {
        return index + 1;
      }
    }
    return proposedEnd;
  }

  private @Nullable String detokenizeCache(String tokenKey, Map<String, String> detokenizeCache) {
    String original = vault.detokenize(tokenKey);
    detokenizeCache.put(tokenKey, original);
    return original;
  }

  private void runPreflightIfRequired() {
    if (failureMode != PrismFailureMode.FAIL_CLOSED
        || !(vault instanceof PrismVaultAvailability availability)) {
      return;
    }
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      Future<?> future = executor.submit(() -> availability.verifyAvailability(PREFLIGHT_TIMEOUT));
      future.get(PREFLIGHT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      executor.shutdownNow();
      metricsSink.onRequestBlocked(INTEGRATION);
      throw protectionException(
          PrismProtectionPhase.PREFLIGHT, PrismProtectionReason.TIMEOUT, exception);
    } catch (InterruptedException exception) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
      metricsSink.onRequestBlocked(INTEGRATION);
      throw protectionException(
          PrismProtectionPhase.PREFLIGHT, PrismProtectionReason.VAULT_UNAVAILABLE, exception);
    } catch (ExecutionException exception) {
      executor.shutdownNow();
      metricsSink.onRequestBlocked(INTEGRATION);
      throw protectionException(
          PrismProtectionPhase.PREFLIGHT,
          PrismProtectionReason.VAULT_UNAVAILABLE,
          exception.getCause() != null ? exception.getCause() : exception);
    } finally {
      executor.shutdownNow();
    }
  }

  private PrismProtectionException protectionException(
      PrismProtectionPhase phase, PrismProtectionReason reason, Throwable cause) {
    return new PrismProtectionException(
        phase,
        reason,
        INTEGRATION,
        failureMode,
        "Request blocked: Privacy constraints could not be enforced.",
        cause);
  }

  private static PrismProtectionReason inferVaultReason(
      Throwable cause, PrismProtectionReason fallback) {
    String message = cause.getMessage();
    if (message != null && message.toLowerCase().contains("redis")) {
      return PrismProtectionReason.VAULT_UNAVAILABLE;
    }
    return fallback;
  }

  private record TextSegment(int offset, @NonNull String text) {}

  private record TokenCacheKey(@NonNull String text, @NonNull String label) {}

  private record ReplacementResult(@NonNull String text, int replacementCount) {}
}
