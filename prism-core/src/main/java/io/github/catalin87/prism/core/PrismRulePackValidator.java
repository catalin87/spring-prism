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
import org.jspecify.annotations.NonNull;

/**
 * Pre-flight check applied by {@link PrismRulePackRegistry#replace(List)} before the atomic swap
 * publishes a new rule-pack list. A validator inspects the candidate ruleset for structural defects
 * (null detectors, packs whose {@code getDetectors()} throws, missing entity types, etc.) and
 * rejects the update by throwing a {@link PrismRulePackValidationException} <em>before</em> the
 * registry mutates its snapshot.
 *
 * <p>Validators preserve atomicity: either every pack in the candidate list is accepted and
 * published together, or none of them is and the previous snapshot remains the active ruleset.
 * Validators run on the writer thread, exactly once per {@code replace(...)} call, and must be
 * thread-safe. They must not have side effects on the candidate packs.
 *
 * <p>The core module ships two stock implementations:
 *
 * <ul>
 *   <li>{@link #NOOP} — accepts every candidate. Use only when the caller has already validated the
 *       packs.
 *   <li>{@link #STRUCTURAL} — rejects null packs, packs with a null name, packs whose {@code
 *       getDetectors()} throws or returns null, null detector elements, and detectors with a null
 *       or blank {@code getEntityType()}. This is the default for callers that do not supply their
 *       own validator.
 * </ul>
 *
 * <p>Hosts can plug in a domain-specific validator (e.g. one that compiles regex patterns or
 * enforces naming conventions) by implementing this interface and supplying it to the {@linkplain
 * PrismRulePackRegistry#PrismRulePackRegistry(List, PrismRegistryMetricsSink,
 * PrismRulePackValidator) three-arg constructor}. Heavyweight checks belong here rather than inside
 * the registry itself, keeping the swap path free of policy decisions.
 *
 * @since 1.3.0
 */
@FunctionalInterface
public interface PrismRulePackValidator {

  /** Permissive validator that accepts every candidate ruleset. */
  PrismRulePackValidator NOOP = packs -> {};

  /**
   * Structural validator used when no custom validator is supplied. Performs only cheap,
   * allocation- free checks: no regex compilation, no I/O, no detector invocation against sample
   * text.
   */
  PrismRulePackValidator STRUCTURAL = new StructuralValidator();

  /**
   * Inspects {@code packs} and throws {@link PrismRulePackValidationException} if any pack is
   * structurally invalid. The list itself is guaranteed non-null and contains no null elements when
   * invoked from {@link PrismRulePackRegistry#replace(List)} (those checks happen earlier, during
   * {@link List#copyOf(java.util.Collection)}).
   *
   * @param packs The candidate rule-pack list; never {@code null} and never contains {@code null}
   *     elements.
   * @throws PrismRulePackValidationException if any pack fails the structural contract.
   */
  void validate(@NonNull List<@NonNull PrismRulePack> packs)
      throws PrismRulePackValidationException;

  /**
   * Default structural validator. Rejects packs that would cause downstream scanners to throw at
   * load time (e.g. a {@code getDetectors()} call that itself raises an exception — a classic
   * "poison pill" pattern). Detector-time exceptions thrown from {@code detect(...)} are handled
   * separately by the scanner's per-detector fail-safe wrapper and are intentionally not validated
   * here.
   */
  final class StructuralValidator implements PrismRulePackValidator {

    @Override
    public void validate(@NonNull List<@NonNull PrismRulePack> packs)
        throws PrismRulePackValidationException {
      Objects.requireNonNull(packs, "packs");
      for (int packIndex = 0; packIndex < packs.size(); packIndex++) {
        PrismRulePack pack = packs.get(packIndex);
        if (pack == null) {
          throw new PrismRulePackValidationException(
              "rule pack at index " + packIndex + " is null");
        }
        String name;
        try {
          name = pack.getName();
        } catch (RuntimeException cause) {
          throw new PrismRulePackValidationException(
              "rule pack at index " + packIndex + " threw from getName()", cause);
        }
        if (name == null || name.isBlank()) {
          throw new PrismRulePackValidationException(
              "rule pack at index " + packIndex + " has a null or blank name");
        }
        List<@NonNull PiiDetector> detectors;
        try {
          detectors = pack.getDetectors();
        } catch (RuntimeException cause) {
          throw new PrismRulePackValidationException(
              "rule pack '" + name + "' threw from getDetectors()", cause);
        }
        if (detectors == null) {
          throw new PrismRulePackValidationException(
              "rule pack '" + name + "' returned a null detector list");
        }
        for (int detectorIndex = 0; detectorIndex < detectors.size(); detectorIndex++) {
          PiiDetector detector = detectors.get(detectorIndex);
          if (detector == null) {
            throw new PrismRulePackValidationException(
                "rule pack '" + name + "' has a null detector at index " + detectorIndex);
          }
          String entityType;
          try {
            entityType = detector.getEntityType();
          } catch (RuntimeException cause) {
            throw new PrismRulePackValidationException(
                "detector "
                    + detector.getClass().getName()
                    + " in rule pack '"
                    + name
                    + "' threw from getEntityType()",
                cause);
          }
          if (entityType == null || entityType.isBlank()) {
            throw new PrismRulePackValidationException(
                "detector "
                    + detector.getClass().getName()
                    + " in rule pack '"
                    + name
                    + "' has a null or blank entity type");
          }
        }
      }
    }
  }
}
