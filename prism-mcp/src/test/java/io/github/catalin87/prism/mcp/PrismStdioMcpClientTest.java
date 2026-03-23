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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Transport tests for {@link PrismStdioMcpClient}. */
class PrismStdioMcpClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final byte[] SECRET = "mcp-stdio-client-secret-material".getBytes();

  private PrismVault vault;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
  }

  @Test
  void exchangeSanitizesStdioPayloadAndRestoresResponseTokens() throws IOException {
    Path recordPath = Files.createTempFile("prism-mcp-stdio", ".json");
    PrismStdioMcpClient client =
        new PrismStdioMcpClient(
            javaCommand(),
            List.of("-cp", System.getProperty("java.class.path"), FakeStdioServer.class.getName()),
            Map.of("PRISM_MCP_RECORD_PATH", recordPath.toString()),
            null,
            List.of(new UniversalRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMcpMetricsSink.NOOP,
            false);

    Map<String, Object> response =
        client.exchange(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                "1",
                "params",
                Map.of("prompt", "Please contact user@example.com")));

    String recordedRequest = Files.readString(recordPath);
    assertThat(recordedRequest).contains("<PRISM_EMAIL_").doesNotContain("user@example.com");
    assertThat(String.valueOf(((Map<?, ?>) response.get("result")).get("message")))
        .contains("user@example.com");
  }

  private static String javaCommand() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  /** Test helper subprocess acting as a minimal stdio MCP server. */
  public static final class FakeStdioServer {
    private FakeStdioServer() {}

    public static void main(String[] args) throws IOException {
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
        String requestJson = reader.readLine();
        if (requestJson == null) {
          return;
        }
        Files.writeString(
            Path.of(System.getenv("PRISM_MCP_RECORD_PATH")), requestJson, StandardCharsets.UTF_8);
        @SuppressWarnings("unchecked")
        Map<String, Object> request = OBJECT_MAPPER.readValue(requestJson, MAP_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String prompt = String.valueOf(params.get("prompt"));
        System.out.println(
            OBJECT_MAPPER.writeValueAsString(
                Map.of(
                    "jsonrpc",
                    "2.0",
                    "id",
                    request.get("id"),
                    "result",
                    Map.of("message", "Ack " + prompt))));
      }
    }
  }
}
