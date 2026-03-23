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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Integration tests for the MCP example app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpExampleApplicationTest {

  private static final Path RECORD_PATH = createRecordPath();

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void configureMcp(DynamicPropertyRegistry registry) {
    registry.add("spring.prism.mcp.enabled", () -> "true");
    registry.add("spring.prism.mcp.transport", () -> "stdio");
    registry.add("spring.prism.mcp.stdio.command", McpExampleApplicationTest::javaCommand);
    registry.add("spring.prism.mcp.stdio.args", McpExampleApplicationTest::serverArguments);
    registry.add("spring.prism.mcp.stdio.env.PRISM_MCP_RECORD_PATH", RECORD_PATH::toString);
  }

  @Test
  void exampleBootsAndProtectsMcpPayloads() throws IOException {
    String email = "user@example.com";

    String response =
        restTemplate.getForObject(
            "http://localhost:" + port + "/demo/mcp?email={email}", String.class, email);

    assertThat(response).contains(email);
    String recorded = Files.readString(RECORD_PATH);
    assertThat(recorded).contains("<PRISM_EMAIL_").doesNotContain(email);

    String dashboard =
        restTemplate.getForObject("http://localhost:" + port + "/prism/index.html", String.class);
    String metrics =
        restTemplate.getForObject("http://localhost:" + port + "/actuator/prism", String.class);

    assertThat(dashboard).contains("Spring Prism Dashboard");
    assertThat(metrics).contains("\"mcp-stdio\"");
  }

  private static Path createRecordPath() {
    try {
      return Files.createTempFile("prism-mcp-example", ".json");
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to allocate MCP record file", exception);
    }
  }

  private static String javaCommand() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  private static String[] serverArguments() {
    return new String[] {
      "-cp", System.getProperty("java.class.path"), FakeStdioMcpServer.class.getName()
    };
  }
}
