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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Stores temporary request and response traces for the local MCP mock endpoint. */
@Component
final class McpMockRecorder {

  private final ConcurrentHashMap<String, McpMockTrace> traces = new ConcurrentHashMap<>();

  void record(
      String requestId, Map<String, Object> requestPayload, Map<String, Object> responsePayload) {
    traces.put(
        requestId, new McpMockTrace(Map.copyOf(requestPayload), Map.copyOf(responsePayload)));
  }

  McpMockTrace get(String requestId) {
    McpMockTrace trace = traces.remove(requestId);
    if (trace == null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Missing MCP trace for " + requestId);
    }
    return trace;
  }
}
