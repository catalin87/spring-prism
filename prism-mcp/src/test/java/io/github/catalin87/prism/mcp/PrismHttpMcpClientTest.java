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
import com.sun.net.httpserver.HttpServer;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Transport tests for {@link PrismHttpMcpClient}. */
class PrismHttpMcpClientTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final byte[] SECRET = "mcp-http-client-secret-material!".getBytes();

  private PrismVault vault;
  private HttpServer server;

  @BeforeEach
  void setUp() throws IOException {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
    server = HttpServer.create(new InetSocketAddress(0), 0);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void exchangeSanitizesHttpPayloadAndRestoresResponseTokens() throws IOException {
    AtomicReference<String> recordedRequest = new AtomicReference<>();
    server.createContext(
        "/mcp",
        exchange -> {
          String requestJson =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          recordedRequest.set(requestJson);
          @SuppressWarnings("unchecked")
          Map<String, Object> request = OBJECT_MAPPER.readValue(requestJson, MAP_TYPE);
          @SuppressWarnings("unchecked")
          Map<String, Object> params = (Map<String, Object>) request.get("params");
          String prompt = String.valueOf(params.get("prompt"));
          String body =
              OBJECT_MAPPER.writeValueAsString(
                  Map.of(
                      "jsonrpc",
                      "2.0",
                      "id",
                      request.get("id"),
                      "result",
                      Map.of("message", prompt)));
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes(StandardCharsets.UTF_8).length);
          exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
          exchange.close();
        });
    server.start();

    PrismHttpMcpClient client =
        new PrismHttpMcpClient(
            "http://localhost:" + server.getAddress().getPort() + "/mcp",
            java.util.List.of(new UniversalRulePack()),
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

    assertThat(recordedRequest.get()).contains("<PRISM_EMAIL_").doesNotContain("user@example.com");
    assertThat(String.valueOf(((Map<?, ?>) response.get("result")).get("message")))
        .contains("user@example.com");
  }
}
