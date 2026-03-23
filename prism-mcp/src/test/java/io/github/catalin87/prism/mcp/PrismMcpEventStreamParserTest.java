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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link PrismMcpEventStreamParser}. */
class PrismMcpEventStreamParserTest {

  @Test
  void extractLastJsonPayloadIgnoresCommentsAndDoneMarkers() {
    String body =
        """
        : keep-alive
        event: message
        data: {"jsonrpc":"2.0","result":{"message":"first"}}

        data: [DONE]

        data: {"jsonrpc":"2.0","result":{"message":"final"}}

        """;

    assertThat(PrismMcpEventStreamParser.extractLastJsonPayload(body))
        .isEqualTo("{\"jsonrpc\":\"2.0\",\"result\":{\"message\":\"final\"}}");
  }

  @Test
  void extractLastJsonPayloadJoinsMultiLineDataEvents() {
    String body =
        """
        data: {"jsonrpc":"2.0",
        data: "result":{"message":"joined"}}

        """;

    assertThat(PrismMcpEventStreamParser.extractLastJsonPayload(body))
        .isEqualTo("{\"jsonrpc\":\"2.0\",\n\"result\":{\"message\":\"joined\"}}");
  }
}
