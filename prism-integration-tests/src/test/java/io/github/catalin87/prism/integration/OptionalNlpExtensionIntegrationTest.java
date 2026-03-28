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
package io.github.catalin87.prism.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.catalin87.prism.boot.autoconfigure.PrismActuatorEndpoint;
import io.github.catalin87.prism.boot.autoconfigure.PrismMetricsSnapshot;
import io.github.catalin87.prism.boot.autoconfigure.SpringPrismAutoConfiguration;
import io.github.catalin87.prism.extensions.nlp.autoconfigure.PrismNlpAutoConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class OptionalNlpExtensionIntegrationTest {

  private static final String MODEL = "gpt-4o-mini";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static WireMockServer wireMockServer;

  private ConfigurableApplicationContext context;

  @BeforeAll
  static void startWireMock() {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .http2PlainDisabled(true)
                .http2TlsDisabled(true));
    wireMockServer.start();
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
      context = null;
    }
    wireMockServer.resetAll();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void defaultModeLeavesPersonNamesUntouched() throws Exception {
    context = startContext();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

    client().prompt().user("Customer John Doe emailed user@example.com.").call().content();

    String outboundPrompt = lastPromptContent();
    assertThat(outboundPrompt).contains("John Doe").contains("<PRISM_EMAIL_");
    assertThat(outboundPrompt).doesNotContain("PRISM_PERSON_NAME");
  }

  @Test
  void heuristicNlpModeTokenizesAndRestoresPersonNames() throws Exception {
    context =
        startContext(
            "spring.prism.extensions.nlp.enabled=true",
            "spring.prism.extensions.nlp.backend=heuristic");
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

    client().prompt().user("Customer John Doe emailed user@example.com.").call().content();

    String sanitizedPrompt = lastPromptContent();
    assertThat(sanitizedPrompt).contains("<PRISM_PERSON_NAME_", "<PRISM_EMAIL_");
    assertThat(sanitizedPrompt).doesNotContain("John Doe", "user@example.com");

    wireMockServer.resetAll();
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .willReturn(okJson(chatCompletion("Response replay " + sanitizedPrompt))));

    String restored = client().prompt().user("restore person name").call().content();

    assertThat(restored).contains("John Doe", "user@example.com");
    PrismMetricsSnapshot snapshot = context.getBean(PrismActuatorEndpoint.class).metrics();
    assertThat(snapshot.activeRulePacks()).contains("NLP_EXTENSIONS");
    assertThat(snapshot.entityMetrics())
        .extracting(metric -> metric.entityType())
        .contains("PERSON_NAME");
  }

  @Test
  void heuristicNlpModeDoesNotTreatSpringBootAsPerson() throws Exception {
    context =
        startContext(
            "spring.prism.extensions.nlp.enabled=true",
            "spring.prism.extensions.nlp.backend=heuristic");
    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(chatCompletion("ACK"))));

    client().prompt().user("Spring Boot service belongs to customer John Doe.").call().content();

    String outboundPrompt = lastPromptContent();
    assertThat(outboundPrompt).contains("Spring Boot").contains("<PRISM_PERSON_NAME_");
    assertThat(outboundPrompt).doesNotContain("John Doe");
  }

  private ConfigurableApplicationContext startContext(String... additionalProperties) {
    List<String> properties =
        new java.util.ArrayList<>(
            List.of(
                "spring.application.name=nlp-integration",
                "spring.main.banner-mode=off",
                "spring.prism.enabled=true",
                "spring.prism.app-secret=nlp-extension-secret-32-bytes"));
    properties.addAll(List.of(additionalProperties));
    return new SpringApplicationBuilder(TestConfiguration.class)
        .web(WebApplicationType.NONE)
        .properties(properties.toArray(String[]::new))
        .run();
  }

  private ChatClient client() {
    return ChatClient.builder(
            new OpenAiChatModel(
                new OpenAiApi(wireMockServer.baseUrl(), "test-key"),
                OpenAiChatOptions.builder().model(MODEL).temperature(0.0).build()))
        .defaultAdvisors(
            context.getBean(
                io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor.class))
        .build();
  }

  private String lastPromptContent() throws Exception {
    JsonNode body =
        OBJECT_MAPPER.readTree(
            wireMockServer.getAllServeEvents().getLast().getRequest().getBodyAsString());
    return body.path("messages").get(0).path("content").asText();
  }

  private static String chatCompletion(String content) {
    return """
        {
          "id": "chatcmpl-test",
          "object": "chat.completion",
          "created": 1710000001,
          "model": "%s",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": %s
              },
              "finish_reason": "stop"
            }
          ]
        }
        """
        .formatted(MODEL, OBJECT_MAPPER.valueToTree(content).toString());
  }

  @SpringBootTest
  @ImportAutoConfiguration({SpringPrismAutoConfiguration.class, PrismNlpAutoConfiguration.class})
  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {}
}
