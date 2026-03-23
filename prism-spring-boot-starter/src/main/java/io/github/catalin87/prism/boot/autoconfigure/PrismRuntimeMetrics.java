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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
  private final Deque<AuditEvent> auditEvents = new ArrayDeque<>();
  private final Deque<HistorySample> historySamples = new ArrayDeque<>();
  private final int auditRetentionLimit;
  private final int historyRetentionLimit;

  public PrismRuntimeMetrics() {
    this(12, 120);
  }

  public PrismRuntimeMetrics(int auditRetentionLimit, int historyRetentionLimit) {
    this.auditRetentionLimit = Math.max(1, auditRetentionLimit);
    this.historyRetentionLimit = Math.max(10, historyRetentionLimit);
  }

  @Override
  public void onDetected(@NonNull String rulePackName, @NonNull String entityType, int count) {
    detectionCounts
        .computeIfAbsent(metricKey(rulePackName, entityType), ignored -> new AtomicLong())
        .addAndGet(count);
    recordEvent("detected", entityType, count, rulePackName);
  }

  @Override
  public void onDetectionError(@NonNull String rulePackName, @NonNull String entityType) {
    detectionErrorCount.incrementAndGet();
    recordEvent("error", entityType, 1, rulePackName);
  }

  @Override
  public void onTokenized(int count) {
    tokenizedCount.addAndGet(count);
    recordEvent("tokenized", "PRISM_TOKEN", count, "runtime");
  }

  @Override
  public void onDetokenized(int count) {
    detokenizedCount.addAndGet(count);
    recordEvent("detokenized", "PRISM_TOKEN", count, "runtime");
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

  /** Returns a recent bounded snapshot of masked Prism activity events. */
  public synchronized java.util.List<AuditEvent> recentAuditEvents() {
    return java.util.List.copyOf(auditEvents);
  }

  /** Returns the maximum number of masked audit events retained in memory. */
  public int auditRetentionLimit() {
    return auditRetentionLimit;
  }

  /** Returns a recent bounded server-side history of aggregate dashboard snapshots. */
  public synchronized java.util.List<HistorySample> recentHistorySamples() {
    return java.util.List.copyOf(historySamples);
  }

  /** Returns the maximum number of server-side history samples retained in memory. */
  public int historyRetentionLimit() {
    return historyRetentionLimit;
  }

  /** Captures a single aggregate sample so dashboard trends survive browser refreshes. */
  public synchronized void captureHistorySample(String vaultType) {
    long totalDetections = detectionCounts().values().stream().mapToLong(Long::longValue).sum();
    long detectionErrors = detectionErrorCount();
    long tokenized = tokenizedCount();
    long detokenized = detokenizedCount();
    historySamples.addLast(
        new HistorySample(
            Instant.now().toString(),
            totalDetections,
            detectionErrors,
            averageMilliseconds("spring-ai:scan", "langchain4j:scan"),
            tokenized,
            detokenized,
            Math.max(0, tokenized - detokenized),
            vaultType));
    while (historySamples.size() > historyRetentionLimit) {
      historySamples.removeFirst();
    }
  }

  private String metricKey(String rulePackName, String entityType) {
    return rulePackName + ":" + entityType;
  }

  private void recordDuration(String integration, String operation, long nanos) {
    String key = integration + ":" + operation;
    durationTotalsNanos.computeIfAbsent(key, ignored -> new AtomicLong()).addAndGet(nanos);
    durationSamples.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
  }

  private double averageMilliseconds(String... keys) {
    for (String key : keys) {
      AtomicLong totalNanos = durationTotalsNanos.get(key);
      AtomicLong samples = durationSamples.get(key);
      if (totalNanos != null && samples != null && samples.get() > 0) {
        return (totalNanos.get() / (double) samples.get()) / 1_000_000d;
      }
    }
    return 0d;
  }

  private synchronized void recordEvent(String action, String subject, long count, String source) {
    auditEvents.addFirst(new AuditEvent(Instant.now().toString(), action, subject, count, source));
    while (auditEvents.size() > auditRetentionLimit) {
      auditEvents.removeLast();
    }
  }

  /** Immutable timing snapshot for a single Prism runtime operation. */
  public record DurationMetric(long samples, long totalNanos, long averageNanos) {}

  /** Immutable masked audit event summarizing recent Prism activity without exposing raw PII. */
  public record AuditEvent(
      @NonNull String timestamp,
      @NonNull String action,
      @NonNull String subject,
      long count,
      @NonNull String source) {}
}
