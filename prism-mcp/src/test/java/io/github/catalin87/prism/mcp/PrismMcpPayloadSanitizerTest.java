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
package io.github.catalin87.prism.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PrismMcpPayloadSanitizer}. */
class PrismMcpPayloadSanitizerTest {

  private static final byte[] SECRET = "mcp-payload-sanitizer-secret-1234".getBytes();

  private PrismVault vault;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
  }

  @Test
  void sanitizeAndRestoreNestedPayloads() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(List.of(new UniversalRulePack()), vault);

    Map<String, Object> sanitized =
        sanitizer.sanitizeRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "params",
                Map.of(
                    "prompt",
                    "Contact user@example.com",
                    "arguments",
                    List.of("Call +40 712 345 678", Map.of("email", "user@example.com")))),
            PrismMcpMetricsSink.MCP_STDIO_INTEGRATION);

    assertThat(String.valueOf(((Map<?, ?>) sanitized.get("params")).get("prompt")))
        .contains("<PRISM_EMAIL_")
        .doesNotContain("user@example.com");

    Map<String, Object> restored =
        sanitizer.restoreResponse(
            Map.of(
                "jsonrpc",
                "2.0",
                "result",
                Map.of(
                    "message",
                    "Handled "
                        + vault.tokenize("user@example.com", "EMAIL").key()
                        + " and "
                        + vault.tokenize("+40 712 345 678", "PHONE_NUMBER").key())),
            PrismMcpMetricsSink.MCP_STDIO_INTEGRATION);

    assertThat(String.valueOf(((Map<?, ?>) restored.get("result")).get("message")))
        .contains("user@example.com")
        .contains("+40 712 345 678");
  }

  @Test
  void failOpenLeavesPayloadUntouchedWhenDetectorFails() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(List.of(new FailingRulePack()), vault);

    Map<String, Object> sanitized =
        sanitizer.sanitizeRequest(
            Map.of("params", Map.of("prompt", "safe input")),
            PrismMcpMetricsSink.MCP_STDIO_INTEGRATION);

    assertThat(((Map<?, ?>) sanitized.get("params")).get("prompt")).isEqualTo("safe input");
  }

  @Test
  void strictModeThrowsWhenDetectorFails() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(
            List.of(new FailingRulePack()), vault, PrismMcpMetricsSink.NOOP, true);

    assertThatThrownBy(
            () ->
                sanitizer.sanitizeRequest(
                    Map.of("params", Map.of("prompt", "safe input")),
                    PrismMcpMetricsSink.MCP_STDIO_INTEGRATION))
        .isInstanceOf(PrismProtectionException.class)
        .hasMessageContaining("Request blocked")
        .extracting(
            failure -> ((PrismProtectionException) failure).phase(),
            failure -> ((PrismProtectionException) failure).reason(),
            failure -> ((PrismProtectionException) failure).failureMode())
        .containsExactly(
            PrismProtectionPhase.DETECT,
            PrismProtectionReason.DETECTOR_FAILURE,
            PrismFailureMode.FAIL_CLOSED);
  }

  @Test
  void sanitizeJsonEncodedToolArguments() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(List.of(new UniversalRulePack()), vault);

    Map<String, Object> sanitized =
        sanitizer.sanitizeRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "method",
                "tools/call",
                "params",
                Map.of(
                    "name",
                    "lookupCustomer",
                    "arguments",
                    """
                    {"email":"user@example.com","nested":{"phone":"+40 712 345 678"}}
                    """)),
            PrismMcpMetricsSink.MCP_STDIO_INTEGRATION);

    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) sanitized.get("params");
    String arguments = String.valueOf(params.get("arguments"));

    assertThat(arguments).contains("<PRISM_EMAIL_").contains("<PRISM_PHONE_NUMBER_");
    assertThat(arguments).doesNotContain("user@example.com").doesNotContain("+40 712 345 678");
    assertThat(params.get("name")).isEqualTo("lookupCustomer");
    assertThat(sanitized.get("method")).isEqualTo("tools/call");
  }

  @Test
  void sanitizeMessageContentWithoutTouchingProtocolFields() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(List.of(new UniversalRulePack()), vault);

    Map<String, Object> sanitized =
        sanitizer.sanitizeRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "method",
                "sampling/createMessage",
                "params",
                Map.of(
                    "model",
                    "gpt-5.4",
                    "messages",
                    List.of(
                        Map.of(
                            "role",
                            "user",
                            "content",
                            List.of(
                                Map.of("type", "text", "text", "Email user@example.com"),
                                Map.of("type", "text", "text", "Call +40 712 345 678")))))),
            PrismMcpMetricsSink.MCP_STREAMABLE_HTTP_INTEGRATION);

    assertThat(String.valueOf(sanitized))
        .contains("<PRISM_EMAIL_")
        .contains("<PRISM_PHONE_NUMBER_")
        .contains("sampling/createMessage")
        .contains("role=user")
        .contains("type=text")
        .doesNotContain("user@example.com")
        .doesNotContain("+40 712 345 678");
  }

  @Test
  void restoreStructuredContentAndTextBlocks() {
    PrismMcpPayloadSanitizer sanitizer =
        new PrismMcpPayloadSanitizer(List.of(new UniversalRulePack()), vault);

    String emailToken = vault.tokenize("user@example.com", "EMAIL").key();
    String phoneToken = vault.tokenize("+40 712 345 678", "PHONE_NUMBER").key();

    Map<String, Object> restored =
        sanitizer.restoreResponse(
            Map.of(
                "jsonrpc",
                "2.0",
                "result",
                Map.of(
                    "content",
                    List.of(Map.of("type", "text", "text", "Handled " + emailToken)),
                    "structuredContent",
                    "{\"phone\":\"" + phoneToken + "\"}")),
            PrismMcpMetricsSink.MCP_STDIO_INTEGRATION);

    assertThat(String.valueOf(restored))
        .contains("user@example.com")
        .contains("+40 712 345 678")
        .doesNotContain("<PRISM_EMAIL_")
        .doesNotContain("<PRISM_PHONE_NUMBER_");
  }

  private static final class FailingRulePack implements PrismRulePack {
    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return List.of(new FailingDetector());
    }

    @Override
    public @NonNull String getName() {
      return "FAILING";
    }
  }

  private static final class FailingDetector implements PiiDetector {
    @Override
    public @NonNull String getEntityType() {
      return "FAIL";
    }

    @Override
    public @NonNull List<PiiCandidate> detect(@NonNull String text) {
      throw new IllegalStateException("boom");
    }
  }
}
