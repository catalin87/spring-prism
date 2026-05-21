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
 * Extension hook that lets the {@link PrismRulePackRegistry} emit observability events without
 * depending on a concrete metrics backend. Implementations live in integration modules (for
 * example, the Spring Boot starter binds this sink to a Micrometer {@code MeterRegistry}). The core
 * module never references Micrometer types directly.
 *
 * <p>Two events are emitted:
 *
 * <ul>
 *   <li>{@link #onRegistrySwap()} — fired exactly once per successful {@link
 *       PrismRulePackRegistry#replace(java.util.List)} call, <em>after</em> the new snapshot is
 *       visible to readers.
 *   <li>{@link #onRegistryError(Throwable)} — fired when {@code replace(...)} fails (typically due
 *       to a {@code null} list or a {@code null} element rejected by {@link
 *       java.util.List#copyOf(java.util.Collection)}). The original exception is rethrown after the
 *       sink is notified, so error accounting and existing caller contracts both hold.
 * </ul>
 *
 * <p>Sink implementations must be thread-safe: the registry is a high-concurrency component and may
 * invoke this sink from multiple writer threads. Implementations must not throw — any failure
 * inside the sink would mask the underlying registry behaviour. The default {@link #NOOP}
 * implementation is used when no metrics backend is wired in.
 *
 * @since 1.3.0
 */
public interface PrismRegistryMetricsSink {

  /** No-op sink used when no metrics backend is configured. */
  PrismRegistryMetricsSink NOOP = new NoOpSink();

  /** Invoked after a successful {@link PrismRulePackRegistry#replace(java.util.List)} swap. */
  void onRegistrySwap();

  /**
   * Invoked when a {@link PrismRulePackRegistry#replace(java.util.List)} call fails before the
   * snapshot is updated. The {@code cause} is the exception that will propagate to the caller.
   *
   * @param cause the exception that caused the swap to fail; never {@code null}.
   */
  void onRegistryError(@NonNull Throwable cause);

  /** Internal no-op implementation. */
  final class NoOpSink implements PrismRegistryMetricsSink {

    @Override
    public void onRegistrySwap() {
      // intentionally empty
    }

    @Override
    public void onRegistryError(@NonNull Throwable cause) {
      // intentionally empty
    }
  }
}
