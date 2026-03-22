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
import java.util.List;

/** Builds the dashboard and actuator runtime snapshot from the active Prism beans. */
final class PrismMetricsSnapshotFactory {

  private PrismMetricsSnapshotFactory() {}

  static PrismMetricsSnapshot create(
      PrismRuntimeMetrics prismRuntimeMetrics,
      List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault) {
    List<String> activeRulePacks =
        springPrismRulePacks.stream().map(PrismRulePack::getName).toList();
    String vaultType = prismVault.getClass().getSimpleName();
    return new PrismMetricsSnapshot(
        prismRuntimeMetrics.tokenizedCount(),
        prismRuntimeMetrics.detokenizedCount(),
        prismRuntimeMetrics.detectionErrorCount(),
        prismRuntimeMetrics.detectionCounts(),
        prismRuntimeMetrics.durationMetrics(),
        activeRulePacks,
        vaultType);
  }
}
