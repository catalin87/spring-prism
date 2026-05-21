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
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link PrismRulePackRegistry} correctly fires {@link PrismRegistryMetricsSink}
 * callbacks: one {@code onRegistrySwap()} per successful {@code replace(...)} and one {@code
 * onRegistryError(...)} per failed {@code replace(...)}. These assertions cover the contract that
 * the Spring Boot starter relies on when bridging to Micrometer counters.
 */
class PrismRulePackRegistryMetricsTest {

  @Test
  void replaceIncrementsSwapCounterOncePerSuccessfulCall() {
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    registry.replace(List.of(new UniversalRulePack()));
    registry.replace(List.of());
    registry.replace(List.of(new UniversalRulePack(), new UniversalRulePack()));

    assertEquals(
        3, sink.swaps.get(), "every successful replace must fire onRegistrySwap exactly once");
    assertEquals(0, sink.errors.get(), "no replace failed, so the error counter must be untouched");
  }

  @Test
  void constructionDoesNotFireSwapEvent() {
    CountingSink sink = new CountingSink();
    new PrismRulePackRegistry(List.of(new UniversalRulePack()), sink);

    assertEquals(0, sink.swaps.get(), "initial seeding must not be reported as a swap");
    assertEquals(0, sink.errors.get(), "successful construction must not be reported as an error");
  }

  @Test
  void replaceFiresErrorCallbackAndRethrowsWhenInputIsNull() {
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    assertThrows(NullPointerException.class, () -> registry.replace(null));

    assertEquals(0, sink.swaps.get(), "failed replace must not fire onRegistrySwap");
    assertEquals(1, sink.errors.get(), "failed replace must fire onRegistryError exactly once");
    assertTrue(
        sink.lastError.get() instanceof NullPointerException,
        "the error callback must receive the actual NullPointerException, got: "
            + sink.lastError.get());
  }

  @Test
  void replaceFiresErrorCallbackAndRethrowsWhenInputContainsNullElement() {
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);
    List<PrismRulePack> withNull = Arrays.asList(new UniversalRulePack(), null);

    assertThrows(NullPointerException.class, () -> registry.replace(withNull));

    assertEquals(0, sink.swaps.get(), "failed replace must not fire onRegistrySwap");
    assertEquals(1, sink.errors.get(), "failed replace must fire onRegistryError exactly once");
  }

  @Test
  void failedReplaceLeavesPreviousSnapshotIntactAndReadable() {
    CountingSink sink = new CountingSink();
    PrismRulePack initial = new UniversalRulePack();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initial), sink);

    assertThrows(NullPointerException.class, () -> registry.replace(null));

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(
        1, snapshot.size(), "the previous snapshot must remain visible after a failed swap");
    assertSame(initial, snapshot.get(0));
    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
  }

  @Test
  void singleArgConstructorBindsNoopSinkAndStillSucceedsOnReplace() {
    // Backward-compatibility guard: the legacy constructor must continue to work and must not
    // require the caller to supply a sink. We can only test it indirectly by verifying that
    // replace() does not throw and the snapshot updates.
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());
    PrismRulePack pack = new UniversalRulePack();

    registry.replace(List.of(pack));

    assertEquals(1, registry.snapshot().size());
    assertSame(pack, registry.snapshot().get(0));
  }

  @Test
  void constructorRejectsNullMetricsSink() {
    List<PrismRulePack> initial = new ArrayList<>();
    assertThrows(NullPointerException.class, () -> new PrismRulePackRegistry(initial, null));
  }

  /** Counting test double — records swap/error invocations on thread-safe counters. */
  private static final class CountingSink implements PrismRegistryMetricsSink {

    final AtomicInteger swaps = new AtomicInteger();
    final AtomicInteger errors = new AtomicInteger();
    final AtomicReference<Throwable> lastError = new AtomicReference<>();

    @Override
    public void onRegistrySwap() {
      swaps.incrementAndGet();
    }

    @Override
    public void onRegistryError(@NonNull Throwable cause) {
      errors.incrementAndGet();
      lastError.set(cause);
    }
  }
}
