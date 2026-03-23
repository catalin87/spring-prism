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
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Strict mode blocked Prism MCP processing");
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
