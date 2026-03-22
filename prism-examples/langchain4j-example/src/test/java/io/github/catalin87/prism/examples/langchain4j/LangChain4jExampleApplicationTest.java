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
package io.github.catalin87.prism.examples.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Integration tests for the LangChain4j example app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LangChain4jExampleApplicationTest {

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private LangChain4jExampleConfig.RecordingLangChainChatModel delegateChatModel;

  @Test
  void exampleBootsAndRestoresPiiForLangChain4j() {
    String email = "user@example.com";

    String response =
        restTemplate.getForObject(
            "http://localhost:" + port + "/demo/langchain4j?email={email}", String.class, email);

    assertThat(response).contains(email);
    assertThat(delegateChatModel.requests()).isNotEmpty();
    UserMessage recorded =
        (UserMessage) delegateChatModel.requests().getLast().messages().getLast();
    assertThat(recorded.singleText()).contains("<PRISM_EMAIL_").doesNotContain(email);
  }
}
