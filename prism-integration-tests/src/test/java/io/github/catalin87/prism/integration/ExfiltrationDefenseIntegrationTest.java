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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.catalin87.prism.boot.autoconfigure.SpringPrismAutoConfiguration;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * End-to-end test for the Active Defense exfiltration ceiling. A WireMock-stubbed LLM is configured
 * to echo back a response containing 105 distinct PRISM vault tokens; with the ceiling set to 100
 * the advisor must block the response (sync) or terminate the subscription (stream) and increment
 * the dedicated Micrometer counter exactly once.
 */
class ExfiltrationDefenseIntegrationTest {

  private static final String MODEL = "gpt-4o-mini";
  private static final String EXFILTRATION_COUNTER = "gen_ai.prism.protection.exfiltration.blocked";
  private static final int CEILING = 100;
  private static final int TOKEN_COUNT = 105;
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
    io.micrometer.core.instrument.Metrics.globalRegistry.clear();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @Test
  void synchronousResponseExceedingCeilingIsBlockedAndCounted() {
    context = startContext();
    List<String> tokens = preTokenize(TOKEN_COUNT);
    String payload = String.join(" ", tokens);

    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matching("(?s)^(?!.*\"stream\":true).*"))
            .willReturn(okJson(chatCompletion(payload))));

    ChatClient client = chatClient();

    assertThatThrownBy(() -> client.prompt().user("echo tokens").call().content())
        .isInstanceOf(PrismProtectionException.class)
        .satisfies(
            ex ->
                assertThat(((PrismProtectionException) ex).reason())
                    .isEqualTo(PrismProtectionReason.EXFILTRATION_THRESHOLD_EXCEEDED));

    assertThat(exfiltrationBlockedCount()).isEqualTo(1.0);
  }

  @Test
  void streamingResponseExceedingCeilingIsBlockedAndCounted() {
    context = startContext();
    List<String> tokens = preTokenize(TOKEN_COUNT);

    wireMockServer.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matching("(?s).*\"stream\":true.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(streamingCompletionBody(tokens))));

    ChatClient client = chatClient();
    Flux<String> stream = client.prompt().user("echo tokens").stream().content();

    StepVerifier.create(stream)
        .thenConsumeWhile(chunk -> true)
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(PrismProtectionException.class);
              assertThat(((PrismProtectionException) error).reason())
                  .isEqualTo(PrismProtectionReason.EXFILTRATION_THRESHOLD_EXCEEDED);
            })
        .verify();

    assertThat(exfiltrationBlockedCount()).isEqualTo(1.0);
  }

  // -----------------------------------------------------------------------
  // Context wiring
  // -----------------------------------------------------------------------

  private ConfigurableApplicationContext startContext() {
    return new SpringApplicationBuilder(TestConfiguration.class)
        .web(WebApplicationType.NONE)
        .properties(
            "spring.application.name=exfiltration-integration",
            "spring.main.banner-mode=off",
            "spring.prism.enabled=true",
            "spring.prism.app-secret=exfiltration-test-secret-32-bytes",
            "spring.prism.active-defense.max-restored-tokens-per-response=" + CEILING)
        .run();
  }

  private ChatClient chatClient() {
    OpenAiApi openAiApi = new OpenAiApi(wireMockServer.baseUrl(), "test-key");
    OpenAiChatModel chatModel =
        new OpenAiChatModel(
            openAiApi, OpenAiChatOptions.builder().model(MODEL).temperature(0.0).build());
    return ChatClient.builder(chatModel)
        .defaultAdvisors(context.getBean(PrismChatClientAdvisor.class))
        .build();
  }

  /**
   * Pre-tokenises {@code count} distinct PII values directly against the vault so we can synthesise
   * an LLM response containing exactly that many restorable tokens without depending on the
   * detector path. Each call returns a fresh, distinct token key.
   */
  private List<String> preTokenize(int count) {
    PrismVault vault = context.getBean(PrismVault.class);
    List<String> tokenKeys = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      PrismToken token = vault.tokenize("email-" + i + "@example.com", "EMAIL");
      tokenKeys.add(token.key());
    }
    return tokenKeys;
  }

  /**
   * The advisor's exfiltration counter is registered against the {@link MeterRegistry} resolved by
   * the starter's {@link org.springframework.beans.factory.ObjectProvider}. The test context
   * publishes a single {@code @Primary} {@link
   * io.micrometer.core.instrument.simple.SimpleMeterRegistry} so the provider deterministically
   * resolves to it.
   */
  private double exfiltrationBlockedCount() {
    MeterRegistry registry = context.getBean(MeterRegistry.class);
    var counter = registry.find(EXFILTRATION_COUNTER).counter();
    return counter == null ? 0.0 : counter.count();
  }

  // -----------------------------------------------------------------------
  // WireMock body builders
  // -----------------------------------------------------------------------

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

  /**
   * Emits one SSE chunk per vault token so the streaming exfiltration guard observes the threshold
   * being crossed mid-stream rather than on the final flush.
   */
  private static String streamingCompletionBody(List<String> tokens) {
    StringBuilder body = new StringBuilder(tokens.size() * 256);
    for (String token : tokens) {
      body.append(sseChunk(token + " "));
    }
    body.append(
        """
        data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1710000001,"model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """
            .formatted(MODEL));
    return body.toString();
  }

  private static String sseChunk(String content) {
    return """
        data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1710000001,"model":"%s","choices":[{"index":0,"delta":{"content":"%s"},"finish_reason":null}]}

        """
        .formatted(MODEL, jsonEscape(content));
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  @SpringBootTest
  @ImportAutoConfiguration({SpringPrismAutoConfiguration.class})
  @Configuration(proxyBeanMethods = false)
  static class TestConfiguration {

    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    MeterRegistry testMeterRegistry() {
      return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }
  }
}
