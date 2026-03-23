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
package io.github.catalin87.prism.examples.springai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Integration tests for the Spring AI example app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringAiExampleApplicationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private SpringAiExampleConfig.RecordingSpringAiChatModel recordingSpringAiChatModel;

  @Test
  void exampleBootsAndRestoresPiiForSpringAi() {
    String email = "user@example.com";

    String response =
        restTemplate.getForObject(
            "http://localhost:" + port + "/demo/spring-ai?email={email}", String.class, email);

    assertThat(response).contains("user@example.com");
    assertThat(recordingSpringAiChatModel.prompts()).isNotEmpty();
    assertThat(recordingSpringAiChatModel.prompts().getLast())
        .contains("<PRISM_EMAIL_")
        .doesNotContain(email);

    String dashboard =
        restTemplate.getForObject("http://localhost:" + port + "/prism/index.html", String.class);
    String metrics =
        restTemplate.getForObject("http://localhost:" + port + "/actuator/prism", String.class);

    assertThat(dashboard).contains("Spring Prism Dashboard");
    assertThat(metrics).contains("\"activeRulePacks\"");
  }
}
