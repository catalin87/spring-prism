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
    String configuredVaultMode = properties.getVault().getType().name();
    boolean customAppSecretConfigured = !"spring-prism-change-me".equals(properties.getAppSecret());
    boolean distributedVault = vaultType.toLowerCase().contains("redis");
    boolean sharedVaultReady = distributedVault && customAppSecretConfigured;
    String vaultReadinessStatus =
        vaultReadinessStatus(distributedVault, sharedVaultReady, customAppSecretConfigured);
    String vaultReadinessDetails =
        vaultReadinessDetails(distributedVault, sharedVaultReady, customAppSecretConfigured);
    prismRuntimeMetrics.captureHistorySample(vaultType);
    List<HistorySample> historySamples = prismRuntimeMetrics.recentHistorySamples();
    long tokenBacklog =
        Math.max(0, prismRuntimeMetrics.tokenizedCount() - prismRuntimeMetrics.detokenizedCount());
    DashboardConfiguration dashboardConfiguration =
        dashboardConfiguration(prismRuntimeMetrics, properties);
    PrivacyScore privacyScore =
        privacyScore(historySamples, tokenBacklog, prismVault, properties, prismRuntimeMetrics);
    return new PrismMetricsSnapshot(
        prismRuntimeMetrics.tokenizedCount(),
        prismRuntimeMetrics.detokenizedCount(),
        prismRuntimeMetrics.detectionErrorCount(),
        privacyScore,
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
        configuredVaultMode,
        customAppSecretConfigured,
        vaultType,
        distributedVault,
        sharedVaultReady,
        vaultReadinessStatus,
        vaultReadinessDetails,
        tokenBacklog,
        dashboardConfiguration);
  }

  private static String vaultReadinessStatus(
      boolean distributedVault, boolean sharedVaultReady, boolean customAppSecretConfigured) {
    if (sharedVaultReady) {
      return "READY";
    }
    if (distributedVault) {
      return "ATTENTION";
    }
    return customAppSecretConfigured ? "LOCAL_ONLY" : "LOCAL_ONLY_ATTENTION";
  }

  private static String vaultReadinessDetails(
      boolean distributedVault, boolean sharedVaultReady, boolean customAppSecretConfigured) {
    if (sharedVaultReady) {
      return "Redis-backed shared restore path is active and a non-default app secret is"
          + " configured.";
    }
    if (distributedVault) {
      return "Redis-backed vault is active, but the default app secret is still configured. Set a"
          + " shared non-default spring.prism.app-secret on every node.";
    }
    if (customAppSecretConfigured) {
      return "Single-node local vault is active. This is production-safe only when requests and"
          + " restores stay on the same node.";
    }
    return "Single-node local vault is active and the default app secret is still configured."
        + " Override spring.prism.app-secret before production use.";
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

  private static PrivacyScore privacyScore(
      List<HistorySample> historySamples,
      long tokenBacklog,
      PrismVault prismVault,
      SpringPrismProperties properties,
      PrismRuntimeMetrics prismRuntimeMetrics) {
    Instant now = Instant.now();
    List<HistorySample> lastHourSamples = within(historySamples, now, Duration.ofHours(1));
    List<HistorySample> scoringSamples =
        lastHourSamples.isEmpty() ? historySamples : lastHourSamples;

    long protectedActivity = recentProtectedActivity(scoringSamples);
    long recentErrors = recentErrors(scoringSamples);
    double coverage = clamp01(protectedActivity / 20d);
    double reliability = 1d - clamp01(recentErrors / (double) Math.max(1L, protectedActivity));
    double posture = postureScore(prismVault, properties, prismRuntimeMetrics, tokenBacklog);

    int coverageScore = toScore(coverage);
    int reliabilityScore = toScore(reliability);
    int postureScore = toScore(posture);
    int overallScore = toScore((0.50d * coverage) + (0.30d * reliability) + (0.20d * posture));

    return new PrivacyScore(
        overallScore,
        "Last 60 minutes",
        "Based on protected activity, runtime reliability, and deployment posture "
            + "in the last 60 minutes.",
        new PrivacyScoreComponent(
            "Coverage",
            coverageScore,
            protectedActivity > 0
                ? protectedActivity + " protected event(s) observed in the active window."
                : "Waiting for protected traffic in the active window."),
        new PrivacyScoreComponent(
            "Reliability",
            reliabilityScore,
            recentErrors > 0
                ? recentErrors + " error event(s) reduced the runtime reliability score."
                : "No recent detector, vault, or restore errors reduced the score."),
        new PrivacyScoreComponent(
            "Posture",
            postureScore,
            postureDetail(prismVault, properties, prismRuntimeMetrics, tokenBacklog)));
  }

  private static long recentProtectedActivity(List<HistorySample> samples) {
    if (samples.isEmpty()) {
      return 0L;
    }
    HistorySample first = samples.getFirst();
    HistorySample last = samples.getLast();
    long detectionDelta = Math.max(0L, last.totalDetections() - first.totalDetections());
    long tokenizedDelta = Math.max(0L, last.tokenizedCount() - first.tokenizedCount());
    if (samples.size() == 1) {
      return Math.max(last.totalDetections(), last.tokenizedCount());
    }
    return Math.max(detectionDelta, tokenizedDelta);
  }

  private static long recentErrors(List<HistorySample> samples) {
    if (samples.isEmpty()) {
      return 0L;
    }
    HistorySample first = samples.getFirst();
    HistorySample last = samples.getLast();
    if (samples.size() == 1) {
      return last.detectionErrors();
    }
    return Math.max(0L, last.detectionErrors() - first.detectionErrors());
  }

  private static double postureScore(
      PrismVault prismVault,
      SpringPrismProperties properties,
      PrismRuntimeMetrics prismRuntimeMetrics,
      long tokenBacklog) {
    double score = 0.55d;
    score += properties.isSecurityStrictMode() ? 0.15d : 0.05d;
    score += prismVault.getClass().getSimpleName().toLowerCase().contains("redis") ? 0.20d : 0.15d;
    score += "spring-prism-change-me".equals(properties.getAppSecret()) ? -0.20d : 0.10d;
    if (tokenBacklog == 0L) {
      score += 0.05d;
    } else if (tokenBacklog > prismRuntimeMetrics.auditRetentionLimit()) {
      score -= 0.05d;
    }
    return clamp01(score);
  }

  private static String postureDetail(
      PrismVault prismVault,
      SpringPrismProperties properties,
      PrismRuntimeMetrics prismRuntimeMetrics,
      long tokenBacklog) {
    String strictMode = properties.isSecurityStrictMode() ? "strict mode on" : "fail-open mode";
    String vaultMode =
        prismVault.getClass().getSimpleName().toLowerCase().contains("redis")
            ? "Redis-backed shared vault"
            : "local in-memory vault";
    String secretState =
        "spring-prism-change-me".equals(properties.getAppSecret())
            ? "default app secret still configured"
            : "custom app secret configured";
    String backlogState =
        tokenBacklog > prismRuntimeMetrics.auditRetentionLimit()
            ? "token backlog is elevated"
            : "token backlog is within the normal band";
    return strictMode + ", " + vaultMode + ", " + secretState + ", and " + backlogState + ".";
  }

  private static int toScore(double value) {
    return (int) Math.round(clamp01(value) * 100d);
  }

  private static double clamp01(double value) {
    return Math.max(0d, Math.min(1d, value));
  }
}
