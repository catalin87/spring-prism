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
package io.github.catalin87.prism.core;

import org.jspecify.annotations.NonNull;

/**
 * Observability hook for {@link PrismRulePackRegistry} lifecycle events. Deliberately defined in
 * {@code prism-core} with no Micrometer imports so that the registry remains free of any metrics
 * backend dependency. The {@code prism-spring-boot-starter} module supplies a Micrometer-backed
 * implementation that maps these callbacks to {@code prism.registry.swap.count} and {@code
 * prism.registry.swap.error.count} counters.
 *
 * <p>All methods are {@code default} no-ops so future additions remain backwards compatible with
 * existing sinks.
 *
 * @since 1.2.0
 */
public interface PrismRegistryMetricsSink {

  /** No-op sink used when callers do not need registry observability. */
  PrismRegistryMetricsSink NOOP = new PrismRegistryMetricsSink() {};

  /**
   * Invoked after a successful atomic publication of a new ruleset via {@link
   * PrismRulePackRegistry#update(java.util.List)}.
   */
  default void onSwap() {}

  /**
   * Invoked when {@link PrismRulePackRegistry#update(java.util.List)} fails (e.g. a null element
   * inside the supplied list triggers {@link java.util.List#copyOf(java.util.Collection)} to
   * throw). The registry rethrows after this callback runs, so the {@link Throwable} is observed
   * here purely for telemetry.
   *
   * @param error The exception that aborted the swap. Never {@code null}.
   */
  default void onSwapError(@NonNull Throwable error) {}
}
