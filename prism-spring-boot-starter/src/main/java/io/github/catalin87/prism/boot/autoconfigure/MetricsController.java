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
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard-facing metrics endpoint for Spring Prism runtime state. */
@RestController
@RequestMapping("/actuator/prism")
public class MetricsController {

  private final PrismRuntimeMetrics prismRuntimeMetrics;
  private final List<PrismRulePack> springPrismRulePacks;
  private final PrismVault prismVault;

  public MetricsController(
      PrismRuntimeMetrics prismRuntimeMetrics,
      @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault) {
    this.prismRuntimeMetrics = prismRuntimeMetrics;
    this.springPrismRulePacks = springPrismRulePacks;
    this.prismVault = prismVault;
  }

  @GetMapping
  public MetricsSnapshot metrics() {
    List<String> activeRulePacks =
        springPrismRulePacks.stream().map(PrismRulePack::getName).toList();
    String vaultType = prismVault.getClass().getSimpleName();
    return new MetricsSnapshot(
        prismRuntimeMetrics.tokenizedCount(),
        prismRuntimeMetrics.detokenizedCount(),
        prismRuntimeMetrics.detectionErrorCount(),
        prismRuntimeMetrics.detectionCounts(),
        activeRulePacks,
        vaultType);
  }

  public record MetricsSnapshot(
      long tokenizedCount,
      long detokenizedCount,
      long detectionErrorCount,
      Map<String, Long> detectionCounts,
      List<String> activeRulePacks,
      String vaultType) {}
}
