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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Parses Streamable HTTP event streams and extracts the most relevant JSON payload event. */
final class PrismMcpEventStreamParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private PrismMcpEventStreamParser() {}

  static @NonNull String extractResponsePayload(
      @NonNull String body, @Nullable String expectedRequestId) {
    StringBuilder currentEventData = new StringBuilder();
    String currentEventType = "";
    Candidate bestCandidate = null;

    for (String rawLine : body.split("\\R", -1)) {
      String line = rawLine.stripTrailing();
      if (line.isEmpty()) {
        bestCandidate =
            chooseBestCandidate(
                bestCandidate, currentEventType, currentEventData, expectedRequestId);
        currentEventData.setLength(0);
        currentEventType = "";
        continue;
      }
      if (line.startsWith(":")) {
        continue;
      }
      if (line.startsWith("event:")) {
        currentEventType = line.substring("event:".length()).trim();
        continue;
      }
      if (line.startsWith("data:")) {
        if (!currentEventData.isEmpty()) {
          currentEventData.append('\n');
        }
        String dataChunk = line.substring("data:".length());
        currentEventData.append(stripSingleLeadingSpace(dataChunk));
      }
    }

    bestCandidate =
        chooseBestCandidate(bestCandidate, currentEventType, currentEventData, expectedRequestId);
    if (bestCandidate == null) {
      throw new IllegalStateException("No JSON payload found in MCP SSE response");
    }
    return bestCandidate.payload();
  }

  private static @NonNull String stripSingleLeadingSpace(@NonNull String value) {
    return value.startsWith(" ") ? value.substring(1) : value;
  }

  private static @Nullable Candidate chooseBestCandidate(
      @Nullable Candidate bestCandidate,
      @NonNull String eventType,
      @NonNull StringBuilder currentEventData,
      @Nullable String expectedRequestId) {
    Candidate candidate = toCandidate(eventType, currentEventData, expectedRequestId);
    if (candidate == null) {
      return bestCandidate;
    }
    if (bestCandidate == null || candidate.score() >= bestCandidate.score()) {
      return candidate;
    }
    return bestCandidate;
  }

  private static @Nullable Candidate toCandidate(
      @NonNull String eventType,
      @NonNull StringBuilder currentEventData,
      @Nullable String expectedRequestId) {
    if (currentEventData.isEmpty()) {
      return null;
    }
    String payload = currentEventData.toString().trim();
    if (payload.isBlank() || "[DONE]".equals(payload)) {
      return null;
    }
    try {
      JsonNode node = OBJECT_MAPPER.readTree(payload);
      return new Candidate(payload, score(eventType, node, expectedRequestId));
    } catch (IOException exception) {
      return null;
    }
  }

  private static int score(
      @NonNull String eventType, @NonNull JsonNode node, @Nullable String expectedRequestId) {
    int score = 0;
    if ("response".equals(eventType)) {
      score += 20;
    } else if ("message".equals(eventType) || eventType.isBlank()) {
      score += 5;
    } else if ("ping".equals(eventType)) {
      score -= 20;
    }

    if (node.has("jsonrpc")) {
      score += 25;
    }
    if (node.has("result") || node.has("error")) {
      score += 30;
    }
    if (node.has("id")) {
      score += 10;
      if (expectedRequestId != null && expectedRequestId.equals(node.path("id").asText())) {
        score += 40;
      }
    }
    return score;
  }

  private record Candidate(@NonNull String payload, int score) {}
}
