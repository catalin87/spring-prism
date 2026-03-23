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
package io.github.catalin87.prism.examples.demo;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Local mock MCP endpoint used by the unified demo application. */
@RestController
@RequestMapping("/demo-lab/api/mock-mcp")
class DemoMockMcpController {

  private final McpMockRecorder mcpMockRecorder;

  DemoMockMcpController(McpMockRecorder mcpMockRecorder) {
    this.mcpMockRecorder = mcpMockRecorder;
  }

  @PostMapping
  Map<String, Object> exchange(@RequestBody Map<String, Object> request) {
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) request.get("params");
    String prompt = String.valueOf(params.get("prompt"));

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", request.get("id"));
    response.put(
        "result",
        Map.of(
            "message",
            DemoExperienceService.MOCK_PREFIX + prompt + DemoExperienceService.MOCK_SUFFIX));

    mcpMockRecorder.record(String.valueOf(request.get("id")), request, response);
    return response;
  }
}
