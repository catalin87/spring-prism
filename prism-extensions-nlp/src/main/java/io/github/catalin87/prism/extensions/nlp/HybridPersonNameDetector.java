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
package io.github.catalin87.prism.extensions.nlp;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;

/** Hybrid person-name detector combining OpenNLP candidates with heuristic/context scoring. */
public final class HybridPersonNameDetector implements PiiDetector {

  private static final Set<String> DEFAULT_POSITIVE_CONTEXT_TERMS =
      Set.of(
          "name",
          "customer",
          "employee",
          "patient",
          "contact",
          "manager",
          "owner",
          "assignee",
          "requester",
          "user");
  private static final Set<String> DEFAULT_BLOCKED_PHRASES =
      Set.of(
          "spring boot",
          "spring prism",
          "redis cluster",
          "redis sentinel",
          "openai api",
          "azure openai",
          "langchain4j",
          "docker desktop",
          "kubernetes service");
  private static final Set<String> TITLE_PREFIXES = Set.of("mr", "mrs", "ms", "miss", "dr", "prof");
  private static final Pattern TOKEN_SPLIT = Pattern.compile("\\s+");

  private final List<PersonNameBackend> backends;
  private final int confidenceThreshold;
  private final int maxTokens;
  private final boolean allowSingleTokenWithTitle;
  private final Set<String> positiveContextTerms;
  private final Set<String> blockedPhrases;

  /** Creates a detector using the configured candidate backends and scoring settings. */
  public HybridPersonNameDetector(
      List<PersonNameBackend> backends, NlpPersonNameProperties properties) {
    this.backends = List.copyOf(backends);
    this.confidenceThreshold = properties.getConfidenceThreshold();
    this.maxTokens = properties.getMaxTokens();
    this.allowSingleTokenWithTitle = properties.isAllowSingleTokenWithTitle();
    this.positiveContextTerms =
        mergeNormalized(DEFAULT_POSITIVE_CONTEXT_TERMS, properties.getPositiveContextTerms());
    this.blockedPhrases = mergeNormalized(DEFAULT_BLOCKED_PHRASES, properties.getBlockedPhrases());
  }

  @Override
  public boolean mayMatch(@NonNull String text) {
    if (text.isBlank()) {
      return false;
    }
    for (int index = 0; index < text.length(); index++) {
      if (Character.isUpperCase(text.charAt(index))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @NonNull String getEntityType() {
    return "PERSON_NAME";
  }

  @Override
  public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
    if (!mayMatch(text)) {
      return List.of();
    }

    Map<SpanKey, AggregatedMatch> aggregated = new LinkedHashMap<>();
    for (PersonNameBackend backend : backends) {
      for (PersonNameMatch match : backend.detect(text)) {
        if (match.start() < 0 || match.end() > text.length() || match.start() >= match.end()) {
          continue;
        }
        SpanKey key = new SpanKey(match.start(), match.end(), match.text());
        aggregated
            .computeIfAbsent(
                key, ignored -> new AggregatedMatch(match.start(), match.end(), match.text()))
            .add(match);
      }
    }

    if (aggregated.isEmpty()) {
      return List.of();
    }

    List<PiiCandidate> detected = new ArrayList<>();
    for (AggregatedMatch match : aggregated.values()) {
      int score = score(text, match);
      if (score >= confidenceThreshold) {
        detected.add(new PiiCandidate(match.text(), match.start(), match.end(), getEntityType()));
      }
    }
    return detected;
  }

  private int score(String text, AggregatedMatch match) {
    int score = 0;
    String[] tokens = TOKEN_SPLIT.split(match.text().trim());
    boolean hasTitle = hasTitlePrefix(tokens);

    if (match.backends.contains("opennlp")) {
      score += 3;
      if (match.maxConfidence >= 0.75d) {
        score += 1;
      }
    }
    if (match.backends.contains("heuristic")) {
      score += 2;
    }
    if (hasTitle) {
      score += 2;
    }
    if (tokens.length >= 2) {
      score += 2;
    } else if (allowSingleTokenWithTitle && hasTitle) {
      score += 1;
    } else {
      score -= 2;
    }
    if (tokens.length > maxTokens) {
      score -= 3;
    }
    String normalizedText = normalize(match.text());
    if (blockedPhrases.contains(normalizedText)) {
      score -= 5;
    }
    if (containsPositiveContext(text, match.start(), match.end())) {
      score += 2;
    }
    if (looksTechnical(tokens)) {
      score -= 3;
    }
    return score;
  }

  private boolean containsPositiveContext(String text, int start, int end) {
    int contextStart = Math.max(0, start - 40);
    int contextEnd = Math.min(text.length(), end + 40);
    String normalized = normalize(text.substring(contextStart, contextEnd));
    for (String positiveContextTerm : positiveContextTerms) {
      if (normalized.contains(positiveContextTerm)) {
        return true;
      }
    }
    return false;
  }

  private static boolean looksTechnical(String[] tokens) {
    for (String token : tokens) {
      String normalized = normalize(token);
      if (normalized.equals("spring")
          || normalized.equals("redis")
          || normalized.equals("openai")
          || normalized.equals("docker")
          || normalized.equals("kubernetes")
          || normalized.equals("langchain4j")) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasTitlePrefix(String[] tokens) {
    return tokens.length > 0 && TITLE_PREFIXES.contains(normalize(tokens[0]).replace(".", ""));
  }

  private static Set<String> mergeNormalized(
      Collection<String> defaults, Collection<String> additionalTerms) {
    Set<String> merged = new LinkedHashSet<>();
    defaults.forEach(term -> merged.add(normalize(term)));
    additionalTerms.stream().map(HybridPersonNameDetector::normalize).forEach(merged::add);
    return merged;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private record SpanKey(int start, int end, @NonNull String text) {}

  private static final class AggregatedMatch {
    private final int start;
    private final int end;
    private final String text;
    private final Set<String> backends = new LinkedHashSet<>();
    private double maxConfidence;

    private AggregatedMatch(int start, int end, String text) {
      this.start = start;
      this.end = end;
      this.text = text;
    }

    private void add(PersonNameMatch match) {
      backends.add(match.backendId());
      maxConfidence = Math.max(maxConfidence, match.confidence());
    }

    private int start() {
      return start;
    }

    private int end() {
      return end;
    }

    private String text() {
      return text;
    }
  }
}
