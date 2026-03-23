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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Builds the dashboard and actuator runtime snapshot from the active Prism beans. */
final class PrismMetricsSnapshotFactory {

  private PrismMetricsSnapshotFactory() {}

  static PrismMetricsSnapshot create(
      PrismRuntimeMetrics prismRuntimeMetrics,
      List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault,
      SpringPrismProperties properties) {
    Map<String, Long> detectionCounts = prismRuntimeMetrics.detectionCounts();
    List<String> activeRulePacks =
        springPrismRulePacks.stream().map(PrismRulePack::getName).toList();
    List<EntityMetric> entityMetrics =
        springPrismRulePacks.stream()
            .flatMap(
                rulePack ->
                    rulePack.getDetectors().stream()
                        .map(
                            detector ->
                                new EntityMetric(
                                    rulePack.getName(),
                                    detector.getEntityType(),
                                    detectionCounts.getOrDefault(
                                        rulePack.getName() + ":" + detector.getEntityType(), 0L))))
            .filter(entityMetric -> entityMetric.detections() > 0)
            .sorted(Comparator.comparingLong(EntityMetric::detections).reversed())
            .toList();
    List<RulePackMetric> rulePackMetrics =
        springPrismRulePacks.stream()
            .map(
                rulePack -> {
                  long totalDetections =
                      rulePack.getDetectors().stream()
                          .mapToLong(
                              detector ->
                                  detectionCounts.getOrDefault(
                                      rulePack.getName() + ":" + detector.getEntityType(), 0L))
                          .sum();
                  return new RulePackMetric(
                      rulePack.getName(), rulePack.getDetectors().size(), totalDetections);
                })
            .sorted(Comparator.comparingLong(RulePackMetric::totalDetections).reversed())
            .toList();
    Map<String, PrismRuntimeMetrics.DurationMetric> durationMetrics =
        prismRuntimeMetrics.durationMetrics();
    List<IntegrationMetric> integrationMetrics =
        Stream.of("spring-ai", "langchain4j", "mcp-stdio", "mcp-streamable-http")
            .map(
                integration ->
                    new IntegrationMetric(
                        integration,
                        durationMetrics.get(integration + ":scan"),
                        durationMetrics.get(integration + ":vault-tokenize"),
                        durationMetrics.get(integration + ":vault-detokenize")))
            .toList();
    String vaultType = prismVault.getClass().getSimpleName();
    prismRuntimeMetrics.captureHistorySample(vaultType);
    List<HistorySample> historySamples = prismRuntimeMetrics.recentHistorySamples();
    long tokenBacklog =
        Math.max(0, prismRuntimeMetrics.tokenizedCount() - prismRuntimeMetrics.detokenizedCount());
    DashboardConfiguration dashboardConfiguration =
        dashboardConfiguration(prismRuntimeMetrics, properties);
    return new PrismMetricsSnapshot(
        prismRuntimeMetrics.tokenizedCount(),
        prismRuntimeMetrics.detokenizedCount(),
        prismRuntimeMetrics.detectionErrorCount(),
        detectionCounts,
        durationMetrics,
        rulePackMetrics,
        entityMetrics,
        integrationMetrics,
        historySamples,
        historyRollups(historySamples),
        prismRuntimeMetrics.historyRetentionLimit(),
        prismRuntimeMetrics.recentAuditEvents(),
        prismRuntimeMetrics.auditRetentionLimit(),
        activeRulePacks,
        vaultType,
        vaultType.toLowerCase().contains("redis"),
        tokenBacklog,
        dashboardConfiguration);
  }

  private static DashboardConfiguration dashboardConfiguration(
      PrismRuntimeMetrics prismRuntimeMetrics, SpringPrismProperties properties) {
    SpringPrismProperties.AlertThresholds alertThresholds =
        properties.getDashboard().getAlertThresholds();
    return new DashboardConfiguration(
        prismRuntimeMetrics.auditRetentionLimit(),
        prismRuntimeMetrics.historyRetentionLimit(),
        properties.getDashboard().getDefaultPollingSeconds(),
        new DashboardAlertThresholds(
            alertThresholds.getScanLatencyWarnMs(),
            alertThresholds.getScanLatencyCriticalMs(),
            alertThresholds.getTokenBacklogWarn(),
            alertThresholds.getTokenBacklogCritical(),
            alertThresholds.getDetectionErrorWarn(),
            alertThresholds.getDetectionErrorCritical()));
  }

  private static List<HistoryRollup> historyRollups(List<HistorySample> historySamples) {
    Instant now = Instant.now();
    return List.of(
        rollup("recent", "Recent", historySamples),
        rollup("5m", "5 minutes", within(historySamples, now, Duration.ofMinutes(5))),
        rollup("15m", "15 minutes", within(historySamples, now, Duration.ofMinutes(15))),
        rollup("1h", "1 hour", within(historySamples, now, Duration.ofHours(1))));
  }

  private static List<HistorySample> within(
      List<HistorySample> historySamples, Instant now, Duration duration) {
    Instant cutoff = now.minus(duration);
    return historySamples.stream()
        .filter(sample -> Instant.parse(sample.capturedAt()).isAfter(cutoff))
        .toList();
  }

  private static HistoryRollup rollup(String key, String label, List<HistorySample> samples) {
    if (samples.isEmpty()) {
      return new HistoryRollup(key, label, 0, 0, 0, 0d, 0);
    }

    long latestDetections = samples.getLast().totalDetections();
    long errorEvents = samples.stream().mapToLong(HistorySample::detectionErrors).max().orElse(0);
    double averageScanMilliseconds =
        samples.stream().mapToDouble(HistorySample::scanMilliseconds).average().orElse(0d);
    long peakTokenBacklog = samples.stream().mapToLong(HistorySample::tokenBacklog).max().orElse(0);

    return new HistoryRollup(
        key,
        label,
        samples.size(),
        latestDetections,
        errorEvents,
        averageScanMilliseconds,
        peakTokenBacklog);
  }
}
