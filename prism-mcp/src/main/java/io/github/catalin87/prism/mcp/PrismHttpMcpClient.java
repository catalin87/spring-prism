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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** MCP client implementation for hosted servers over Streamable HTTP. */
public final class PrismHttpMcpClient extends AbstractPrismMcpClient {

  private final HttpClient httpClient;
  private final URI endpoint;

  /**
   * Creates a Prism-protected HTTP client targeting a Streamable HTTP MCP endpoint.
   *
   * @param baseUrl server endpoint URL
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   * @param observationRegistry Micrometer registry reserved for future observations
   * @param metricsSink runtime metrics callback
   * @param strictMode whether detector failures should abort instead of failing open
   */
  public PrismHttpMcpClient(
      @NonNull String baseUrl,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMcpMetricsSink metricsSink,
      boolean strictMode) {
    super(
        PrismMcpMetricsSink.MCP_STREAMABLE_HTTP_INTEGRATION,
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        strictMode);
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.endpoint = URI.create(baseUrl);
  }

  @Override
  protected @NonNull String execute(@NonNull String requestJson, @Nullable String requestId)
      throws IOException {
    HttpRequest request =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "MCP HTTP request failed with status " + response.statusCode());
      }
      String contentType = response.headers().firstValue("content-type").orElse("");
      return contentType.contains("text/event-stream")
          ? PrismMcpEventStreamParser.extractResponsePayload(response.body(), requestId)
          : response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for MCP HTTP response", exception);
    }
  }
}
