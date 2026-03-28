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
package io.github.catalin87.prism.examples.demo;

import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.PrismVaultAvailability;
import java.time.Duration;
import org.jspecify.annotations.NonNull;

/** Prism vault wrapper that can simulate a distributed vault outage for the demo sandbox. */
final class SimulationAwarePrismVault implements PrismVault, PrismVaultAvailability {

  private final PrismVault delegate;
  private final LabSimulationState simulationState;

  SimulationAwarePrismVault(PrismVault delegate, LabSimulationState simulationState) {
    this.delegate = delegate;
    this.simulationState = simulationState;
  }

  @Override
  public @NonNull PrismToken tokenize(@NonNull String value, @NonNull String label) {
    assertAvailable();
    return delegate.tokenize(value, label);
  }

  @Override
  public String detokenize(@NonNull String tokenKey) {
    assertAvailable();
    return delegate.detokenize(tokenKey);
  }

  @Override
  public void verifyAvailability(Duration timeout) {
    assertAvailable();
    if (delegate instanceof PrismVaultAvailability availability) {
      availability.verifyAvailability(timeout);
    }
  }

  private void assertAvailable() {
    if (simulationState.redisOutageActive()) {
      throw new IllegalStateException("Simulated Redis outage is active in the enterprise lab");
    }
  }
}
