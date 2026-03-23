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
package io.github.catalin87.prism.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Recursively sanitizes and restores textual values inside JSON-like MCP payloads. */
public final class PrismMcpPayloadSanitizer {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("<PRISM_[a-zA-Z0-9_-]+>");
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};
  private static final Set<String> STRUCTURAL_STRING_KEYS =
      Set.of("jsonrpc", "id", "method", "type", "role", "name", "uri", "mimeType", "model");
  private static final Set<String> JSON_STRING_KEYS = Set.of("arguments", "structuredContent");

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final List<PrismRulePack> rulePacks;
  private final PrismVault vault;
  private final PrismMcpMetricsSink metricsSink;
  private final boolean strictMode;

  /**
   * Creates a payload sanitizer with explicit metrics and strict-mode settings.
   *
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   */
  public PrismMcpPayloadSanitizer(
      @NonNull List<PrismRulePack> rulePacks, @NonNull PrismVault vault) {
    this(rulePacks, vault, PrismMcpMetricsSink.NOOP, false);
  }

  /**
   * Creates a payload sanitizer with explicit metrics and strict-mode settings.
   *
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   * @param metricsSink runtime metrics callback
   * @param strictMode whether detector failures should abort instead of failing open
   */
  public PrismMcpPayloadSanitizer(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull PrismMcpMetricsSink metricsSink,
      boolean strictMode) {
    this.rulePacks = List.copyOf(Objects.requireNonNull(rulePacks, "rulePacks"));
    this.vault = Objects.requireNonNull(vault, "vault");
    this.metricsSink = Objects.requireNonNull(metricsSink, "metricsSink");
    this.strictMode = strictMode;
  }

  /** Returns a recursively sanitized copy of the outbound request payload. */
  public @NonNull Map<String, Object> sanitizeRequest(
      @NonNull Map<String, Object> payload, @NonNull String integration) {
    return castMap(transformValue(payload, integration, true, null));
  }

  /** Returns a recursively restored copy of the inbound response payload. */
  public @NonNull Map<String, Object> restoreResponse(
      @NonNull Map<String, Object> payload, @NonNull String integration) {
    return castMap(transformValue(payload, integration, false, null));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }

  private Object transformValue(
      Object value, String integration, boolean outbound, @Nullable String fieldName) {
    if (value instanceof String text) {
      if (fieldName != null && STRUCTURAL_STRING_KEYS.contains(fieldName)) {
        return text;
      }
      if (fieldName != null && JSON_STRING_KEYS.contains(fieldName)) {
        Object parsed = tryParseStructuredJson(text);
        if (parsed != null) {
          try {
            return objectMapper.writeValueAsString(
                transformValue(parsed, integration, outbound, fieldName));
          } catch (JsonProcessingException exception) {
            if (strictMode) {
              throw new IllegalStateException(
                  "Strict mode blocked Prism MCP processing after JSON serialization failure",
                  exception);
            }
            return outbound
                ? tokenizeString(text, integration)
                : detokenizeString(text, integration);
          }
        }
      }
      return outbound ? tokenizeString(text, integration) : detokenizeString(text, integration);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> transformed = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        String key = String.valueOf(entry.getKey());
        transformed.put(key, transformValue(entry.getValue(), integration, outbound, key));
      }
      return transformed;
    }
    if (value instanceof List<?> list) {
      List<Object> transformed = new ArrayList<>(list.size());
      for (Object element : list) {
        transformed.add(transformValue(element, integration, outbound, fieldName));
      }
      return transformed;
    }
    return value;
  }

  private @Nullable Object tryParseStructuredJson(String text) {
    String trimmed = text.trim();
    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
      return null;
    }
    try {
      return trimmed.startsWith("{")
          ? objectMapper.readValue(trimmed, MAP_TYPE)
          : objectMapper.readValue(trimmed, LIST_TYPE);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }

  private String tokenizeString(String text, String integration) {
    if (text.isBlank()) {
      return text;
    }

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
          } catch (RuntimeException exception) {
            metricsSink.onDetectionError(pack.getName(), detector.getEntityType());
            if (strictMode) {
              throw new IllegalStateException(
                  "Strict mode blocked Prism MCP processing after detector failure: "
                      + detector.getEntityType(),
                  exception);
            }
          }
        }
      }
    } finally {
      metricsSink.onScanDuration(integration, System.nanoTime() - scanStartedAt);
    }

    if (allCandidates.isEmpty()) {
      return text;
    }

    allCandidates.sort((left, right) -> Integer.compare(right.start(), left.start()));
    List<PiiCandidate> deduplicated = deduplicate(allCandidates);
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
      metricsSink.onVaultTokenizeDuration(integration, System.nanoTime() - tokenizeStartedAt);
    }
    if (redactedCount > 0) {
      metricsSink.onTokenized(redactedCount);
    }
    return result.toString();
  }

  private String detokenizeString(String text, String integration) {
    if (text.isBlank()) {
      return text;
    }

    Matcher matcher = TOKEN_PATTERN.matcher(text);
    StringBuilder result = new StringBuilder(text.length());
    int detokenizedCount = 0;
    long startedAt = System.nanoTime();
    try {
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
    } finally {
      metricsSink.onVaultDetokenizeDuration(integration, System.nanoTime() - startedAt);
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
}
