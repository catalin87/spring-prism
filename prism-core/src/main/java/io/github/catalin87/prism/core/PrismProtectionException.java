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

import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Runtime exception raised when Prism blocks processing to preserve privacy guarantees. */
public final class PrismProtectionException extends RuntimeException {

  private final PrismProtectionPhase phase;
  private final PrismProtectionReason reason;
  private final String integration;
  private final PrismFailureMode failureMode;

  /**
   * Creates a new protection exception without an underlying cause.
   *
   * @param phase the processing phase that triggered the block
   * @param reason the stable machine-readable reason code
   * @param integration the integration name that raised the block
   * @param failureMode the active failure mode
   * @param message the public-safe message to expose to callers
   */
  public PrismProtectionException(
      @NonNull PrismProtectionPhase phase,
      @NonNull PrismProtectionReason reason,
      @NonNull String integration,
      @NonNull PrismFailureMode failureMode,
      @NonNull String message) {
    super(message);
    this.phase = Objects.requireNonNull(phase, "phase");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.integration = Objects.requireNonNull(integration, "integration");
    this.failureMode = Objects.requireNonNull(failureMode, "failureMode");
  }

  /**
   * Creates a new protection exception with an underlying cause.
   *
   * @param phase the processing phase that triggered the block
   * @param reason the stable machine-readable reason code
   * @param integration the integration name that raised the block
   * @param failureMode the active failure mode
   * @param message the public-safe message to expose to callers
   * @param cause the underlying cause that Prism caught internally
   */
  public PrismProtectionException(
      @NonNull PrismProtectionPhase phase,
      @NonNull PrismProtectionReason reason,
      @NonNull String integration,
      @NonNull PrismFailureMode failureMode,
      @NonNull String message,
      @Nullable Throwable cause) {
    super(message, cause);
    this.phase = Objects.requireNonNull(phase, "phase");
    this.reason = Objects.requireNonNull(reason, "reason");
    this.integration = Objects.requireNonNull(integration, "integration");
    this.failureMode = Objects.requireNonNull(failureMode, "failureMode");
  }

  public @NonNull PrismProtectionPhase phase() {
    return phase;
  }

  public @NonNull PrismProtectionReason reason() {
    return reason;
  }

  public @NonNull String integration() {
    return integration;
  }

  public @NonNull PrismFailureMode failureMode() {
    return failureMode;
  }
}
