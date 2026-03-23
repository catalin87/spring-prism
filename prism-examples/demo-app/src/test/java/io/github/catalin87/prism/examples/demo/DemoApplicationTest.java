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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Integration tests for the unified demo app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void demoAppExposesOptionsAndFrontend() {
    DemoOptionsResponse options =
        restTemplate.postForObject(
            "http://localhost:" + port + "/demo-lab/api/options", null, DemoOptionsResponse.class);

    assertThat(options).isNotNull();
    assertThat(options.integrations()).containsExactly("spring-ai", "langchain4j", "mcp");
    assertThat(options.rulePacks()).contains("EUROPE");

    String page =
        restTemplate.getForObject(
            "http://localhost:" + port + "/demo-lab/index.html", String.class);
    assertThat(page).contains("Prism Demo Lab");
  }

  @Test
  void demoAppShowsSanitizeAndRestoreAcrossAllIntegrations() {
    assertDemo("spring-ai");
    assertDemo("langchain4j");
    assertDemo("mcp");
  }

  private void assertDemo(String integration) {
    DemoRunRequest request =
        new DemoRunRequest(
            integration,
            "Please contact user@example.com and validate RO49AAAA1B31007593840000.",
            List.of("UNIVERSAL", "EUROPE"));

    DemoRunResponse response =
        restTemplate.postForObject(
            "http://localhost:" + port + "/demo-lab/api/run", request, DemoRunResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.restoredResponse())
        .contains("user@example.com")
        .contains("RO49AAAA1B31007593840000");
    assertThat(response.sanitizedOutbound()).contains("<PRISM_").doesNotContain("user@example.com");
    assertThat(response.mockModelResponse()).contains("<PRISM_");
  }
}
