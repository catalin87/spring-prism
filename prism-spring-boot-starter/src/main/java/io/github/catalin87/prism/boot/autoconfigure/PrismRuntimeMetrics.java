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
package io.github.catalin87.prism.boot.autoconfigure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;

/** In-memory runtime metrics collector for dashboard and actuator-style reads. */
public class PrismRuntimeMetrics
    implements io.github.catalin87.prism.spring.ai.advisor.PrismMetricsSink,
        io.github.catalin87.prism.langchain4j.PrismMetricsSink {

  private final AtomicLong tokenizedCount = new AtomicLong();
  private final AtomicLong detokenizedCount = new AtomicLong();
  private final AtomicLong detectionErrorCount = new AtomicLong();
  private final ConcurrentHashMap<String, AtomicLong> detectionCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> durationTotalsNanos =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> durationSamples = new ConcurrentHashMap<>();

  @Override
  public void onDetected(@NonNull String rulePackName, @NonNull String entityType, int count) {
    detectionCounts
        .computeIfAbsent(metricKey(rulePackName, entityType), ignored -> new AtomicLong())
        .addAndGet(count);
  }

  @Override
  public void onDetectionError(@NonNull String rulePackName, @NonNull String entityType) {
    detectionErrorCount.incrementAndGet();
  }

  @Override
  public void onTokenized(int count) {
    tokenizedCount.addAndGet(count);
  }

  @Override
  public void onDetokenized(int count) {
    detokenizedCount.addAndGet(count);
  }

  @Override
  public void onScanDuration(@NonNull String integration, long nanos) {
    recordDuration(integration, "scan", nanos);
  }

  @Override
  public void onVaultTokenizeDuration(@NonNull String integration, long nanos) {
    recordDuration(integration, "vault-tokenize", nanos);
  }

  @Override
  public void onVaultDetokenizeDuration(@NonNull String integration, long nanos) {
    recordDuration(integration, "vault-detokenize", nanos);
  }

  public long tokenizedCount() {
    return tokenizedCount.get();
  }

  public long detokenizedCount() {
    return detokenizedCount.get();
  }

  public long detectionErrorCount() {
    return detectionErrorCount.get();
  }

  /** Returns an immutable snapshot of detection counts grouped by rule pack and entity type. */
  public Map<String, Long> detectionCounts() {
    return detectionCounts.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> e.getValue().get()));
  }

  /**
   * Returns an immutable snapshot of timing totals and sample counts grouped by integration path.
   */
  public Map<String, DurationMetric> durationMetrics() {
    return durationTotalsNanos.entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> {
                  long totalNanos = entry.getValue().get();
                  long samples =
                      durationSamples.getOrDefault(entry.getKey(), new AtomicLong()).get();
                  long averageNanos = samples == 0 ? 0 : totalNanos / samples;
                  return new DurationMetric(samples, totalNanos, averageNanos);
                }));
  }

  private String metricKey(String rulePackName, String entityType) {
    return rulePackName + ":" + entityType;
  }

  private void recordDuration(String integration, String operation, long nanos) {
    String key = integration + ":" + operation;
    durationTotalsNanos.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(nanos);
    durationSamples.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
  }

  /** Immutable timing snapshot for a single Prism runtime operation. */
  public record DurationMetric(long samples, long totalNanos, long averageNanos) {}
}
