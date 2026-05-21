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

import io.github.catalin87.prism.core.PrismRegistryMetricsSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * In-memory runtime metrics collector for dashboard and actuator-style reads.
 *
 * <p>When a Micrometer {@link MeterRegistry} is supplied (via the three-arg constructor), this
 * class additionally registers three meters:
 *
 * <ul>
 *   <li>{@code prism.registry.swap.count} — counter, incremented on every successful {@code
 *       PrismRulePackRegistry.replace(...)} call.
 *   <li>{@code prism.registry.swap.error.count} — counter, incremented on every failed swap.
 *   <li>{@code prism.scanner.tokenize.duration} — timer, fed by scanner {@code onScanDuration}
 *       callbacks (tagged by {@code integration}).
 * </ul>
 *
 * <p>When no {@link MeterRegistry} is supplied, the class still operates as the in-memory sink for
 * the existing dashboard, but emits nothing to Micrometer. This preserves backward compatibility
 * with hosts that have not configured a metrics backend.
 */
public class PrismRuntimeMetrics
    implements io.github.catalin87.prism.spring.ai.advisor.PrismMetricsSink,
        io.github.catalin87.prism.langchain4j.PrismMetricsSink,
        io.github.catalin87.prism.mcp.PrismMcpMetricsSink,
        PrismRegistryMetricsSink {

  static final String REGISTRY_SWAP_COUNTER = "prism.registry.swap.count";
  static final String REGISTRY_SWAP_ERROR_COUNTER = "prism.registry.swap.error.count";
  static final String SCANNER_TOKENIZE_TIMER = "prism.scanner.tokenize.duration";

  private final AtomicLong tokenizedCount = new AtomicLong();
  private final AtomicLong detokenizedCount = new AtomicLong();
  private final AtomicLong detectionErrorCount = new AtomicLong();
  private final AtomicLong blockedRequestCount = new AtomicLong();
  private final AtomicLong blockedResponseCount = new AtomicLong();
  private final ConcurrentHashMap<String, AtomicLong> detectionCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> durationTotalsNanos =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> durationSamples = new ConcurrentHashMap<>();
  private final Deque<AuditEvent> auditEvents = new ArrayDeque<>();
  private final Deque<HistorySample> historySamples = new ArrayDeque<>();
  private final int auditRetentionLimit;
  private final int historyRetentionLimit;

  private final @Nullable MeterRegistry meterRegistry;
  private final @Nullable Counter registrySwapCounter;
  private final @Nullable Counter registrySwapErrorCounter;
  private final ConcurrentHashMap<String, Timer> tokenizeTimers = new ConcurrentHashMap<>();

  public PrismRuntimeMetrics() {
    this(12, 120);
  }

  public PrismRuntimeMetrics(int auditRetentionLimit, int historyRetentionLimit) {
    this(auditRetentionLimit, historyRetentionLimit, null);
  }

  /**
   * Creates a metrics collector that additionally publishes registry and scanner metrics to {@code
   * meterRegistry} when it is non-null. Passing {@code null} preserves the legacy in-memory-only
   * behaviour.
   */
  public PrismRuntimeMetrics(
      int auditRetentionLimit, int historyRetentionLimit, @Nullable MeterRegistry meterRegistry) {
    this.auditRetentionLimit = Math.max(1, auditRetentionLimit);
    this.historyRetentionLimit = Math.max(10, historyRetentionLimit);
    this.meterRegistry = meterRegistry;
    if (meterRegistry != null) {
      this.registrySwapCounter =
          Counter.builder(REGISTRY_SWAP_COUNTER)
              .description("Number of successful PrismRulePackRegistry.replace(...) swaps.")
              .register(meterRegistry);
      this.registrySwapErrorCounter =
          Counter.builder(REGISTRY_SWAP_ERROR_COUNTER)
              .description("Number of failed PrismRulePackRegistry.replace(...) swaps.")
              .register(meterRegistry);
    } else {
      this.registrySwapCounter = null;
      this.registrySwapErrorCounter = null;
    }
  }

  @Override
  public void onRegistrySwap() {
    if (registrySwapCounter != null) {
      registrySwapCounter.increment();
    }
  }

  @Override
  public void onRegistryError(@NonNull Throwable cause) {
    if (registrySwapErrorCounter != null) {
      registrySwapErrorCounter.increment();
    }
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
    if (meterRegistry != null) {
      tokenizeTimers
          .computeIfAbsent(
              integration,
              key ->
                  Timer.builder(SCANNER_TOKENIZE_TIMER)
                      .description("Duration of PrismTextScanner.tokenize() across integrations.")
                      .tag("integration", key)
                      .register(meterRegistry))
          .record(nanos, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public void onVaultTokenizeDuration(@NonNull String integration, long nanos) {
    recordDuration(integration, "vault-tokenize", nanos);
  }

  @Override
  public void onVaultDetokenizeDuration(@NonNull String integration, long nanos) {
    recordDuration(integration, "vault-detokenize", nanos);
  }

  @Override
  public void onRequestBlocked(@NonNull String integration) {
    blockedRequestCount.incrementAndGet();
    recordEvent("blocked-request", "PRISM_PROTECTION_BLOCK", 1, integration);
  }

  @Override
  public void onResponseBlocked(@NonNull String integration) {
    blockedResponseCount.incrementAndGet();
    recordEvent("blocked-response", "PRISM_PROTECTION_BLOCK", 1, integration);
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

  public long blockedRequestCount() {
    return blockedRequestCount.get();
  }

  public long blockedResponseCount() {
    return blockedResponseCount.get();
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
  public synchronized void captureHistorySample(@NonNull String vaultType) {
    long totalDetections = detectionCounts().values().stream().mapToLong(Long::longValue).sum();
    long detectionErrors = detectionErrorCount();
    long tokenized = tokenizedCount();
    long detokenized = detokenizedCount();
    historySamples.addLast(
        new HistorySample(
            Instant.now().toString(),
            totalDetections,
            detectionErrors,
            averageMilliseconds(
                "spring-ai:scan", "langchain4j:scan", "mcp-stdio:scan", "mcp-streamable-http:scan"),
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
