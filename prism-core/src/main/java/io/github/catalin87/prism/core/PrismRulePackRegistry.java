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
 * Thread-safe registry that holds the active {@link PrismRulePack} list used by Spring Prism
 * scanners and exposes it as an immutable snapshot.
 *
 * <p>The registry is designed for high-concurrency read paths (PII detection runs on every request)
 * combined with very rare writes (operational rule-set updates, on the order of a few per day). The
 * current snapshot is held in an {@link AtomicReference}, so {@link #snapshot()} performs a single
 * volatile read with no locks and no allocation. {@link #replace(List)} atomically swaps in a
 * defensive {@link List#copyOf(java.util.Collection) immutable copy} of its argument.
 *
 * <p><strong>Atomicity contract:</strong> a concurrent reader of {@link #snapshot()} observes
 * either the previous list or the new list in full; it never observes a partially updated state.
 * The returned snapshot is immutable and may be iterated by the caller without external
 * synchronisation. A {@code replace(...)} call that fails validation leaves the previous snapshot
 * completely intact — partial updates are impossible.
 *
 * <p><strong>Observability:</strong> an optional {@link PrismRegistryMetricsSink} can be supplied
 * to the {@linkplain #PrismRulePackRegistry(List, PrismRegistryMetricsSink) two-arg constructor}.
 * The sink is invoked on every {@link #replace(List)} call — {@code onRegistrySwap()} on success,
 * {@code onRegistryError(...)} on failure (always before the exception is rethrown). The single-arg
 * constructor wires {@link PrismRegistryMetricsSink#NOOP} so unobserved deployments incur no
 * overhead. Sinks must be thread-safe and must not throw.
 *
 * <p><strong>Pre-flight validation:</strong> the {@linkplain #PrismRulePackRegistry(List,
 * PrismRegistryMetricsSink, PrismRulePackValidator) three-arg constructor} accepts a {@link
 * PrismRulePackValidator} that runs structural checks against the candidate list before the swap.
 * If the validator throws, the previous snapshot is preserved and the failure is routed through
 * {@code onRegistryError(...)}. The default {@link PrismRulePackValidator#STRUCTURAL} validator
 * rejects "poison pill" packs (e.g. one whose {@code getDetectors()} itself throws) before they can
 * reach the active ruleset, guaranteeing that a buggy or misconfigured pack cannot corrupt the
 * scanner.
 *
 * @since 1.3.0
 */
public final class PrismRulePackRegistry {

  private final AtomicReference<@NonNull List<@NonNull PrismRulePack>> ref;
  private final PrismRegistryMetricsSink metricsSink;
  private final PrismRulePackValidator validator;

  /**
   * Creates a registry seeded with the given packs, no metrics instrumentation, and the default
   * {@link PrismRulePackValidator#STRUCTURAL} validator.
   *
   * @param initial The initial rule-pack list. Must not be {@code null} and must not contain {@code
   *     null} elements.
   * @throws NullPointerException if {@code initial} is {@code null} or contains {@code null}
   *     elements.
   * @throws PrismRulePackValidationException if {@code initial} fails the structural contract.
   */
  public PrismRulePackRegistry(@NonNull List<@NonNull PrismRulePack> initial) {
    this(initial, PrismRegistryMetricsSink.NOOP, PrismRulePackValidator.STRUCTURAL);
  }

  /**
   * Creates a registry seeded with the given packs, wired to {@code metricsSink}, and using the
   * default {@link PrismRulePackValidator#STRUCTURAL} validator.
   *
   * @param initial The initial rule-pack list.
   * @param metricsSink Sink for registry observability events. Pass {@link
   *     PrismRegistryMetricsSink#NOOP} to disable instrumentation.
   * @throws NullPointerException if {@code initial} or {@code metricsSink} is {@code null}, or if
   *     {@code initial} contains {@code null} elements.
   * @throws PrismRulePackValidationException if {@code initial} fails the structural contract.
   */
  public PrismRulePackRegistry(
      @NonNull List<@NonNull PrismRulePack> initial,
      @NonNull PrismRegistryMetricsSink metricsSink) {
    this(initial, metricsSink, PrismRulePackValidator.STRUCTURAL);
  }

  /**
   * Creates a registry seeded with the given packs, wired to {@code metricsSink}, and validated by
   * {@code validator}. The initial snapshot is validated up front; if it fails, no registry is
   * constructed and the failure propagates to the caller. The initial snapshot is <em>not</em>
   * reported as a swap — only subsequent {@code replace(...)} calls fire the metrics sink.
   *
   * @param initial The initial rule-pack list. Must not be {@code null} and must not contain {@code
   *     null} elements.
   * @param metricsSink Sink for registry observability events.
   * @param validator Pre-flight validator applied to every candidate list, including {@code
   *     initial}.
   * @throws NullPointerException if any argument is {@code null}, or if {@code initial} contains
   *     {@code null} elements.
   * @throws PrismRulePackValidationException if {@code initial} fails the validator's contract.
   */
  public PrismRulePackRegistry(
      @NonNull List<@NonNull PrismRulePack> initial,
      @NonNull PrismRegistryMetricsSink metricsSink,
      @NonNull PrismRulePackValidator validator) {
    this.metricsSink = Objects.requireNonNull(metricsSink, "metricsSink");
    this.validator = Objects.requireNonNull(validator, "validator");
    List<PrismRulePack> copied = List.copyOf(initial);
    this.validator.validate(copied);
    this.ref = new AtomicReference<>(copied);
  }

  /**
   * Returns the current immutable snapshot of registered rule packs. Wait-free and lock-free.
   *
   * <p>Callers should typically read the snapshot exactly once at the start of a logical operation
   * (e.g. a single {@code tokenize} call) and iterate that reference for the remainder of the
   * operation, so that a concurrent {@link #replace(List)} cannot perturb the in-flight work.
   *
   * @return the current immutable rule-pack list.
   */
  public @NonNull List<@NonNull PrismRulePack> snapshot() {
    return ref.get();
  }

  /**
   * Atomically replaces the current snapshot with a defensive copy of {@code packs}, provided the
   * configured {@link PrismRulePackValidator} accepts it.
   *
   * <p>The call proceeds in three ordered steps:
   *
   * <ol>
   *   <li>Defensive copy via {@link List#copyOf(java.util.Collection)} — rejects {@code null} lists
   *       and {@code null} elements.
   *   <li>Pre-flight validation via {@link PrismRulePackValidator#validate(List)} — rejects
   *       structurally broken packs (poison pills) before they reach the active snapshot.
   *   <li>Atomic publication via {@link AtomicReference#set(Object)}.
   * </ol>
   *
   * <p>If steps 1 or 2 throw, {@link PrismRegistryMetricsSink#onRegistryError(Throwable)} is
   * invoked and the original exception is rethrown without mutating the snapshot. Readers continue
   * to observe the previous list. On success, the new list is published and {@link
   * PrismRegistryMetricsSink#onRegistrySwap()} is invoked. The publication is happens-before-
   * ordered with respect to future readers, so all fields of the new packs and detectors are
   * visible.
   *
   * @param packs The new rule-pack list. Must not be {@code null} and must not contain {@code null}
   *     elements.
   * @throws NullPointerException if {@code packs} is {@code null} or contains {@code null}
   *     elements.
   * @throws PrismRulePackValidationException if the configured validator rejects {@code packs}.
   */
  public void replace(@NonNull List<@NonNull PrismRulePack> packs) {
    List<PrismRulePack> copied;
    try {
      copied = List.copyOf(packs);
    } catch (RuntimeException failure) {
      metricsSink.onRegistryError(failure);
      throw failure;
    }
    try {
      validator.validate(copied);
    } catch (RuntimeException failure) {
      metricsSink.onRegistryError(failure);
      throw failure;
    }
    this.ref.set(copied);
    metricsSink.onRegistrySwap();
  }
}
