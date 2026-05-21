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
import io.micrometer.core.instrument.Metrics;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Micrometer adapter for {@link PrismRegistryMetricsSink}. Bridges core registry lifecycle
 * callbacks to two named counters published against the supplied {@link MeterRegistry} (falling
 * back to {@link Metrics#globalRegistry} when no explicit registry is wired):
 *
 * <ul>
 *   <li>{@code prism.registry.swap.count} — incremented on every successful {@link
 *       io.github.catalin87.prism.core.PrismRulePackRegistry#update(java.util.List)}.
 *   <li>{@code prism.registry.swap.error.count} — incremented on every {@code update(...)} call
 *       that aborted with an exception before publishing.
 * </ul>
 *
 * <p>The adapter is intentionally scoped to {@code prism-spring-boot-starter} so {@code prism-core}
 * keeps zero Micrometer imports.
 *
 * @since 1.2.0
 */
public final class MicrometerPrismRegistryMetricsSink implements PrismRegistryMetricsSink {

  static final String SWAP_COUNTER_NAME = "prism.registry.swap.count";
  static final String SWAP_ERROR_COUNTER_NAME = "prism.registry.swap.error.count";

  private final Counter swapCounter;
  private final Counter swapErrorCounter;

  /**
   * Creates an adapter publishing to the supplied {@link MeterRegistry}, or to {@link
   * Metrics#globalRegistry} when {@code meterRegistry} is {@code null}.
   *
   * @param meterRegistry The target Micrometer registry, or {@code null} to use the global
   *     registry.
   */
  public MicrometerPrismRegistryMetricsSink(@Nullable MeterRegistry meterRegistry) {
    MeterRegistry effectiveRegistry =
        meterRegistry != null ? meterRegistry : Metrics.globalRegistry;
    this.swapCounter =
        Counter.builder(SWAP_COUNTER_NAME)
            .description("Number of successful PrismRulePackRegistry ruleset publications.")
            .register(effectiveRegistry);
    this.swapErrorCounter =
        Counter.builder(SWAP_ERROR_COUNTER_NAME)
            .description(
                "Number of PrismRulePackRegistry update(...) calls that aborted with an"
                    + " exception before publishing a new ruleset.")
            .register(effectiveRegistry);
  }

  @Override
  public void onSwap() {
    swapCounter.increment();
  }

  @Override
  public void onSwapError(@NonNull Throwable error) {
    Objects.requireNonNull(error, "error");
    swapErrorCounter.increment();
  }
}
