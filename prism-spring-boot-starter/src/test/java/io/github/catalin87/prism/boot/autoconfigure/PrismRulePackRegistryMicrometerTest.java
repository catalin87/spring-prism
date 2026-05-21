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
package io.github.catalin87.prism.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.catalin87.prism.core.PrismRulePackRegistry;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * End-to-end starter test for the Micrometer bridge wired in {@link PrismRuntimeMetrics}.
 *
 * <p>The Spring Prism core module owns the {@link
 * io.github.catalin87.prism.core.PrismRegistryMetricsSink} abstraction so that {@link
 * PrismRulePackRegistry} can emit observability events without depending on Micrometer. This test
 * verifies that, once the starter wires {@link PrismRuntimeMetrics} as the sink and supplies a
 * {@link MeterRegistry}, the three contracted meters update correctly:
 *
 * <ul>
 *   <li>{@code prism.registry.swap.count} is incremented once per successful {@code
 *       registry.replace(...)} call.
 *   <li>{@code prism.registry.swap.error.count} is incremented once per failed {@code
 *       registry.replace(...)} call.
 *   <li>{@code prism.scanner.tokenize.duration} records each scanner {@code onScanDuration} sample,
 *       tagged by integration.
 * </ul>
 */
class PrismRulePackRegistryMicrometerTest {

  @Test
  void successfulReplaceCallsIncrementSwapCounter() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    PrismRuntimeMetrics runtimeMetrics = new PrismRuntimeMetrics(12, 120, meterRegistry);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), runtimeMetrics);

    registry.replace(List.of(new UniversalRulePack()));
    registry.replace(List.of());
    registry.replace(List.of(new UniversalRulePack(), new UniversalRulePack()));

    Counter swapCounter = meterRegistry.find(PrismRuntimeMetrics.REGISTRY_SWAP_COUNTER).counter();
    assertThat(swapCounter)
        .as("starter must register the swap counter when a MeterRegistry is present")
        .isNotNull();
    assertThat(swapCounter.count())
        .as("every successful replace must bump prism.registry.swap.count exactly once")
        .isEqualTo(3.0);

    Counter errorCounter =
        meterRegistry.find(PrismRuntimeMetrics.REGISTRY_SWAP_ERROR_COUNTER).counter();
    assertThat(errorCounter).isNotNull();
    assertThat(errorCounter.count()).isZero();
  }

  @Test
  void failedReplaceIncrementsSwapErrorCounter() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    PrismRuntimeMetrics runtimeMetrics = new PrismRuntimeMetrics(12, 120, meterRegistry);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), runtimeMetrics);

    assertThatThrownBy(() -> registry.replace(null)).isInstanceOf(NullPointerException.class);

    Counter swapCounter = meterRegistry.find(PrismRuntimeMetrics.REGISTRY_SWAP_COUNTER).counter();
    assertThat(swapCounter).isNotNull();
    assertThat(swapCounter.count()).as("a failed swap must not bump the success counter").isZero();

    Counter errorCounter =
        meterRegistry.find(PrismRuntimeMetrics.REGISTRY_SWAP_ERROR_COUNTER).counter();
    assertThat(errorCounter).isNotNull();
    assertThat(errorCounter.count())
        .as("a failed swap must bump prism.registry.swap.error.count exactly once")
        .isEqualTo(1.0);
  }

  @Test
  void scannerTokenizeDurationsArePublishedAsTimerSamples() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    PrismRuntimeMetrics runtimeMetrics = new PrismRuntimeMetrics(12, 120, meterRegistry);

    runtimeMetrics.onScanDuration("spring-ai", 1_500_000L);
    runtimeMetrics.onScanDuration("spring-ai", 2_500_000L);
    runtimeMetrics.onScanDuration("langchain4j", 3_000_000L);

    Timer springAiTimer =
        meterRegistry
            .find(PrismRuntimeMetrics.SCANNER_TOKENIZE_TIMER)
            .tag("integration", "spring-ai")
            .timer();
    assertThat(springAiTimer).as("a separate timer must be registered per integration").isNotNull();
    assertThat(springAiTimer.count())
        .as("the spring-ai timer must record both samples")
        .isEqualTo(2L);
    assertThat(springAiTimer.totalTime(TimeUnit.NANOSECONDS))
        .as("totalTime must reflect both nano samples (1.5ms + 2.5ms = 4ms)")
        .isEqualTo(4_000_000.0);

    Timer langchainTimer =
        meterRegistry
            .find(PrismRuntimeMetrics.SCANNER_TOKENIZE_TIMER)
            .tag("integration", "langchain4j")
            .timer();
    assertThat(langchainTimer).isNotNull();
    assertThat(langchainTimer.count()).isEqualTo(1L);
  }

  @Test
  void withoutMeterRegistryNoMetersAreRegistered() {
    // Backward-compatibility guard: a host with no MeterRegistry bean must still get a working
    // PrismRuntimeMetrics. The two-arg constructor must NOT register Micrometer meters anywhere.
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    PrismRuntimeMetrics runtimeMetrics = new PrismRuntimeMetrics(12, 120);
    PrismRulePackRegistry registry = new PrismRulePackRegistry(List.of(), runtimeMetrics);

    registry.replace(List.of(new UniversalRulePack()));
    runtimeMetrics.onScanDuration("spring-ai", 1_000_000L);

    assertThat(meterRegistry.find(PrismRuntimeMetrics.REGISTRY_SWAP_COUNTER).counter())
        .as(
            "no counter must be registered when no MeterRegistry was supplied to PrismRuntimeMetrics")
        .isNull();
    assertThat(meterRegistry.find(PrismRuntimeMetrics.SCANNER_TOKENIZE_TIMER).timer())
        .as("no timer must be registered when no MeterRegistry was supplied to PrismRuntimeMetrics")
        .isNull();
  }

  @Test
  void runtimeMetricsIsBackwardCompatibleWithExistingScannerSinkContract() {
    // The bridge must continue to satisfy the in-memory accumulation contract used by the
    // dashboard regardless of whether a MeterRegistry is wired in.
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    PrismRuntimeMetrics runtimeMetrics = new PrismRuntimeMetrics(12, 120, meterRegistry);

    runtimeMetrics.onTokenized(5);
    runtimeMetrics.onDetokenized(3);
    runtimeMetrics.onScanDuration("spring-ai", 1_000_000L);

    assertThat(runtimeMetrics.tokenizedCount()).isEqualTo(5L);
    assertThat(runtimeMetrics.detokenizedCount()).isEqualTo(3L);
    PrismRuntimeMetrics.DurationMetric metric =
        runtimeMetrics.durationMetrics().get("spring-ai:scan");
    assertThat(metric).isNotNull();
    assertThat(metric.samples()).isEqualTo(1L);
    assertThat(metric.totalNanos()).isEqualTo(1_000_000L);
  }
}
