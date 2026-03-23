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
package io.github.catalin87.prism.examples.mcp;

import io.github.catalin87.prism.mcp.PrismMcpClient;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entrypoint for the MCP example. */
@RestController
@RequestMapping("/demo/mcp")
class McpExampleController {

  private final ObjectProvider<PrismMcpClient> prismMcpClientProvider;

  McpExampleController(ObjectProvider<PrismMcpClient> prismMcpClientProvider) {
    this.prismMcpClientProvider = prismMcpClientProvider;
  }

  @GetMapping
  String demo(@RequestParam("email") String email) {
    PrismMcpClient prismMcpClient = prismMcpClientProvider.getIfAvailable();
    if (prismMcpClient == null) {
      return "Prism MCP client is not configured";
    }
    Map<String, Object> response =
        prismMcpClient.exchange(
            Map.of(
                "jsonrpc",
                "2.0",
                "id",
                "demo-1",
                "method",
                "tools/call",
                "params",
                Map.of("prompt", "Please contact " + email)));
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) response.get("result");
    return String.valueOf(result.get("message"));
  }
}
