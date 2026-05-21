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
import org.jspecify.annotations.NonNull;

/**
 * Pre-flight validator for {@link PrismRulePackRegistry#update(List)} swaps. Lets callers reject a
 * structurally broken or otherwise unsafe ruleset <em>before</em> it replaces the live one, so the
 * registry's atomicity guarantee ("the whole new ruleset succeeds, or none of it changes") extends
 * to semantic checks the registry itself does not understand.
 *
 * <p>The default implementation ({@link #DEFAULT}) enforces only structural invariants the registry
 * cannot infer from generics alone: every pack is non-null and yields a non-blank name, every
 * detector is non-null and yields a non-blank entity type. Domain-specific checks (regex sanity,
 * detector instantiation cost, locale coverage) belong in a custom validator wired by the
 * application — {@code prism-core} stays free of dependencies on those concerns.
 *
 * <p>Validators must throw {@link IllegalArgumentException} (or a subclass) to reject a swap. Any
 * other runtime exception still aborts the swap, but the {@code IllegalArgumentException} contract
 * lets sinks and operators distinguish "input rejected" from "validator itself blew up".
 *
 * @since 1.2.0
 */
@FunctionalInterface
public interface PrismRulePackValidator {

  /**
   * Validates a candidate ruleset. Implementations must return normally on success and throw {@link
   * IllegalArgumentException} on rejection.
   *
   * @param candidatePacks The defensively-copied list the registry is about to publish. Never
   *     {@code null} and never contains {@code null} elements (the registry has already filtered
   *     those out via {@link List#copyOf(java.util.Collection)}).
   * @throws IllegalArgumentException When the candidate ruleset is structurally or semantically
   *     invalid.
   */
  void validate(@NonNull List<@NonNull PrismRulePack> candidatePacks);

  /**
   * No-op validator. Use this when the application has already validated the ruleset upstream or
   * wants the pre-1.2 behavior (only {@link List#copyOf(java.util.Collection)} null-rejection).
   */
  PrismRulePackValidator NOOP = candidatePacks -> {};

  /**
   * Structural validator wired by {@link PrismRulePackRegistry} when no explicit validator is
   * supplied. Verifies that:
   *
   * <ul>
   *   <li>Every pack yields a non-blank {@link PrismRulePack#getName()}.
   *   <li>Every pack's detector list is non-null and contains no {@code null} entries.
   *   <li>Every detector yields a non-blank {@link PiiDetector#getEntityType()}.
   * </ul>
   *
   * <p>Behavioral failures (a detector that throws inside {@link PiiDetector#detect(String)}) are
   * out of scope here — those are caught at scan time by the scanner's per-detector fail-safe and
   * reported via the metrics sink. Pre-flight validation only ensures the ruleset can be safely
   * iterated.
   */
  PrismRulePackValidator DEFAULT =
      candidatePacks -> {
        for (int packIndex = 0; packIndex < candidatePacks.size(); packIndex++) {
          PrismRulePack pack = candidatePacks.get(packIndex);
          String packName;
          try {
            packName = pack.getName();
          } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                "PrismRulePack at index " + packIndex + " threw from getName()", error);
          }
          if (packName == null || packName.isBlank()) {
            throw new IllegalArgumentException(
                "PrismRulePack at index " + packIndex + " has a null or blank name");
          }
          List<@NonNull PiiDetector> detectors;
          try {
            detectors = pack.getDetectors();
          } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                "PrismRulePack '" + packName + "' threw from getDetectors()", error);
          }
          if (detectors == null) {
            throw new IllegalArgumentException(
                "PrismRulePack '" + packName + "' returned a null detector list");
          }
          for (int detectorIndex = 0; detectorIndex < detectors.size(); detectorIndex++) {
            PiiDetector detector = detectors.get(detectorIndex);
            if (detector == null) {
              throw new IllegalArgumentException(
                  "PrismRulePack '"
                      + packName
                      + "' contains a null detector at index "
                      + detectorIndex);
            }
            String entityType;
            try {
              entityType = detector.getEntityType();
            } catch (RuntimeException error) {
              throw new IllegalArgumentException(
                  "Detector at index "
                      + detectorIndex
                      + " in pack '"
                      + packName
                      + "' threw from getEntityType()",
                  error);
            }
            if (entityType == null || entityType.isBlank()) {
              throw new IllegalArgumentException(
                  "Detector at index "
                      + detectorIndex
                      + " in pack '"
                      + packName
                      + "' has a null or blank entityType");
            }
          }
        }
      };
}
