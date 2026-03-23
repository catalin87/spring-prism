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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/** Shared base implementation for Prism-protected MCP client transports. */
abstract class AbstractPrismMcpClient implements PrismMcpClient {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PrismMcpPayloadSanitizer payloadSanitizer;
  private final String integration;

  AbstractPrismMcpClient(
      @NonNull String integration,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMcpMetricsSink metricsSink,
      boolean strictMode) {
    this.integration = integration;
    this.payloadSanitizer = new PrismMcpPayloadSanitizer(rulePacks, vault, metricsSink, strictMode);
  }

  @Override
  public final @NonNull Map<String, Object> exchange(@NonNull Map<String, Object> request) {
    try {
      Map<String, Object> sanitizedRequest = payloadSanitizer.sanitizeRequest(request, integration);
      String rawResponse = execute(objectMapper.writeValueAsString(sanitizedRequest));
      Map<String, Object> response = parseResponse(rawResponse);
      return payloadSanitizer.restoreResponse(response, integration);
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to process MCP payload", exception);
    }
  }

  protected abstract @NonNull String execute(@NonNull String requestJson) throws IOException;

  protected final @NonNull String writeJson(@NonNull Map<String, Object> payload)
      throws IOException {
    return objectMapper.writeValueAsString(payload);
  }

  protected final @NonNull Map<String, Object> parseResponse(@NonNull String rawResponse)
      throws IOException {
    return objectMapper.readValue(rawResponse, MAP_TYPE);
  }
}
