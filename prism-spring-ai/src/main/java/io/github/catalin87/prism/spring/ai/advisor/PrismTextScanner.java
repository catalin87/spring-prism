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
package io.github.catalin87.prism.spring.ai.advisor;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Internal utility for scanning text blocks to replace detected PII with vault tokens (tokenize)
 * and to replace vault tokens in LLM responses with the original values (detokenize).
 */
class PrismTextScanner {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[a-zA-Z0-9_-]+>");
  private static final String INTEGRATION = PrismMetricsSink.SPRING_AI_INTEGRATION;

  private final List<PrismRulePack> rulePacks;
  private final PrismVault vault;
  private final ObservationRegistry observationRegistry;
  private final PrismMetricsSink metricsSink;
  private final boolean strictMode;

  PrismTextScanner(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink) {
    this(rulePacks, vault, observationRegistry, metricsSink, false);
  }

  PrismTextScanner(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode) {
    this.rulePacks = List.copyOf(rulePacks);
    this.vault = vault;
    this.observationRegistry = observationRegistry;
    this.metricsSink = metricsSink;
    this.strictMode = strictMode;
  }

  /**
   * Scans the given text for PII using all registered rule packs. Discovered PII values are
   * replaced with their pseudonymized vault tokens.
   *
   * @param text The raw user or system prompt text.
   * @return Sanitized text with all PII replaced by vault tokens.
   */
  @Nullable String tokenize(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return text;
    }

    // Collect all candidates across all detectors in all packs
    List<PiiCandidate> allCandidates = new ArrayList<>();
    long scanStartedAt = System.nanoTime();
    try {
      for (PrismRulePack pack : rulePacks) {
        for (PiiDetector detector : pack.getDetectors()) {
          if (!detector.mayMatch(text)) {
            continue;
          }
          try {
            List<PiiCandidate> detected = detector.detect(text);
            if (!detected.isEmpty()) {
              metricsSink.onDetected(pack.getName(), detector.getEntityType(), detected.size());
              allCandidates.addAll(detected);
            }
          } catch (Exception e) {
            metricsSink.onDetectionError(pack.getName(), detector.getEntityType());
            if (strictMode) {
              throw new IllegalStateException(
                  "Strict mode blocked Prism processing after detector failure: "
                      + detector.getEntityType(),
                  e);
            }
            // Fail-Open: emit metric but do not block the request
            observationRegistry
                .observationConfig()
                .observationHandler(null); // triggers default handler chain
          }
        }
      }
    } finally {
      metricsSink.onScanDuration(INTEGRATION, System.nanoTime() - scanStartedAt);
    }

    if (allCandidates.isEmpty()) {
      return text;
    }

    // Sort by start offset descending so replacements don't shift indices
    allCandidates.sort((a, b) -> Integer.compare(b.start(), a.start()));

    // Deduplicate overlapping spans (keep the largest / first by offset)
    List<PiiCandidate> deduplicated = deduplicate(allCandidates);

    // Replace all spans from right-to-left in the original text
    StringBuilder result = new StringBuilder(text);
    int redactedCount = 0;
    long tokenizeStartedAt = System.nanoTime();
    try {
      for (PiiCandidate candidate : deduplicated) {
        PrismToken token = vault.tokenize(candidate.text(), candidate.label());
        result.replace(candidate.start(), candidate.end(), token.key());
        redactedCount++;
      }
    } finally {
      metricsSink.onVaultTokenizeDuration(INTEGRATION, System.nanoTime() - tokenizeStartedAt);
    }

    if (redactedCount > 0) {
      metricsSink.onTokenized(redactedCount);
      observationRegistry
          .observationConfig()
          .observationHandler(null); // no-op: real metrics emitted via Observation API
    }

    return result.toString();
  }

  /**
   * Scans the given text segment for PRISM vault tokens and replaces each with the original value
   * retrieved from the vault. Tokens that are expired or unknown are left as-is (Fail-Open).
   *
   * @param text The chunk of LLM response text to detokenize.
   * @return Text with all recognised vault tokens replaced by their original PII values.
   */
  @NonNull String detokenize(@NonNull String text) {
    if (text.isBlank()) {
      return text;
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder(text.length());
    int detokenizedCount = 0;
    long detokenizeStartedAt = System.nanoTime();
    try {
      while (matcher.find()) {
        String tokenKey = matcher.group();
        String original = vault.detokenize(tokenKey);
        // Fail-Open: if token not found or expired, keep the token placeholder
        if (original != null) {
          detokenizedCount++;
        }
        matcher.appendReplacement(
            result, Matcher.quoteReplacement(original != null ? original : tokenKey));
      }
      matcher.appendTail(result);
    } finally {
      metricsSink.onVaultDetokenizeDuration(INTEGRATION, System.nanoTime() - detokenizeStartedAt);
    }
    if (detokenizedCount > 0) {
      metricsSink.onDetokenized(detokenizedCount);
    }
    return result.toString();
  }

  /**
   * Remove overlapping candidates, keeping the one with the latest start offset (already sorted).
   */
  private static List<PiiCandidate> deduplicate(List<PiiCandidate> sorted) {
    List<PiiCandidate> result = new ArrayList<>();
    int lastStart = Integer.MAX_VALUE;
    for (PiiCandidate c : sorted) {
      if (c.end() <= lastStart) {
        result.add(c);
        lastStart = c.start();
      }
    }
    return result;
  }
}
