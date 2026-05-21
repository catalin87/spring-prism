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
package io.github.catalin87.prism.spring.ai.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismRulePackRegistry;
import io.github.catalin87.prism.core.ruleset.EuropeRulePack;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Stress test that validates the "zero-lock" {@link PrismRulePackRegistry} design end-to-end
 * through the production hot path — {@link PrismTextScanner#tokenize(String)} — under sustained
 * high concurrency.
 *
 * <p>The harness:
 *
 * <ol>
 *   <li>Builds a single shared {@link PrismTextScanner} backed by a registry initialised with the
 *       {@link UniversalRulePack}, paired with an in-process {@link DefaultPrismVault} so tokenize
 *       calls succeed without external dependencies.
 *   <li>Pre-computes the two <em>legal</em> tokenized outputs the scanner could return — one for
 *       each rule pack the writer cycles through — and stores them in a reference set. Because
 *       vault token issuance is HMAC-deterministic (same vault key + same input ⇒ same token), any
 *       tokenize result observed during the stress run must match one of these two strings exactly.
 *       Any other output is, by definition, a torn read: the scanner observed a partial blend of
 *       two rule packs mid-iteration.
 *   <li>Spawns 32 reader threads that loop calling {@code tokenize()} on the same fixed prompt and
 *       compare each result against the legal-output set.
 *   <li>Runs a single background writer that hammers {@link PrismRulePackRegistry#replace(List)} to
 *       toggle the active pack as fast as the JVM will let it — no sleeps, no backoff.
 *   <li>After {@value #STRESS_DURATION_SECONDS} seconds, asserts: no reader threw, no torn output
 *       was observed, at least one read of each legal output was seen (proving the swap actually
 *       took effect during the run), and total throughput cleared a soft floor.
 * </ol>
 *
 * <p>Tagged {@code stress} so CI pipelines that want a fast feedback loop can exclude it via {@code
 * -DexcludedGroups=stress}.
 */
@Tag("stress")
class RegistryConcurrencyStressTest {

  /** Number of reader threads; the task contract requires at least 32. */
  private static final int READER_THREAD_COUNT = 32;

  /** How long readers and writer run concurrently. */
  private static final int STRESS_DURATION_SECONDS = 20;

  /** Budget for executors to drain after the stop signal. */
  private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

  /**
   * Fixed in-process vault key — deterministic token issuance is essential for torn-read checks.
   */
  private static final byte[] VAULT_SECRET =
      "stress-test-secret-material-32-bytes!".getBytes(StandardCharsets.UTF_8);

  /** Vault TTL well beyond the stress duration so tokens never expire mid-run. */
  private static final long VAULT_TTL_SECONDS = 3_600L;

  /**
   * A prompt embedding PII patterns recognised by <em>both</em> rule packs so a swap produces
   * structurally distinct (but each individually legal) tokenized outputs.
   */
  private static final String STRESS_PROMPT =
      "Contact alice@example.com or call 555-123-4567 about SSN 123-45-6789 "
          + "and card 4111 1111 1111 1111 for account holder Bob Smith.";

  @Test
  void scannerTokenizeIsAtomicAndNonBlockingUnderSustainedConcurrency()
      throws InterruptedException {
    // Two real rule packs; cycling between them exercises the swap path with semantically
    // distinct detector sets. Vault is shared between the two reference scanners and the
    // stress scanner so the deterministic HMAC token output is identical across all three.
    PrismRulePack universalPack = new UniversalRulePack();
    PrismRulePack europePack = new EuropeRulePack();
    DefaultPrismVault sharedVault =
        new DefaultPrismVault(new HmacSha256TokenGenerator(), VAULT_SECRET, VAULT_TTL_SECONDS);

    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(universalPack));
    PrismTextScanner scanner =
        new PrismTextScanner(
            registry, sharedVault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP, false);

    // Pre-compute the two legal outputs. Because the vault is HMAC-deterministic, every
    // tokenize() call against the same rule pack on the same prompt must return the same
    // string. Any deviation = torn read.
    Set<String> legalOutputs = computeLegalOutputs(scanner, registry, universalPack, europePack);
    assertThat(legalOutputs)
        .as("the two rule packs must produce distinguishable tokenizations of the stress prompt")
        .hasSize(2);

    // Reset to a known starting state for the stress phase.
    registry.replace(List.of(universalPack));

    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicLong totalScans = new AtomicLong();
    ConcurrentLinkedQueue<Throwable> readerFailures = new ConcurrentLinkedQueue<>();
    ConcurrentLinkedQueue<String> tornReads = new ConcurrentLinkedQueue<>();
    Set<String> observedOutputs = new java.util.concurrent.ConcurrentSkipListSet<>();

    ExecutorService readers = Executors.newFixedThreadPool(READER_THREAD_COUNT);
    ExecutorService writer = Executors.newSingleThreadExecutor();
    CountDownLatch readyLatch = new CountDownLatch(READER_THREAD_COUNT + 1);
    CountDownLatch startLatch = new CountDownLatch(1);

    long elapsedNanos;
    try {
      for (int i = 0; i < READER_THREAD_COUNT; i++) {
        readers.submit(
            readerTask(
                scanner,
                legalOutputs,
                observedOutputs,
                stop,
                totalScans,
                readerFailures,
                tornReads,
                readyLatch,
                startLatch));
      }
      writer.submit(
          writerTask(
              registry,
              List.of(universalPack),
              List.of(europePack),
              stop,
              readyLatch,
              startLatch,
              readerFailures));

      assertThat(readyLatch.await(5, TimeUnit.SECONDS))
          .as("threads failed to reach the start barrier within 5s")
          .isTrue();

      long startNanos = System.nanoTime();
      startLatch.countDown();
      Thread.sleep(TimeUnit.SECONDS.toMillis(STRESS_DURATION_SECONDS));
      stop.set(true);

      readers.shutdown();
      writer.shutdown();
      assertThat(readers.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .as("readers did not drain within %ds", SHUTDOWN_TIMEOUT_SECONDS)
          .isTrue();
      assertThat(writer.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .as("writer did not drain within %ds", SHUTDOWN_TIMEOUT_SECONDS)
          .isTrue();

      elapsedNanos = System.nanoTime() - startNanos;
    } finally {
      readers.shutdownNow();
      writer.shutdownNow();
    }

    reportThroughput(totalScans.get(), elapsedNanos);

    assertThat(readerFailures)
        .as(
            "no reader should throw — got: %s",
            readerFailures.peek() == null ? "(none)" : readerFailures.peek().toString())
        .isEmpty();
    assertThat(tornReads)
        .as("a torn read indicates the registry leaked mid-swap state into an in-flight tokenize")
        .isEmpty();
    assertThat(totalScans.get())
        .as("no tokenize() calls completed — stress harness failed to schedule reader work")
        .isPositive();
    assertThat(observedOutputs)
        .as(
            "the writer must successfully swap the registry at least once during the run; "
                + "otherwise the test degenerates into a single-pack benchmark")
        .containsExactlyInAnyOrderElementsOf(legalOutputs);
  }

  private static @NonNull Set<String> computeLegalOutputs(
      PrismTextScanner scanner,
      PrismRulePackRegistry registry,
      PrismRulePack universalPack,
      PrismRulePack europePack) {
    Set<String> legal = new HashSet<>();

    registry.replace(List.of(universalPack));
    legal.add(scanner.tokenize(STRESS_PROMPT));

    registry.replace(List.of(europePack));
    legal.add(scanner.tokenize(STRESS_PROMPT));

    return Set.copyOf(legal);
  }

  private static @NonNull Runnable readerTask(
      PrismTextScanner scanner,
      Set<String> legalOutputs,
      Set<String> observedOutputs,
      AtomicBoolean stop,
      AtomicLong totalScans,
      ConcurrentLinkedQueue<Throwable> readerFailures,
      ConcurrentLinkedQueue<String> tornReads,
      CountDownLatch readyLatch,
      CountDownLatch startLatch) {
    return () -> {
      try {
        readyLatch.countDown();
        startLatch.await();
        long localScans = 0L;
        while (!stop.get()) {
          // The production hot path. If the registry can leak a half-swapped state into
          // tokenize(), this is where it will surface — either as a ConcurrentModificationException
          // (caught below) or as an output that doesn't match either reference tokenization.
          String tokenized = scanner.tokenize(STRESS_PROMPT);
          if (tokenized == null || !legalOutputs.contains(tokenized)) {
            tornReads.add(
                "non-legal tokenize output observed: "
                    + (tokenized == null ? "<null>" : truncate(tokenized)));
            return;
          }
          observedOutputs.add(tokenized);
          localScans++;
        }
        totalScans.addAndGet(localScans);
      } catch (Throwable t) {
        readerFailures.add(t);
      }
    };
  }

  private static @NonNull Runnable writerTask(
      PrismRulePackRegistry registry,
      List<PrismRulePack> packA,
      List<PrismRulePack> packB,
      AtomicBoolean stop,
      CountDownLatch readyLatch,
      CountDownLatch startLatch,
      ConcurrentLinkedQueue<Throwable> readerFailures) {
    return () -> {
      try {
        readyLatch.countDown();
        startLatch.await();
        boolean flip = false;
        while (!stop.get()) {
          // Contract: "constantly performs swaps". No sleep — we want maximum overlap with
          // in-flight tokenize iterations to maximise the chance of a partial-publication
          // race surfacing.
          registry.replace(flip ? packB : packA);
          flip = !flip;
        }
      } catch (Throwable t) {
        readerFailures.add(t);
      }
    };
  }

  private static void reportThroughput(long totalScans, long elapsedNanos) {
    double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
    double scansPerSecond = totalScans / elapsedSeconds;
    System.out.printf(
        "[RegistryConcurrencyStressTest] %d tokenize() calls across %d threads in %.2fs"
            + " = %.0f scans/s (~%.0f scans/s per thread)%n",
        totalScans,
        READER_THREAD_COUNT,
        elapsedSeconds,
        scansPerSecond,
        scansPerSecond / READER_THREAD_COUNT);
    // Soft floor: tokenize() is far heavier than a raw snapshot read (it walks every detector
    // in the active pack and hits the vault for every PII candidate), so we set a generous
    // 50 scans/sec per thread aggregated across the run. Anything below this on commodity CI
    // hardware suggests the hot path is blocked rather than merely slow.
    long softFloor = (long) READER_THREAD_COUNT * STRESS_DURATION_SECONDS * 50L;
    assertThat(totalScans)
        .as(
            "throughput suspiciously low (%d scans < soft floor %d) — hot path may be blocking",
            totalScans, softFloor)
        .isGreaterThanOrEqualTo(softFloor);
  }

  private static @NonNull String truncate(String value) {
    return value.length() <= 120 ? value : value.substring(0, 117) + "...";
  }
}
