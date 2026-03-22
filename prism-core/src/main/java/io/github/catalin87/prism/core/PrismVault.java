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
import org.jspecify.annotations.Nullable;

/**
 * Interface defining the storage and retrieval engine for pseudonymized PII mapping sets. Default
 * implementation enforces a rigorous Time-To-Live (TTL) eviction policy.
 */
public interface PrismVault {

  /**
   * Ingests a raw value and generates a pseudonymized token containing an HMAC-SHA256 signature.
   *
   * @param value The raw PII string.
   * @param label The semantic entity label representing the PII type.
   * @return The secure token representing this value.
   */
  @NonNull PrismToken tokenize(@NonNull String value, @NonNull String label);

  /**
   * Retrieves the original PII value utilizing its un-guessable token metadata.
   *
   * @param token The Token metadata intercepted from the LLM response.
   * @return The raw PII value, or null if TTL expired or signature invalid.
   */
  @Nullable String detokenize(@NonNull PrismToken token);
}
