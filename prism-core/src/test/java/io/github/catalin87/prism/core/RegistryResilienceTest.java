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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

/**
 * "Edge case / fail-safe" coverage for {@link PrismRulePackRegistry} and its {@link
 * PrismRulePackValidator}. The goal is to prove that a misconfigured or buggy "poison pill"
 * rule-pack cannot corrupt the active ruleset: every failure path must reject the update, fire
 * {@link PrismRegistryMetricsSink#onRegistryError(Throwable)}, and leave the previous snapshot
 * fully intact.
 *
 * <p>Per-detector exceptions thrown from {@link PiiDetector#detect(String)} are handled separately
 * by the scanner's fail-safe wrapper (see {@code PrismTextScanner}) and are documented here for
 * traceability: a detector that throws at scan time MUST NOT crash the registry — that path is
 * tested in the spring-ai and langchain4j scanner test suites because the scanner itself lives
 * outside prism-core.
 */
class RegistryResilienceTest {

  @Test
  void packWhoseGetDetectorsThrowsIsRejectedAndPreviousSnapshotSurvives() {
    PrismRulePack previousValid = new UniversalRulePack();
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(previousValid), sink);

    PrismRulePack poison = new PoisonGetDetectorsPack("poison-pack");
    PrismRulePackValidationException thrown =
        assertThrows(
            PrismRulePackValidationException.class, () -> registry.replace(List.of(poison)));

    assertTrue(
        thrown.getMessage().contains("poison-pack"),
        "validator must name the offending pack in its message, got: " + thrown.getMessage());
    assertNotNull(thrown.getCause(), "the underlying poison-pill failure must be preserved");

    // Atomicity: the previous snapshot must still be the active one, byte-for-byte.
    List<PrismRulePack> snapshot = registry.snapshot();
    assertEquals(1, snapshot.size(), "rejected swap must not mutate the active snapshot");
    assertSame(
        previousValid, snapshot.get(0), "rejected swap must preserve the previous pack identity");

    // Observability: the failure must be reported exactly once, and no successful swap recorded.
    assertEquals(0, sink.swaps.get(), "a rejected swap must never increment the success counter");
    assertEquals(1, sink.errors.get(), "a rejected swap must fire onRegistryError exactly once");
    assertSame(
        thrown,
        sink.lastError.get(),
        "the sink must observe the validator's exception verbatim, not a wrapped form");
  }

  @Test
  void packWithBlankNameIsRejectedByStructuralValidator() {
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    PrismRulePackValidationException thrown =
        assertThrows(
            PrismRulePackValidationException.class,
            () -> registry.replace(List.of(new BlankNamePack())));

    assertTrue(
        thrown.getMessage().contains("null or blank name"),
        "validator must report the structural cause, got: " + thrown.getMessage());
    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
  }

  @Test
  void packWithDetectorMissingEntityTypeIsRejected() {
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    PrismRulePack pack = new SimplePack("partial-pack", List.of(new BlankEntityTypeDetector()));

    PrismRulePackValidationException thrown =
        assertThrows(PrismRulePackValidationException.class, () -> registry.replace(List.of(pack)));

    assertTrue(
        thrown.getMessage().contains("partial-pack"),
        "validator message must include the offending pack name");
    assertTrue(
        thrown.getMessage().contains("entity type"),
        "validator message must describe the missing entity type, got: " + thrown.getMessage());
    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
  }

  @Test
  void noopValidatorBypassesStructuralChecksWhenExplicitlyRequested() {
    // The NOOP validator exists for callers that want to opt out of pre-flight checks (e.g. tests
    // that need to install a deliberately broken pack to exercise a downstream behaviour). It must
    // accept whatever it is given, so the previous-snapshot guarantee is delegated to the caller.
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry =
        new PrismRulePackRegistry(List.of(), sink, PrismRulePackValidator.NOOP);

    PrismRulePack poison = new PoisonGetDetectorsPack("bypassed-pack");
    registry.replace(List.of(poison));

    assertEquals(1, registry.snapshot().size(), "NOOP validator must let the swap through");
    assertEquals(1, sink.swaps.get(), "NOOP success path must fire the success callback");
    assertEquals(0, sink.errors.get());
  }

  @Test
  void customValidatorIsInvokedExactlyOncePerReplaceAttempt() {
    AtomicInteger invocations = new AtomicInteger();
    PrismRulePackValidator counting = packs -> invocations.incrementAndGet();
    CountingSink sink = new CountingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink, counting);

    // Initial construction validates once.
    int afterConstruction = invocations.get();
    assertEquals(1, afterConstruction, "the validator must run against the initial snapshot");

    registry.replace(List.of(new UniversalRulePack()));
    registry.replace(List.of());

    assertEquals(
        3, invocations.get(), "every replace(...) must invoke the validator exactly once more");
    assertEquals(2, sink.swaps.get());
    assertEquals(0, sink.errors.get());
  }

  @Test
  void constructorRejectsAPoisonPackInTheInitialSeed() {
    // Atomicity at construction time: a poisoned initial seed must not produce a registry instance
    // that returns a corrupted snapshot. The exception propagates and no registry is constructed.
    assertThrows(
        PrismRulePackValidationException.class,
        () ->
            new PrismRulePackRegistry(
                List.of(new PoisonGetDetectorsPack("seeded-poison")),
                PrismRegistryMetricsSink.NOOP));
  }

  @Test
  void scannerLikeLoopContinuesPastAThrowingDetectorWhenWrappedByTheCaller() {
    // Documents the contract relied upon by PrismTextScanner: a detector that throws from detect()
    // is the *scanner's* responsibility to swallow, not the registry's. The registry must publish
    // such a detector if the validator accepts it (a runtime-throwing detector is structurally
    // valid — only its behaviour is bad). This test simulates the scanner's per-detector fail-safe
    // wrapper using the validator-accepted detector and confirms the iteration completes.
    PrismRulePackValidator permissiveStructural = PrismRulePackValidator.STRUCTURAL;
    PrismRulePack runtimePoison =
        new SimplePack(
            "runtime-poison", List.of(new ThrowingDetector("CARD"), new BenignDetector("EMAIL")));
    permissiveStructural.validate(List.of(runtimePoison));

    int benignSuccesses = 0;
    int detectFailures = 0;
    for (PiiDetector detector : runtimePoison.getDetectors()) {
      try {
        List<PiiCandidate> ignored = detector.detect("sample@example.com");
        if (ignored != null) {
          benignSuccesses++;
        }
      } catch (RuntimeException ignored) {
        detectFailures++;
      }
    }
    assertEquals(1, detectFailures, "the throwing detector must surface its failure to the caller");
    assertEquals(
        1,
        benignSuccesses,
        "the benign detector must still be invoked despite an earlier sibling throwing");
  }

  // ----- Test doubles -----

  /** Pack whose {@link #getDetectors()} throws — the canonical "poison pill" case. */
  private static final class PoisonGetDetectorsPack implements PrismRulePack {

    private final String name;

    PoisonGetDetectorsPack(String name) {
      this.name = name;
    }

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<@NonNull PiiDetector> getDetectors() {
      throw new IllegalStateException("simulated misconfiguration in " + name);
    }
  }

  /** Pack with a blank name — caught by the structural validator's name check. */
  private static final class BlankNamePack implements PrismRulePack {

    @Override
    public @NonNull String getName() {
      return "   ";
    }

    @Override
    public @NonNull List<@NonNull PiiDetector> getDetectors() {
      return List.of();
    }
  }

  /** Minimal well-formed pack used to compose mixed-validity test scenarios. */
  private static final class SimplePack implements PrismRulePack {

    private final String name;
    private final List<PiiDetector> detectors;

    SimplePack(String name, List<PiiDetector> detectors) {
      this.name = name;
      this.detectors = detectors;
    }

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    public @NonNull List<@NonNull PiiDetector> getDetectors() {
      return detectors;
    }
  }

  /** Detector whose {@link #getEntityType()} returns blank — rejected by the structural check. */
  private static final class BlankEntityTypeDetector implements PiiDetector {

    @Override
    public @NonNull String getEntityType() {
      return "";
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      return List.of();
    }
  }

  /** Detector that throws from {@link #detect(String)} but is structurally valid. */
  private static final class ThrowingDetector implements PiiDetector {

    private final String entityType;

    ThrowingDetector(String entityType) {
      this.entityType = entityType;
    }

    @Override
    public @NonNull String getEntityType() {
      return entityType;
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      throw new IllegalStateException("runtime fault inside detector " + entityType);
    }
  }

  /** Detector that never throws and never matches — used as a sibling of a throwing detector. */
  private static final class BenignDetector implements PiiDetector {

    private final String entityType;

    BenignDetector(String entityType) {
      this.entityType = entityType;
    }

    @Override
    public @NonNull String getEntityType() {
      return entityType;
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      return List.of();
    }
  }

  /** Counting sink — records swap/error invocations and the last error observed. */
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
