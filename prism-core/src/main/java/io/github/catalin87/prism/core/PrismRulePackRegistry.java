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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;

/**
 * Thread-safe holder for the active set of {@link PrismRulePack} instances used by Spring Prism
 * scanners. Designed for the read-heavy / write-rare profile of PII detection: every chat request
 * issues exactly one {@link #snapshot()} call, while operators replace the ruleset only a handful
 * of times per day.
 *
 * <h2>Concurrency model</h2>
 *
 * <p>Internally backed by a single {@link AtomicReference} whose payload is always an unmodifiable
 * list produced by {@link List#copyOf(java.util.Collection)}. Two properties combine to satisfy
 * "zero-lock reads with atomic swap":
 *
 * <ul>
 *   <li><b>Atomic publication.</b> {@link AtomicReference#set(Object)} stores with volatile
 *       semantics; readers observe either the entire prior list or the entire new list, never a
 *       partial state.
 *   <li><b>Frozen snapshots.</b> Because each published list is structurally immutable, callers
 *       iterating a snapshot cannot encounter {@link java.util.ConcurrentModificationException} or
 *       torn reads, even if {@link #update(List)} runs concurrently.
 * </ul>
 *
 * <p>The recommended consumption pattern is <em>snapshot-per-call</em>: a scanner captures {@link
 * #snapshot()} into a local variable at the top of its scan method and iterates that local for the
 * duration of the call. A scan that started against ruleset N therefore finishes against ruleset N,
 * even if an update lands mid-scan.
 *
 * <h2>Observability</h2>
 *
 * <p>An optional {@link PrismRegistryMetricsSink} can be supplied at construction time. Each
 * successful {@link #update(List)} call fires {@link PrismRegistryMetricsSink#onSwap()}; failures
 * (e.g. a null element rejected by {@link List#copyOf(java.util.Collection)}) fire {@link
 * PrismRegistryMetricsSink#onSwapError(Throwable)} before the exception propagates. The sink
 * abstraction keeps {@code prism-core} free of any Micrometer dependency; the {@code
 * prism-spring-boot-starter} module wires a Micrometer-backed implementation when a {@code
 * MeterRegistry} bean is available.
 *
 * @since 1.2.0
 */
public final class PrismRulePackRegistry {

  private final AtomicReference<@NonNull List<@NonNull PrismRulePack>> rulePacksRef;
  private final PrismRegistryMetricsSink metricsSink;
  private final PrismRulePackValidator validator;

  /**
   * Creates a registry seeded with the supplied rule packs. The supplied list is defensively
   * copied; subsequent mutations of the caller's list have no effect on the registry. Equivalent to
   * {@link #PrismRulePackRegistry(List, PrismRegistryMetricsSink)} with {@link
   * PrismRegistryMetricsSink#NOOP}.
   *
   * @param initialPacks The initial ruleset. Must not be {@code null} (use an empty list to start
   *     with no packs).
   */
  public PrismRulePackRegistry(@NonNull List<@NonNull PrismRulePack> initialPacks) {
    this(initialPacks, PrismRegistryMetricsSink.NOOP);
  }

  /**
   * Creates a registry seeded with the supplied rule packs and wired to the supplied observability
   * sink. The default {@link PrismRulePackValidator#DEFAULT structural validator} is used.
   *
   * @param initialPacks The initial ruleset. Must not be {@code null}.
   * @param metricsSink The observability sink notified on every {@link #update(List)} call. Use
   *     {@link PrismRegistryMetricsSink#NOOP} to opt out.
   */
  public PrismRulePackRegistry(
      @NonNull List<@NonNull PrismRulePack> initialPacks,
      @NonNull PrismRegistryMetricsSink metricsSink) {
    this(initialPacks, metricsSink, PrismRulePackValidator.DEFAULT);
  }

  /**
   * Creates a registry seeded with the supplied rule packs, observability sink, and pre-flight
   * validator.
   *
   * <p>The supplied {@code initialPacks} are passed through the validator before publication,
   * matching the contract enforced by {@link #update(List)}: if the seed ruleset itself is invalid
   * the constructor fails fast rather than letting a broken registry come online.
   *
   * @param initialPacks The initial ruleset. Must not be {@code null}.
   * @param metricsSink The observability sink notified on every {@link #update(List)} call.
   * @param validator The pre-flight validator. Use {@link PrismRulePackValidator#NOOP} to skip
   *     structural checks; use {@link PrismRulePackValidator#DEFAULT} for the built-in checks.
   * @since 1.2.0
   */
  public PrismRulePackRegistry(
      @NonNull List<@NonNull PrismRulePack> initialPacks,
      @NonNull PrismRegistryMetricsSink metricsSink,
      @NonNull PrismRulePackValidator validator) {
    Objects.requireNonNull(initialPacks, "initialPacks");
    this.metricsSink = Objects.requireNonNull(metricsSink, "metricsSink");
    this.validator = Objects.requireNonNull(validator, "validator");
    List<@NonNull PrismRulePack> defensiveCopy = List.copyOf(initialPacks);
    this.validator.validate(defensiveCopy);
    this.rulePacksRef = new AtomicReference<>(defensiveCopy);
  }

  /**
   * Returns the current ruleset as an immutable list. Hot path — one volatile load, no copying, no
   * locking. The returned list is safe to iterate concurrently with {@link #update(List)}.
   *
   * @return The current ruleset snapshot.
   */
  public @NonNull List<@NonNull PrismRulePack> snapshot() {
    return rulePacksRef.get();
  }

  /**
   * Atomically replaces the current ruleset. Cold path — expected to be invoked on the order of a
   * few times per day from an operator-facing endpoint. The supplied list is defensively copied and
   * passed through the configured {@link PrismRulePackValidator} <em>before</em> publication.
   *
   * <p>Atomicity guarantee: either the entire new ruleset is published, or the active ruleset
   * remains exactly as it was. The atomic publication only runs after both the defensive copy and
   * the validator return normally — there is no window in which readers can observe a partially
   * applied or invalid swap.
   *
   * <p>On success, {@link PrismRegistryMetricsSink#onSwap()} fires after publication. If the
   * defensive copy rejects a null element, or the validator throws, {@link
   * PrismRegistryMetricsSink#onSwapError(Throwable)} fires and the original exception is rethrown
   * unchanged — callers see exactly the same exception type the validator threw.
   *
   * @param newPacks The replacement ruleset. Must not be {@code null}.
   * @throws IllegalArgumentException When the configured validator rejects the candidate ruleset.
   *     The active ruleset is left untouched.
   * @throws NullPointerException When {@code newPacks} contains a {@code null} element. The active
   *     ruleset is left untouched.
   */
  public void update(@NonNull List<@NonNull PrismRulePack> newPacks) {
    Objects.requireNonNull(newPacks, "newPacks");
    List<@NonNull PrismRulePack> defensiveCopy;
    try {
      defensiveCopy = List.copyOf(newPacks);
      validator.validate(defensiveCopy);
    } catch (RuntimeException error) {
      metricsSink.onSwapError(error);
      throw error;
    }
    rulePacksRef.set(defensiveCopy);
    metricsSink.onSwap();
  }
}
