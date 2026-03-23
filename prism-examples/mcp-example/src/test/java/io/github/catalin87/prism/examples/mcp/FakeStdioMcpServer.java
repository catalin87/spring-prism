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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fake stdio MCP server used by integration tests to record sanitized payloads. */
public final class FakeStdioMcpServer {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private FakeStdioMcpServer() {}

  public static void main(String[] args) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String requestJson = reader.readLine();
      if (requestJson == null || requestJson.isBlank()) {
        return;
      }

      Files.writeString(
          Path.of(System.getenv().getOrDefault("PRISM_MCP_RECORD_PATH", "mcp-request.json")),
          requestJson,
          StandardCharsets.UTF_8);

      Map<String, Object> request = OBJECT_MAPPER.readValue(requestJson, MAP_TYPE);
      @SuppressWarnings("unchecked")
      Map<String, Object> params = (Map<String, Object>) request.get("params");
      String prompt = String.valueOf(params.get("prompt"));

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("jsonrpc", "2.0");
      result.put("id", request.get("id"));
      result.put("result", Map.of("message", "MCP reply: " + prompt));
      System.out.println(OBJECT_MAPPER.writeValueAsString(result));
    }
  }
}
