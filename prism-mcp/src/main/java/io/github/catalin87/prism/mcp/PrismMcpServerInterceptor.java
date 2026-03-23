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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

/**
 * Generic MCP server-side interception utility for sanitizing inbound JSON-like requests and
 * restoring outbound responses without coupling Prism to a specific MCP server framework.
 */
public final class PrismMcpServerInterceptor {

  private final PrismMcpPayloadSanitizer payloadSanitizer;
  private final String integration;

  /**
   * Creates a server-side interceptor with fail-open defaults and no custom metrics sink.
   *
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   */
  public PrismMcpServerInterceptor(
      @NonNull List<PrismRulePack> rulePacks, @NonNull PrismVault vault) {
    this(
        rulePacks,
        vault,
        PrismMcpMetricsSink.NOOP,
        false,
        PrismMcpMetricsSink.MCP_STREAMABLE_HTTP_INTEGRATION);
  }

  /**
   * Creates a server-side interceptor with explicit metrics, strict mode, and integration naming.
   *
   * @param rulePacks active Prism rule packs
   * @param vault Prism vault used for tokenization and restoration
   * @param metricsSink runtime metrics callback
   * @param strictMode whether detector failures should abort instead of failing open
   * @param integration integration tag recorded into metrics
   */
  public PrismMcpServerInterceptor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull PrismMcpMetricsSink metricsSink,
      boolean strictMode,
      @NonNull String integration) {
    this.payloadSanitizer = new PrismMcpPayloadSanitizer(rulePacks, vault, metricsSink, strictMode);
    this.integration = Objects.requireNonNull(integration, "integration");
  }

  /** Returns a recursively sanitized copy of the inbound MCP request payload. */
  public @NonNull Map<String, Object> sanitizeInboundRequest(@NonNull Map<String, Object> request) {
    return payloadSanitizer.sanitizeRequest(request, integration);
  }

  /** Returns a recursively restored copy of the outbound MCP response payload. */
  public @NonNull Map<String, Object> restoreOutboundResponse(
      @NonNull Map<String, Object> response) {
    return payloadSanitizer.restoreResponse(response, integration);
  }
}
