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
 * Internal utility that tokenizes outbound LangChain4j text and restores Prism tokens in inbound
 * responses.
 */
final class PrismTextScanner {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[a-zA-Z0-9_-]+>");

  private final List<PrismRulePack> rulePacks;
  private final PrismVault vault;
  private final ObservationRegistry observationRegistry;
  private final PrismMetricsSink metricsSink;
  private final boolean strictMode;

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

  @Nullable String tokenize(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return text;
    }

    List<PiiCandidate> allCandidates = new ArrayList<>();
    for (PrismRulePack pack : rulePacks) {
      for (PiiDetector detector : pack.getDetectors()) {
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
          observationRegistry.observationConfig().observationHandler(null);
        }
      }
    }

    if (allCandidates.isEmpty()) {
      return text;
    }

    allCandidates.sort((a, b) -> Integer.compare(b.start(), a.start()));
    List<PiiCandidate> deduplicated = deduplicate(allCandidates);

    StringBuilder result = new StringBuilder(text);
    int redactedCount = 0;
    for (PiiCandidate candidate : deduplicated) {
      PrismToken token = vault.tokenize(candidate.text(), candidate.label());
      result.replace(candidate.start(), candidate.end(), token.key());
      redactedCount++;
    }

    if (redactedCount > 0) {
      metricsSink.onTokenized(redactedCount);
      observationRegistry.observationConfig().observationHandler(null);
    }

    return result.toString();
  }

  @NonNull String detokenize(@NonNull String text) {
    if (text.isBlank()) {
      return text;
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder(text.length());
    int detokenizedCount = 0;
    while (matcher.find()) {
      String tokenKey = matcher.group();
      String original = vault.detokenize(tokenKey);
      if (original != null) {
        detokenizedCount++;
      }
      matcher.appendReplacement(
          result, Matcher.quoteReplacement(original != null ? original : tokenKey));
    }
    matcher.appendTail(result);
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
}
