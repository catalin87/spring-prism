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
 * Interface defining a logical grouping of localized or contextual {@link PiiDetector}s. Following
 * the Spring Prism "EU-First" architecture, packs can be toggled by the active locale context.
 */
public interface PrismRulePack {

  /**
   * Identifies all active sequence finding logic components bound to this specific core RulePack.
   *
   * @return A list of actively registered detectors provided by this rule pack.
   */
  @NonNull List<PiiDetector> getDetectors();

  /**
   * Evaluates the standard geographical or contextual identifier of this matrix node.
   *
   * @return the locale identifier or logical grouping name (e.g. "EN", "EU", "UNIVERSAL").
   */
  @NonNull String getName();
}
