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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PrismProtectionContractsTest {

  @Test
  void failureModeEnumExposesSupportedValues() {
    assertEquals(PrismFailureMode.FAIL_SAFE, PrismFailureMode.valueOf("FAIL_SAFE"));
    assertEquals(PrismFailureMode.FAIL_CLOSED, PrismFailureMode.valueOf("FAIL_CLOSED"));
  }

  @Test
  void protectionPhaseEnumExposesSupportedValues() {
    assertEquals(PrismProtectionPhase.DETECT, PrismProtectionPhase.valueOf("DETECT"));
    assertEquals(PrismProtectionPhase.TOKENIZE, PrismProtectionPhase.valueOf("TOKENIZE"));
    assertEquals(PrismProtectionPhase.DETOKENIZE, PrismProtectionPhase.valueOf("DETOKENIZE"));
    assertEquals(PrismProtectionPhase.RESTORE, PrismProtectionPhase.valueOf("RESTORE"));
    assertEquals(
        PrismProtectionPhase.STRUCTURED_PAYLOAD,
        PrismProtectionPhase.valueOf("STRUCTURED_PAYLOAD"));
    assertEquals(PrismProtectionPhase.PREFLIGHT, PrismProtectionPhase.valueOf("PREFLIGHT"));
  }

  @Test
  void protectionReasonEnumExposesSupportedValues() {
    assertEquals(
        PrismProtectionReason.VAULT_UNAVAILABLE,
        PrismProtectionReason.valueOf("VAULT_UNAVAILABLE"));
    assertEquals(PrismProtectionReason.TIMEOUT, PrismProtectionReason.valueOf("TIMEOUT"));
    assertEquals(
        PrismProtectionReason.DETECTOR_FAILURE, PrismProtectionReason.valueOf("DETECTOR_FAILURE"));
    assertEquals(
        PrismProtectionReason.STRUCTURED_PARSE_FAILURE,
        PrismProtectionReason.valueOf("STRUCTURED_PARSE_FAILURE"));
    assertEquals(
        PrismProtectionReason.RESTORE_FAILURE, PrismProtectionReason.valueOf("RESTORE_FAILURE"));
    assertEquals(PrismProtectionReason.POLICY_BLOCK, PrismProtectionReason.valueOf("POLICY_BLOCK"));
  }

  @Test
  void protectionExceptionCapturesContractWithoutCause() {
    PrismProtectionException exception =
        new PrismProtectionException(
            PrismProtectionPhase.TOKENIZE,
            PrismProtectionReason.VAULT_UNAVAILABLE,
            "spring-ai",
            PrismFailureMode.FAIL_CLOSED,
            "Request blocked: Privacy constraints could not be enforced.");

    assertEquals(PrismProtectionPhase.TOKENIZE, exception.phase());
    assertEquals(PrismProtectionReason.VAULT_UNAVAILABLE, exception.reason());
    assertEquals("spring-ai", exception.integration());
    assertEquals(PrismFailureMode.FAIL_CLOSED, exception.failureMode());
    assertEquals(
        "Request blocked: Privacy constraints could not be enforced.", exception.getMessage());
    assertNull(exception.getCause());
    assertInstanceOf(RuntimeException.class, exception);
  }

  @Test
  void protectionExceptionCapturesContractWithCause() {
    IllegalStateException cause = new IllegalStateException("Redis unavailable");

    PrismProtectionException exception =
        new PrismProtectionException(
            PrismProtectionPhase.PREFLIGHT,
            PrismProtectionReason.TIMEOUT,
            "mcp",
            PrismFailureMode.FAIL_CLOSED,
            "Request blocked: Privacy constraints could not be enforced.",
            cause);

    assertEquals(PrismProtectionPhase.PREFLIGHT, exception.phase());
    assertEquals(PrismProtectionReason.TIMEOUT, exception.reason());
    assertEquals("mcp", exception.integration());
    assertEquals(PrismFailureMode.FAIL_CLOSED, exception.failureMode());
    assertSame(cause, exception.getCause());
  }

  @Test
  void protectionExceptionRejectsNullContractFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PrismProtectionException(
                null,
                PrismProtectionReason.POLICY_BLOCK,
                "spring-ai",
                PrismFailureMode.FAIL_CLOSED,
                "blocked"));
    assertThrows(
        NullPointerException.class,
        () ->
            new PrismProtectionException(
                PrismProtectionPhase.DETECT,
                null,
                "spring-ai",
                PrismFailureMode.FAIL_CLOSED,
                "blocked"));
    assertThrows(
        NullPointerException.class,
        () ->
            new PrismProtectionException(
                PrismProtectionPhase.DETECT,
                PrismProtectionReason.POLICY_BLOCK,
                null,
                PrismFailureMode.FAIL_CLOSED,
                "blocked"));
    assertThrows(
        NullPointerException.class,
        () ->
            new PrismProtectionException(
                PrismProtectionPhase.DETECT,
                PrismProtectionReason.POLICY_BLOCK,
                "spring-ai",
                null,
                "blocked"));
    assertThrows(
        NullPointerException.class,
        () ->
            new PrismProtectionException(
                PrismProtectionPhase.DETECT,
                PrismProtectionReason.POLICY_BLOCK,
                "spring-ai",
                null,
                "blocked"));
  }

  @Test
  void vaultAvailabilityContractAcceptsExplicitTimeouts() {
    AtomicReference<Duration> observedTimeout = new AtomicReference<>();
    PrismVaultAvailability availability = observedTimeout::set;

    availability.verifyAvailability(Duration.ofMillis(50));

    assertEquals(Duration.ofMillis(50), observedTimeout.get());
  }

  @Test
  void failureModeSemanticOrderingRemainsStable() {
    PrismFailureMode[] values = PrismFailureMode.values();

    assertEquals(2, values.length);
    assertEquals(PrismFailureMode.FAIL_SAFE, values[0]);
    assertEquals(PrismFailureMode.FAIL_CLOSED, values[1]);
    assertTrue(values[0].ordinal() < values[1].ordinal());
  }
}
