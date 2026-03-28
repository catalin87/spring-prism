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

import java.util.List;
import java.util.Map;

/** Immutable runtime snapshot shared by the fallback endpoint and actuator endpoint. */
public record PrismMetricsSnapshot(
    long tokenizedCount,
    long detokenizedCount,
    long detectionErrorCount,
    PrivacyScore privacyScore,
    Map<String, Long> detectionCounts,
    Map<String, PrismRuntimeMetrics.DurationMetric> durationMetrics,
    List<RulePackMetric> rulePackMetrics,
    List<EntityMetric> entityMetrics,
    List<IntegrationMetric> integrationMetrics,
    List<HistorySample> historySamples,
    List<HistoryRollup> historyRollups,
    int historyRetentionLimit,
    List<PrismRuntimeMetrics.AuditEvent> auditEvents,
    int auditRetentionLimit,
    List<String> activeRulePacks,
    int totalActiveRules,
    String failureMode,
    long blockedRequestCount,
    long blockedResponseCount,
    String configuredVaultMode,
    boolean customAppSecretConfigured,
    String vaultType,
    boolean distributedVault,
    boolean sharedVaultReady,
    String vaultReadinessStatus,
    String vaultReadinessDetails,
    long tokenBacklog,
    DashboardConfiguration dashboardConfiguration) {

  /**
   * Legacy constructor preserving the {@code 1.0.0} record shape.
   *
   * @deprecated since {@code 1.1.0}. Use the full constructor that also accepts {@code
   *     totalActiveRules}, failure-mode counters, and vault-readiness fields. This legacy
   *     constructor preserves the {@code 1.0.0} record shape and applies the following fallback
   *     values for fields introduced in {@code 1.1.0}: {@code totalActiveRules = 0}, {@code
   *     failureMode = "FAIL_SAFE"}, {@code blockedRequestCount = 0L}, {@code blockedResponseCount =
   *     0L}, {@code configuredVaultMode = "AUTO"}, {@code customAppSecretConfigured = false},
   *     {@code sharedVaultReady = false}, {@code vaultReadinessStatus = "UNKNOWN"}, and {@code
   *     vaultReadinessDetails = ""}. Will be removed in {@code 2.0.0}.
   */
  @Deprecated(since = "1.1.0", forRemoval = true)
  public PrismMetricsSnapshot(
      long tokenizedCount,
      long detokenizedCount,
      long detectionErrorCount,
      PrivacyScore privacyScore,
      Map<String, Long> detectionCounts,
      Map<String, PrismRuntimeMetrics.DurationMetric> durationMetrics,
      List<RulePackMetric> rulePackMetrics,
      List<EntityMetric> entityMetrics,
      List<IntegrationMetric> integrationMetrics,
      List<HistorySample> historySamples,
      List<HistoryRollup> historyRollups,
      int historyRetentionLimit,
      List<PrismRuntimeMetrics.AuditEvent> auditEvents,
      int auditRetentionLimit,
      List<String> activeRulePacks,
      String vaultType,
      boolean distributedVault,
      long tokenBacklog,
      DashboardConfiguration dashboardConfiguration) {
    this(
        tokenizedCount,
        detokenizedCount,
        detectionErrorCount,
        privacyScore,
        detectionCounts,
        durationMetrics,
        rulePackMetrics,
        entityMetrics,
        integrationMetrics,
        historySamples,
        historyRollups,
        historyRetentionLimit,
        auditEvents,
        auditRetentionLimit,
        activeRulePacks,
        0,
        "FAIL_SAFE",
        0L,
        0L,
        "AUTO",
        false,
        vaultType,
        distributedVault,
        false,
        "UNKNOWN",
        "",
        tokenBacklog,
        dashboardConfiguration);
  }
}
