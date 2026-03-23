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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Builds the dashboard and actuator runtime snapshot from the active Prism beans. */
final class PrismMetricsSnapshotFactory {

  private PrismMetricsSnapshotFactory() {}

  static PrismMetricsSnapshot create(
      PrismRuntimeMetrics prismRuntimeMetrics,
      List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault) {
    Map<String, Long> detectionCounts = prismRuntimeMetrics.detectionCounts();
    List<String> activeRulePacks =
        springPrismRulePacks.stream().map(PrismRulePack::getName).toList();
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
    String vaultType = prismVault.getClass().getSimpleName();
    return new PrismMetricsSnapshot(
        prismRuntimeMetrics.tokenizedCount(),
        prismRuntimeMetrics.detokenizedCount(),
        prismRuntimeMetrics.detectionErrorCount(),
        detectionCounts,
        prismRuntimeMetrics.durationMetrics(),
        rulePackMetrics,
        prismRuntimeMetrics.recentAuditEvents(),
        prismRuntimeMetrics.auditRetentionLimit(),
        activeRulePacks,
        vaultType);
  }
}
