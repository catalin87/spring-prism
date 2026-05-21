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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PrismRulePackRegistryTest {

  @Test
  void snapshotReturnsInitialPacks() {
    PrismRulePack pack = new UniversalRulePack();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(pack));

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(1, snapshot.size());
    assertSame(pack, snapshot.get(0));
  }

  @Test
  void snapshotIsEmptyWhenConstructedWithEmptyList() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());

    assertTrue(registry.snapshot().isEmpty());
  }

  @Test
  void snapshotPreservesInsertionOrderForMultiplePacks() {
    PrismRulePack first = new UniversalRulePack();
    PrismRulePack second = new NamedRulePack("FIRST");
    PrismRulePack third = new NamedRulePack("SECOND");

    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(first, second, third));

    assertIterableEquals(List.of(first, second, third), registry.snapshot());
  }

  @Test
  void constructorDefensivelyCopiesInput() {
    PrismRulePack pack = new UniversalRulePack();
    List<PrismRulePack> mutable = new ArrayList<>();
    mutable.add(pack);

    PrismRulePackRegistry registry = new PrismRulePackRegistry(mutable);
    mutable.clear();

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(1, snapshot.size());
    assertSame(pack, snapshot.get(0));
  }

  @Test
  void replaceSwapsSnapshot() {
    PrismRulePack initial = new NamedRulePack("INITIAL");
    PrismRulePack updated = new NamedRulePack("UPDATED");
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initial));

    registry.replace(List.of(updated));

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(1, snapshot.size());
    assertSame(updated, snapshot.get(0));
  }

  @Test
  void replaceDefensivelyCopiesInput() {
    PrismRulePack pack = new NamedRulePack("UPDATED");
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());

    List<PrismRulePack> mutable = new ArrayList<>();
    mutable.add(pack);
    registry.replace(mutable);
    mutable.clear();

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(1, snapshot.size());
    assertSame(pack, snapshot.get(0));
  }

  @Test
  void snapshotIsImmutable() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(new UniversalRulePack()));

    List<PrismRulePack> snapshot = registry.snapshot();

    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new UniversalRulePack()));
  }

  @Test
  void constructorRejectsNullList() {
    assertThrows(NullPointerException.class, () -> new PrismRulePackRegistry(null));
  }

  @Test
  void constructorRejectsListContainingNull() {
    List<PrismRulePack> withNull = Arrays.asList(new UniversalRulePack(), null);

    assertThrows(NullPointerException.class, () -> new PrismRulePackRegistry(withNull));
  }

  @Test
  void replaceRejectsNullList() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());

    assertThrows(NullPointerException.class, () -> registry.replace(null));
  }

  @Test
  void replaceRejectsListContainingNull() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());
    List<PrismRulePack> withNull = Arrays.asList(new UniversalRulePack(), null);

    assertThrows(NullPointerException.class, () -> registry.replace(withNull));
  }

  /**
   * Concurrent readers iterating the snapshot while a writer swaps lists must always observe one of
   * the two known input lists in full, never a partial blend or a {@link
   * java.util.ConcurrentModificationException}.
   */
  @Test
  void concurrentReadersObserveOnlyFullSnapshots() throws Exception {
    PrismRulePack a1 = new NamedRulePack("A1");
    PrismRulePack a2 = new NamedRulePack("A2");
    PrismRulePack b1 = new NamedRulePack("B1");

    List<PrismRulePack> packsA = List.of(a1, a2);
    List<PrismRulePack> packsB = List.of(b1);
    Set<List<PrismRulePack>> validSnapshots = Set.of(packsA, packsB);

    PrismRulePackRegistry registry = new PrismRulePackRegistry(packsA);

    int readerCount = 8;
    int iterationsPerReader = 10_000;
    Set<List<PrismRulePack>> observed = Collections.synchronizedSet(new HashSet<>());
    AtomicBoolean stopWriter = new AtomicBoolean(false);
    AtomicBoolean readerFailed = new AtomicBoolean(false);

    ExecutorService pool = Executors.newFixedThreadPool(readerCount + 1);
    CountDownLatch start = new CountDownLatch(1);

    try {
      for (int i = 0; i < readerCount; i++) {
        pool.submit(
            () -> {
              try {
                start.await();
                for (int n = 0; n < iterationsPerReader; n++) {
                  List<PrismRulePack> seen = registry.snapshot();
                  // Materialise by iterating — ensures no CME and that names are observable.
                  List<PrismRulePack> copy = List.copyOf(seen);
                  observed.add(copy);
                }
              } catch (Throwable t) {
                readerFailed.set(true);
              }
            });
      }

      pool.submit(
          () -> {
            try {
              start.await();
              boolean flip = false;
              while (!stopWriter.get()) {
                registry.replace(flip ? packsA : packsB);
                flip = !flip;
              }
            } catch (Throwable ignored) {
              // writer interruption is acceptable; readers are the assertion target
            }
          });

      start.countDown();
      pool.shutdown();

      // Give readers time to finish their bounded loops; then stop the writer.
      assertFalse(pool.awaitTermination(20, TimeUnit.SECONDS));
      stopWriter.set(true);
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      stopWriter.set(true);
      pool.shutdownNow();
    }

    assertFalse(readerFailed.get(), "at least one reader observed an inconsistent snapshot");
    assertFalse(observed.isEmpty(), "expected at least one observed snapshot");
    for (List<PrismRulePack> snapshot : observed) {
      assertTrue(
          validSnapshots.contains(snapshot),
          () -> "observed snapshot not equal to any input list: " + snapshot);
    }
  }

  /** Minimal test-only rule pack with a custom name and no detectors. */
  private static final class NamedRulePack implements PrismRulePack {

    private final String name;

    NamedRulePack(String name) {
      this.name = name;
    }

    @Override
    public List<PiiDetector> getDetectors() {
      return List.of();
    }

    @Override
    public String getName() {
      return name;
    }
  }
}
