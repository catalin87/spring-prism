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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link PrismRulePackRegistry} fans {@link #update(java.util.List)} lifecycle events
 * out to the configured {@link PrismRegistryMetricsSink}. Uses a hand-rolled recording sink so the
 * test stays inside {@code prism-core} (which has no Micrometer dependency by design).
 */
class PrismRulePackRegistryMetricsTest {

  @Test
  void successfulUpdateIncrementsSwapCounter() {
    RecordingSink sink = new RecordingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    registry.update(List.of(new EmptyPack("A")));
    registry.update(List.of(new EmptyPack("B"), new EmptyPack("C")));
    registry.update(List.of());

    assertEquals(3, sink.swaps.get(), "Every successful update should fire onSwap exactly once");
    assertEquals(0, sink.errors.get(), "No errors expected for valid update inputs");
    assertEquals(
        List.of(),
        sink.lastError.get() == null ? List.of() : List.of("unexpected-error"),
        "lastError reference should remain null on the happy path");
  }

  @Test
  void failedUpdateIncrementsSwapErrorCounterAndLeavesRegistryUntouched() {
    RecordingSink sink = new RecordingSink();
    EmptyPack seedPack = new EmptyPack("seed");
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(seedPack), sink);

    // List.copyOf rejects null elements with NullPointerException — that's the failure path the
    // sink contract promises to observe.
    List<PrismRulePack> withNull = Arrays.asList(new EmptyPack("ok"), null);
    assertThrows(NullPointerException.class, () -> registry.update(withNull));

    assertEquals(0, sink.swaps.get(), "Failed update must not fire onSwap");
    assertEquals(1, sink.errors.get(), "Failed update must fire onSwapError exactly once");
    assertEquals(
        NullPointerException.class,
        sink.lastError.get() == null ? null : sink.lastError.get().getClass(),
        "Sink should observe the original NPE that aborted the swap");
    assertEquals(
        List.of(seedPack),
        registry.snapshot(),
        "Active ruleset must remain on the prior generation when a swap is rejected");
  }

  @Test
  void defaultConstructorUsesNoopSinkAndStillSwapsAtomically() {
    // Smoke-check the backward-compatible single-arg constructor: no sink supplied → NOOP wired,
    // updates still publish atomically.
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());
    EmptyPack pack = new EmptyPack("solo");

    registry.update(List.of(pack));

    assertSame(pack, registry.snapshot().get(0));
  }

  /** Hand-rolled sink with atomic counters so the test does not depend on Mockito or Micrometer. */
  private static final class RecordingSink implements PrismRegistryMetricsSink {
    final AtomicInteger swaps = new AtomicInteger();
    final AtomicInteger errors = new AtomicInteger();
    final AtomicReference<Throwable> lastError = new AtomicReference<>();

    @Override
    public void onSwap() {
      swaps.incrementAndGet();
    }

    @Override
    public void onSwapError(@NonNull Throwable error) {
      errors.incrementAndGet();
      lastError.set(error);
    }
  }

  /** Minimal pack stub — the registry only cares about identity and reference equality here. */
  private static final class EmptyPack implements PrismRulePack {
    private final String name;

    EmptyPack(@NonNull String name) {
      this.name = name;
    }

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return Collections.emptyList();
    }
  }
}
