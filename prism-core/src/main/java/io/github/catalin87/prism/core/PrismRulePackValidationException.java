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
 * Thrown by a {@link PrismRulePackValidator} when a candidate rule-pack list fails pre-flight
 * validation. Propagates out of {@link PrismRulePackRegistry#replace(java.util.List)} unchanged so
 * callers can distinguish "the list itself is malformed" (rejected before publication) from a
 * runtime failure during detection.
 *
 * @since 1.3.0
 */
public final class PrismRulePackValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PrismRulePackValidationException(@NonNull String message) {
    super(message);
  }

  public PrismRulePackValidationException(@NonNull String message, @Nullable Throwable cause) {
    super(message, cause);
  }
}
