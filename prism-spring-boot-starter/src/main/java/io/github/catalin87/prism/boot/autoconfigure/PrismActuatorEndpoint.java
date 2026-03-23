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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/** Actuator endpoint exposing the current Spring Prism runtime snapshot. */
@Endpoint(id = "prism")
public class PrismActuatorEndpoint {

  private final PrismRuntimeMetrics prismRuntimeMetrics;
  private final List<PrismRulePack> springPrismRulePacks;
  private final PrismVault prismVault;
  private final SpringPrismProperties springPrismProperties;

  /** Creates the actuator endpoint with the active rule packs, vault, and runtime counters. */
  public PrismActuatorEndpoint(
      PrismRuntimeMetrics prismRuntimeMetrics,
      @Qualifier("springPrismRulePacks") List<PrismRulePack> springPrismRulePacks,
      PrismVault prismVault,
      SpringPrismProperties springPrismProperties) {
    this.prismRuntimeMetrics = prismRuntimeMetrics;
    this.springPrismRulePacks = springPrismRulePacks;
    this.prismVault = prismVault;
    this.springPrismProperties = springPrismProperties;
  }

  /** Returns the current Spring Prism runtime snapshot for actuator consumers. */
  @ReadOperation
  public PrismMetricsSnapshot metrics() {
    return PrismMetricsSnapshotFactory.create(
        prismRuntimeMetrics, springPrismRulePacks, prismVault, springPrismProperties);
  }
}
