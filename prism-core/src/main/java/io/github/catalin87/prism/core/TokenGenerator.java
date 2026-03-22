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

import org.jspecify.annotations.NonNull;

/** Protocol for deterministic token generators mapped exactly to EU rules. */
public interface TokenGenerator {

  /**
   * Generates a protected PrismToken record using mathematical boundaries.
   *
   * @param candidate The isolated textual entity bounding the PII mapping.
   * @param secretKey The byte payload generating the mathematical signature.
   * @return A cryptographically secure prism context.
   */
  @NonNull PrismToken generate(@NonNull PiiCandidate candidate, byte @NonNull [] secretKey);
}
