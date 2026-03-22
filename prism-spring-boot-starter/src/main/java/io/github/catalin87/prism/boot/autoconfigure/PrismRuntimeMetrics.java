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

  private String metricKey(String rulePackName, String entityType) {
    return rulePackName + ":" + entityType;
  }
}
