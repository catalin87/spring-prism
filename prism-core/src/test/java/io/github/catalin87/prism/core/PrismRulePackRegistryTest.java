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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class PrismRulePackRegistryTest {

  @Test
  void constructorRejectsNullInitialPacks() {
    assertThrows(NullPointerException.class, () -> new PrismRulePackRegistry(null));
  }

  @Test
  void updateRejectsNullNewPacks() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());
    assertThrows(NullPointerException.class, () -> registry.update(null));
  }

  @Test
  void snapshotReturnsInitialPacks() {
    PrismRulePack pack = new NamedPack("A");
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(pack));

    assertEquals(1, registry.snapshot().size());
    assertEquals("A", registry.snapshot().get(0).getName());
  }

  @Test
  void snapshotReflectsMostRecentUpdate() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(new NamedPack("A")));

    registry.update(List.of(new NamedPack("B"), new NamedPack("C")));

    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(2, snapshot.size());
    assertEquals("B", snapshot.get(0).getName());
    assertEquals("C", snapshot.get(1).getName());
  }

  @Test
  void constructorDefensivelyCopiesInitialPacks() {
    List<PrismRulePack> source = new ArrayList<>();
    source.add(new NamedPack("A"));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(source);

    source.add(new NamedPack("LATE"));

    assertEquals(1, registry.snapshot().size());
    assertEquals("A", registry.snapshot().get(0).getName());
  }

  @Test
  void updateDefensivelyCopiesNewPacks() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of());
    List<PrismRulePack> incoming = new ArrayList<>();
    incoming.add(new NamedPack("X"));
    registry.update(incoming);

    incoming.add(new NamedPack("LATE"));

    assertEquals(1, registry.snapshot().size());
    assertEquals("X", registry.snapshot().get(0).getName());
  }

  @Test
  void snapshotIsUnmodifiable() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(new NamedPack("A")));
    List<PrismRulePack> snapshot = registry.snapshot();

    assertThrows(UnsupportedOperationException.class, () -> snapshot.add(new NamedPack("B")));
  }

  @Test
  void updatePublishesNewListInstance() {
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(new NamedPack("A")));
    List<PrismRulePack> before = registry.snapshot();

    registry.update(List.of(new NamedPack("B")));

    assertNotSame(before, registry.snapshot());
  }

  /**
   * Concurrency smoke test asserting that under heavy concurrent reads interleaved with frequent
   * whole-set updates, every reader observes exactly one of the published lists end-to-end — never
   * a torn read, never a {@code ConcurrentModificationException}, never a mix of pre- and
   * post-update entries. This is the load-bearing safety property of the registry.
   */
  @Test
  void concurrentReadersNeverObserveTornListUnderConcurrentUpdates() throws InterruptedException {
    final int readerCount = 16;
    final int iterationsPerReader = 5_000;
    final int writerIterations = 2_000;

    List<List<PrismRulePack>> validRulesets =
        List.of(
            List.of(new NamedPack("RULESET_1_A")),
            List.of(new NamedPack("RULESET_2_A"), new NamedPack("RULESET_2_B")),
            List.of(
                new NamedPack("RULESET_3_A"),
                new NamedPack("RULESET_3_B"),
                new NamedPack("RULESET_3_C")),
            List.of(
                new NamedPack("RULESET_4_A"),
                new NamedPack("RULESET_4_B"),
                new NamedPack("RULESET_4_C"),
                new NamedPack("RULESET_4_D")));

    Set<List<String>> publishedShapes = new java.util.HashSet<>();
    for (List<PrismRulePack> packs : validRulesets) {
      List<String> names = new ArrayList<>();
      for (PrismRulePack p : packs) {
        names.add(p.getName());
      }
      publishedShapes.add(Collections.unmodifiableList(names));
    }

    PrismRulePackRegistry registry = new PrismRulePackRegistry(validRulesets.get(0));
    AtomicBoolean tornReadObserved = new AtomicBoolean(false);
    AtomicInteger totalReads = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(readerCount + 1);

    for (int r = 0; r < readerCount; r++) {
      executor.submit(
          () -> {
            try {
              start.await();
              for (int i = 0; i < iterationsPerReader; i++) {
                List<PrismRulePack> snapshot = registry.snapshot();
                List<String> observedNames = new ArrayList<>(snapshot.size());
                for (PrismRulePack p : snapshot) {
                  observedNames.add(p.getName());
                }
                if (!publishedShapes.contains(observedNames)) {
                  tornReadObserved.set(true);
                }
                totalReads.incrementAndGet();
              }
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            } finally {
              done.countDown();
            }
          });
    }

    executor.submit(
        () -> {
          try {
            start.await();
            for (int i = 0; i < writerIterations; i++) {
              registry.update(validRulesets.get(i % validRulesets.size()));
            }
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });

    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent test did not complete within 30s");
    executor.shutdownNow();

    assertFalse(
        tornReadObserved.get(),
        "Reader observed a list that was never a published ruleset — torn read detected");
    assertEquals(readerCount * iterationsPerReader, totalReads.get());
  }

  /** Minimal {@link PrismRulePack} test double identified only by name. */
  private static final class NamedPack implements PrismRulePack {
    private final String name;

    NamedPack(@NonNull String name) {
      this.name = name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return List.of();
    }

    @Override
    public @NonNull String getName() {
      return name;
    }
  }
}
