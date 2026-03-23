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

import org.jspecify.annotations.NonNull;

/** Parses Streamable HTTP event streams and extracts the last JSON payload event. */
final class PrismMcpEventStreamParser {

  private PrismMcpEventStreamParser() {}

  static @NonNull String extractLastJsonPayload(@NonNull String body) {
    StringBuilder currentEvent = new StringBuilder();
    String latestPayload = null;

    for (String rawLine : body.split("\\R", -1)) {
      String line = rawLine.stripTrailing();
      if (line.isEmpty()) {
        latestPayload = chooseLatest(latestPayload, currentEvent);
        currentEvent.setLength(0);
        continue;
      }
      if (line.startsWith(":")) {
        continue;
      }
      if (line.startsWith("data:")) {
        if (!currentEvent.isEmpty()) {
          currentEvent.append('\n');
        }
        currentEvent.append(line.substring("data:".length()).trim());
      }
    }

    latestPayload = chooseLatest(latestPayload, currentEvent);
    if (latestPayload == null) {
      throw new IllegalStateException("No JSON payload found in MCP SSE response");
    }
    return latestPayload;
  }

  private static String chooseLatest(String latestPayload, StringBuilder currentEvent) {
    if (currentEvent.isEmpty()) {
      return latestPayload;
    }
    String candidate = currentEvent.toString().trim();
    if (candidate.isBlank() || "[DONE]".equals(candidate)) {
      return latestPayload;
    }
    return candidate;
  }
}
