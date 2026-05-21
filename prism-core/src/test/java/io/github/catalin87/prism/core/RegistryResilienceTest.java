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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
 * Verifies the "poison pill" resilience contract of {@link PrismRulePackRegistry}: a structurally
 * broken candidate ruleset is rejected before it can replace the active one, the previously
 * published ruleset stays intact, and the rejection is observable via the metrics sink.
 *
 * <p>Behavioral fail-safe — a detector that throws from {@link PiiDetector#detect(String)} — is the
 * scanner's responsibility (it traps the throw, reports {@code onDetectionError}, and keeps
 * scanning with the remaining detectors). The registry intentionally accepts such packs at swap
 * time because pre-flight cannot simulate the live input.
 */
class RegistryResilienceTest {

  @Test
  void poisonPillWithBlankPackNameIsRejectedAndPriorRulesetSurvives() {
    RecordingSink sink = new RecordingSink();
    NamedPack initialPack = new NamedPack("seed", List.of(new EchoDetector("EMAIL")));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initialPack), sink);

    NamedPack poisonPack = new NamedPack("   ", List.of(new EchoDetector("PHONE")));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poisonPack)));

    assertEquals(0, sink.swaps.get(), "Rejected update must not fire onSwap");
    assertEquals(1, sink.errors.get(), "Rejected update must fire onSwapError exactly once");
    assertSame(error, sink.lastError.get(), "Sink must observe the validator's exception verbatim");
    assertEquals(
        List.of(initialPack),
        registry.snapshot(),
        "Active ruleset must remain on the prior generation when a swap is rejected");
  }

  @Test
  void poisonPillWithNullDetectorIsRejectedAndPriorRulesetSurvives() {
    RecordingSink sink = new RecordingSink();
    NamedPack initialPack = new NamedPack("seed", List.of(new EchoDetector("EMAIL")));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initialPack), sink);

    NamedPack poisonPack = new NamedPack("us-pack", Arrays.asList(new EchoDetector("SSN"), null));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poisonPack)));

    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
    assertSame(error, sink.lastError.get());
    assertEquals(List.of(initialPack), registry.snapshot());
  }

  @Test
  void poisonPillWithBlankEntityTypeIsRejectedAndPriorRulesetSurvives() {
    RecordingSink sink = new RecordingSink();
    NamedPack initialPack = new NamedPack("seed", List.of(new EchoDetector("EMAIL")));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initialPack), sink);

    NamedPack poisonPack = new NamedPack("ro-pack", List.of(new EchoDetector("")));

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poisonPack)));

    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
    assertSame(error, sink.lastError.get());
    assertEquals(List.of(initialPack), registry.snapshot());
  }

  @Test
  void packWhoseGetDetectorsThrowsIsRejectedAndPriorRulesetSurvives() {
    RecordingSink sink = new RecordingSink();
    NamedPack initialPack = new NamedPack("seed", List.of(new EchoDetector("EMAIL")));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initialPack), sink);

    PrismRulePack throwingPack =
        new PrismRulePack() {
          @Override
          public @NonNull String getName() {
            return "throwing-pack";
          }

          @Override
          public @NonNull List<@NonNull PiiDetector> getDetectors() {
            throw new IllegalStateException("detector enumeration intentionally broken");
          }
        };

    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(throwingPack)));
    assertInstanceOf(
        IllegalStateException.class,
        error.getCause(),
        "Validator should preserve the original failure as the cause");

    assertEquals(0, sink.swaps.get());
    assertEquals(1, sink.errors.get());
    assertEquals(List.of(initialPack), registry.snapshot());
  }

  @Test
  void runtimePoisonPackPassesValidationSoScannerSideFailSafeCanHandleIt() {
    // Behavioral failures are scanner-side concerns. A pack whose detector is structurally valid
    // but throws on detect() must still be acceptable to the registry; the scanner traps the throw
    // via metricsSink.onDetectionError() and keeps scanning with remaining detectors. This test
    // pins the contract so future tightening of validation does not over-reach.
    RecordingSink sink = new RecordingSink();
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), sink);

    PrismRulePack runtimePoison =
        new NamedPack(
            "runtime-poison",
            List.of(
                new PiiDetector() {
                  @Override
                  public @NonNull String getEntityType() {
                    return "BOOM";
                  }

                  @Override
                  public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
                    throw new RuntimeException("intentional runtime failure");
                  }
                }));

    registry.update(List.of(runtimePoison));

    assertEquals(1, sink.swaps.get(), "Structurally valid pack must pass pre-flight");
    assertEquals(0, sink.errors.get());
    assertSame(runtimePoison, registry.snapshot().get(0));
  }

  @Test
  void multiplePoisonAttemptsLeaveActiveRulesetUntouched() {
    RecordingSink sink = new RecordingSink();
    NamedPack initialPack = new NamedPack("seed", List.of(new EchoDetector("EMAIL")));
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(initialPack), sink);

    // First valid swap establishes a "good generation" different from the seed.
    NamedPack goodPack = new NamedPack("good", List.of(new EchoDetector("PHONE")));
    registry.update(List.of(goodPack));
    List<PrismRulePack> goodGeneration = registry.snapshot();

    // Repeated poison attempts must not budge the active ruleset off the good generation.
    NamedPack poison1 = new NamedPack("", List.of(new EchoDetector("X")));
    NamedPack poison2 = new NamedPack("p2", Arrays.asList(new EchoDetector("Y"), null));
    NamedPack poison3 = new NamedPack("p3", List.of(new EchoDetector("")));

    assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poison1)));
    assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poison2)));
    assertThrows(IllegalArgumentException.class, () -> registry.update(List.of(poison3)));

    assertEquals(1, sink.swaps.get(), "Only the good swap fired onSwap");
    assertEquals(3, sink.errors.get(), "Each rejected swap fired onSwapError");
    assertSame(
        goodGeneration,
        registry.snapshot(),
        "Identity-stable: rejected swaps never even replaced the AtomicReference target");
  }

  @Test
  void seedRulesetIsValidatedAtConstructionSoBrokenInitialPacksFailFast() {
    RecordingSink sink = new RecordingSink();
    NamedPack broken = new NamedPack("", List.of(new EchoDetector("EMAIL")));

    assertThrows(
        IllegalArgumentException.class,
        () -> new PrismRulePackRegistry(List.of(broken), sink),
        "Constructor must apply the same pre-flight as update()");
  }

  /** Hand-rolled sink with atomic counters; keeps the test free of Mockito and Micrometer. */
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

  /** Pack whose name and detector list are caller-controlled — the basis for every poison case. */
  private static final class NamedPack implements PrismRulePack {
    private final String name;
    private final List<PiiDetector> detectors;

    NamedPack(@NonNull String name, @NonNull List<PiiDetector> detectors) {
      this.name = name;
      this.detectors = detectors;
    }

    @Override
    public @NonNull String getName() {
      return name;
    }

    @Override
    @SuppressWarnings("NullAway") // Tests intentionally pass lists that contain a null detector.
    public @NonNull List<@NonNull PiiDetector> getDetectors() {
      return detectors;
    }
  }

  /** Minimal detector stub: structurally valid, never matches. Used as a benign reference pack. */
  private static final class EchoDetector implements PiiDetector {
    private final String entityType;

    EchoDetector(@NonNull String entityType) {
      this.entityType = entityType;
    }

    @Override
    public @NonNull String getEntityType() {
      return entityType;
    }

    @Override
    public @NonNull List<@NonNull PiiCandidate> detect(@NonNull String text) {
      return Collections.emptyList();
    }
  }
}
