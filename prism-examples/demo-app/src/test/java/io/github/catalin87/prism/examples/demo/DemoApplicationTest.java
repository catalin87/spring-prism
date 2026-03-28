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
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.prism.vault.type=IN_MEMORY")
class DemoApplicationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void labExposesBootstrapAndMetrics() {
    LabBootstrapResponse bootstrap =
        restTemplate.getForObject(
            "http://localhost:" + port + "/lab/api/bootstrap", LabBootstrapResponse.class);

    assertThat(bootstrap).isNotNull();
    assertThat(bootstrap.integrations()).containsExactly("spring-ai", "langchain4j", "mcp");
    assertThat(bootstrap.availableRulePacks())
        .extracting(LabRulePackOption::id)
        .contains("RO", "US", "DE");

    LabMetricsResponse metrics =
        restTemplate.getForObject(
            "http://localhost:" + port + "/lab/api/metrics", LabMetricsResponse.class);
    assertThat(metrics).isNotNull();
    assertThat(metrics.nodes()).isNotEmpty();
  }

  @Test
  void labShowsSanitizeAndRestoreAcrossAllIntegrations() {
    assertLabRun("spring-ai");
    assertLabRun("langchain4j");
    assertLabRun("mcp");
  }

  private void assertLabRun(String integration) {
    LabRunRequest request =
        new LabRunRequest(
            integration,
            "Please contact Jane Smith at user@example.com and validate RO49AAAA1B31007593840000.",
            List.of("UNIVERSAL", "RO", "US"),
            "FAIL_SAFE",
            "HEURISTIC",
            "LOCAL");

    LabRunResponse response =
        restTemplate.postForObject(
            "http://localhost:" + port + "/lab/api/run", request, LabRunResponse.class);

    assertThat(response).isNotNull();
    assertThat(response.blocked()).isFalse();
    assertThat(response.restoredResponse())
        .contains("user@example.com")
        .contains("RO49AAAA1B31007593840000");
    assertThat(response.sanitizedOutbound()).contains("<PRISM_").doesNotContain("user@example.com");
    assertThat(response.mockModelResponse()).contains("<PRISM_");
    assertThat(response.traceEvents()).isNotEmpty();
  }
}
