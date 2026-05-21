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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Long-running stress test that exercises {@link PrismRulePackRegistry} under the production load
 * profile: many readers walking a snapshot end-to-end (the {@code PrismTextScanner.tokenize()}
 * pattern) while a writer continuously swaps the active ruleset.
 *
 * <h2>What this test proves</h2>
 *
 * <p>The companion {@code PrismRulePackRegistryTest} concurrency case asserts that the top-level
 * snapshot list is published atomically. This test goes one level deeper: it verifies that the
 * <em>scanner contract</em> holds — namely, a scan begun against ruleset generation {@code N}
 * iterates every pack and every detector belonging to generation {@code N} for the full duration of
 * the call, even if the writer publishes generations {@code N+1}, {@code N+2}, ... mid-iteration.
 *
 * <p>To make a torn iteration visible at detector granularity, every {@link PrismRulePack} and
 * every {@link PiiDetector} published in a given generation is tagged with that generation's
 * sequence number. A reader that observes a single snapshot must see exactly one tag across every
 * pack and every detector it visits — otherwise the iteration crossed a publication boundary, which
 * would mean the {@link java.util.concurrent.atomic.AtomicReference}-backed snapshot contract is
 * broken.
 *
 * <p>The test also measures and prints throughput as a sanity check that the volatile read in the
 * hot path is non-blocking. Throughput is reported but not asserted on — machine-dependent
 * thresholds make for flaky CI.
 *
 * <h2>Execution</h2>
 *
 * <p>Disabled by default to avoid adding 30+ seconds to every build. Enable with {@code
 * -Dprism.stressTests=true}.
 */
@EnabledIfSystemProperty(named = "prism.stressTests", matches = "true")
class RegistryConcurrencyStressTest {

  private static final int READER_THREAD_COUNT = 32;
  private static final Duration TEST_DURATION = Duration.ofSeconds(30);
  private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(30);
  private static final int RULESET_GENERATIONS = 8;
  private static final int PACKS_PER_GENERATION = 4;
  private static final int DETECTORS_PER_PACK = 6;

  /**
   * Under sustained 32-reader / 1-writer load for {@value #TEST_DURATION} seconds, every snapshot a
   * reader takes must iterate a structurally consistent ruleset — same generation tag on every pack
   * name and every detector entity type. Any single mismatched tag is recorded and fails the test.
   *
   * <p>The reader loop also asserts that {@link PrismRulePack#getDetectors()} never throws {@link
   * java.util.ConcurrentModificationException}; the writer mutates only by atomic swap (never by
   * in-place edit), but this catches any future regression where a swap accidentally publishes a
   * mutable list.
   */
  @Test
  void scannerSnapshotContractHoldsUnderSustainedContention() throws InterruptedException {
    StressOutcome outcome = runStress(TEST_DURATION);

    assertTrue(outcome.cleanShutdown(), "Stress test did not shut down within its grace window");
    assertNull(
        outcome.firstFailure(),
        () ->
            "Torn read observed under concurrent update — snapshot contract broken: "
                + outcome.firstFailure());

    double elapsedSeconds = outcome.elapsedNanos() / 1_000_000_000.0;
    double scansPerSecond = outcome.scans() / elapsedSeconds;
    double writesPerSecond = outcome.writes() / elapsedSeconds;
    System.out.printf(
        "[RegistryConcurrencyStressTest] %d readers x %.1fs = %d scans (%.0f scans/sec)"
            + " | %d ruleset swaps (%.0f swaps/sec) across %d generations%n",
        READER_THREAD_COUNT,
        elapsedSeconds,
        outcome.scans(),
        scansPerSecond,
        outcome.writes(),
        writesPerSecond,
        RULESET_GENERATIONS);

    assertTrue(outcome.scans() > 0, "No scans completed — readers never ran");
    assertTrue(outcome.writes() > 0, "No ruleset swaps occurred — writer never ran");
  }

  /**
   * Runs {@value #READER_THREAD_COUNT} virtual-thread readers performing snapshot-then-iterate
   * scans alongside a single writer continuously swapping rulesets. Stops after {@code duration},
   * then waits up to {@link #SHUTDOWN_GRACE} for all participants to exit cleanly. A reader that
   * detects a torn iteration short-circuits the run by signalling stop.
   */
  private static @NonNull StressOutcome runStress(@NonNull Duration duration)
      throws InterruptedException {
    List<List<PrismRulePack>> rulesets = buildRulesetCatalog();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(rulesets.get(0));

    AtomicBoolean stopSignal = new AtomicBoolean(false);
    AtomicLong successfulScanCount = new AtomicLong(0);
    AtomicLong writeCount = new AtomicLong(0);
    AtomicReference<@Nullable String> firstFailure = new AtomicReference<>();

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(READER_THREAD_COUNT + 1);

    // Writer runs on a dedicated platform thread so it cannot be starved by 32 reader virtual
    // threads competing for the same carrier pool — a real concern when surefire pins parallelism
    // to availableProcessors() on CI boxes with few cores.
    Thread writer =
        new Thread(
            () -> {
              try {
                start.await();
                int generation = 0;
                while (!stopSignal.get()) {
                  generation = (generation + 1) % RULESET_GENERATIONS;
                  registry.update(rulesets.get(generation));
                  writeCount.incrementAndGet();
                  Thread.yield();
                }
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            },
            "prism-stress-writer");
    writer.setDaemon(true);
    writer.start();

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int r = 0; r < READER_THREAD_COUNT; r++) {
        executor.submit(
            () -> {
              try {
                start.await();
                while (!stopSignal.get()) {
                  String failure = simulateScannerPass(registry);
                  if (failure != null) {
                    firstFailure.compareAndSet(null, failure);
                    stopSignal.set(true);
                    return;
                  }
                  successfulScanCount.incrementAndGet();
                }
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              } catch (RuntimeException ex) {
                firstFailure.compareAndSet(
                    null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                stopSignal.set(true);
              } finally {
                done.countDown();
              }
            });
      }

      long startNanos = System.nanoTime();
      start.countDown();

      // Linear shutdown: sleep for the test duration, signal stop, wait for everyone to exit.
      // A reader that observes a torn read can flip stopSignal early — we still wait the grace
      // window for the writer + remaining readers to notice the flag, never longer.
      Thread.sleep(duration.toMillis());
      stopSignal.set(true);
      boolean cleanShutdown = done.await(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS);
      long elapsedNanos = System.nanoTime() - startNanos;

      return new StressOutcome(
          cleanShutdown,
          firstFailure.get(),
          successfulScanCount.get(),
          writeCount.get(),
          elapsedNanos);
    }
  }

  /**
   * Drives one full scan against the registry the way {@code PrismTextScanner.tokenize()} does:
   * take one snapshot, then iterate every pack and every detector reachable through that snapshot
   * exactly once. Returns {@code null} on success or a description of the first torn-read it
   * detected.
   *
   * <p>The torn-read check exploits the fact that every pack and every detector published in a
   * given generation carries that generation's number as a tag. A reader that observes more than
   * one distinct tag in a single pass crossed a publication boundary mid-iteration — which would
   * violate the snapshot contract.
   */
  private static @Nullable String simulateScannerPass(@NonNull PrismRulePackRegistry registry) {
    List<PrismRulePack> snapshot = registry.snapshot();
    if (snapshot.isEmpty()) {
      return "empty snapshot — registry was never seeded or was cleared mid-test";
    }
    int expectedGeneration = generationOf(snapshot.get(0).getName());
    if (expectedGeneration < 0) {
      return "unparseable pack name: " + snapshot.get(0).getName();
    }
    for (PrismRulePack pack : snapshot) {
      int packGen = generationOf(pack.getName());
      if (packGen != expectedGeneration) {
        return "torn pack read — first pack tagged generation "
            + expectedGeneration
            + " but pack '"
            + pack.getName()
            + "' tagged generation "
            + packGen;
      }
      for (PiiDetector detector : pack.getDetectors()) {
        int detectorGen = generationOf(detector.getEntityType());
        if (detectorGen != expectedGeneration) {
          return "torn detector read — pack '"
              + pack.getName()
              + "' belongs to generation "
              + expectedGeneration
              + " but its detector '"
              + detector.getEntityType()
              + "' tagged generation "
              + detectorGen;
        }
        detector.mayMatch("payload " + ThreadLocalRandom.current().nextInt());
      }
    }
    return null;
  }

  /** Extracts the generation suffix from a {@code "...@G<n>"} name. Returns -1 if not present. */
  private static int generationOf(@NonNull String taggedName) {
    int marker = taggedName.lastIndexOf("@G");
    if (marker < 0 || marker + 2 >= taggedName.length()) {
      return -1;
    }
    try {
      return Integer.parseInt(taggedName.substring(marker + 2));
    } catch (NumberFormatException ex) {
      return -1;
    }
  }

  /**
   * Builds {@value #RULESET_GENERATIONS} distinct rulesets, each tagged with its own generation
   * number so a reader can later detect torn iteration at either pack or detector granularity.
   */
  private static @NonNull List<List<PrismRulePack>> buildRulesetCatalog() {
    List<List<PrismRulePack>> rulesets = new ArrayList<>(RULESET_GENERATIONS);
    for (int g = 0; g < RULESET_GENERATIONS; g++) {
      List<PrismRulePack> packs = new ArrayList<>(PACKS_PER_GENERATION);
      for (int p = 0; p < PACKS_PER_GENERATION; p++) {
        List<PiiDetector> detectors = new ArrayList<>(DETECTORS_PER_PACK);
        for (int d = 0; d < DETECTORS_PER_PACK; d++) {
          detectors.add(new TaggedNoOpDetector("D" + p + "_" + d + "@G" + g));
        }
        packs.add(new TaggedRulePack("PACK_" + p + "@G" + g, List.copyOf(detectors)));
      }
      rulesets.add(List.copyOf(packs));
    }
    return List.copyOf(rulesets);
  }

  /** Aggregate outcome of one stress run, returned from {@link #runStress(Duration)}. */
  private record StressOutcome(
      boolean cleanShutdown,
      @Nullable String firstFailure,
      long scans,
      long writes,
      long elapsedNanos) {}

  /** Pack tagged with the generation it belongs to. */
  private static final class TaggedRulePack implements PrismRulePack {
    private final String name;
    private final List<PiiDetector> detectors;

    TaggedRulePack(@NonNull String name, @NonNull List<PiiDetector> detectors) {
      this.name = name;
      this.detectors = detectors;
    }

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return detectors;
    }
  }

  /** Detector that does no real work but carries a generation tag in its entity type. */
  private static final class TaggedNoOpDetector implements PiiDetector {
    private final String entityType;

    TaggedNoOpDetector(@NonNull String entityType) {
      this.entityType = entityType;
    }

    @Override
    public @NonNull String getEntityType() {
      return entityType;
    }

    @Override
    public boolean mayMatch(@NonNull String text) {
      return !text.isEmpty();
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      return List.of();
    }
  }
}
