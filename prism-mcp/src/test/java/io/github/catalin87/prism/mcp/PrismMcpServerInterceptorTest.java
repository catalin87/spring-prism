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

import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PrismMcpServerInterceptor}. */
class PrismMcpServerInterceptorTest {

  private static final byte[] SECRET = "mcp-server-interceptor-secret-12".getBytes();

  private PrismVault vault;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
  }

  @Test
  void sanitizeInboundAndRestoreOutboundPayloads() {
    PrismMcpServerInterceptor interceptor =
        new PrismMcpServerInterceptor(List.of(new UniversalRulePack()), vault);

    Map<String, Object> sanitized =
        interceptor.sanitizeInboundRequest(
            Map.of(
                "jsonrpc",
                "2.0",
                "method",
                "tools/call",
                "params",
                Map.of(
                    "arguments",
                    Map.of("email", "user@example.com", "message", "Call +40 712 345 678"))));

    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) sanitized.get("params");
    assertThat(String.valueOf(params))
        .contains("<PRISM_EMAIL_")
        .contains("<PRISM_PHONE_NUMBER_")
        .doesNotContain("user@example.com")
        .doesNotContain("+40 712 345 678");

    Map<String, Object> restored =
        interceptor.restoreOutboundResponse(
            Map.of(
                "jsonrpc",
                "2.0",
                "result",
                Map.of(
                    "content",
                    List.of(
                        Map.of(
                            "type",
                            "text",
                            "text",
                            "Handled " + vault.tokenize("user@example.com", "EMAIL").key())))));

    assertThat(String.valueOf(restored))
        .contains("user@example.com")
        .doesNotContain("<PRISM_EMAIL_");
  }
}
