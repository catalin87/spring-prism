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

import java.util.Map;
import org.jspecify.annotations.NonNull;

/** Prism-protected MCP client contract for structured JSON-RPC style exchanges. */
public interface PrismMcpClient {

  /**
   * Sanitizes the outbound MCP payload, delegates it to the configured transport, and restores any
   * Prism tokens found in the returned result payload.
   *
   * @param request outbound JSON-like request payload
   * @return sanitized and restored response payload
   */
  @NonNull Map<String, Object> exchange(@NonNull Map<String, Object> request);
}
