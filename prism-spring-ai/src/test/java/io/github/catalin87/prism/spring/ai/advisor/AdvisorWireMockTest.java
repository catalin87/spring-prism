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
package io.github.catalin87.prism.spring.ai.advisor;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class AdvisorWireMockTest {

  private static final byte[] SECRET = "wiremock-test-key-material-32!!!!".getBytes();
  private static final String MODEL = "gpt-4o-mini";

  @RegisterExtension
  static final WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .options(
              com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                  .dynamicPort())
          .build();

  private PrismVault vault;
  private PrismChatClientAdvisor advisor;
  private ChatClient client;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
    List<PrismRulePack> packs = List.of(new UniversalRulePack());
    advisor = new PrismChatClientAdvisor(packs, vault, ObservationRegistry.NOOP);

    OpenAiApi openAiApi = new OpenAiApi(wireMock.baseUrl(), "test-key");

    OpenAiChatModel chatModel =
        new OpenAiChatModel(
            openAiApi, OpenAiChatOptions.builder().model(MODEL).temperature(0.0).build());

    client = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();
  }

  @Test
  void advisorRestoresFragmentedStreamingTokensOverHttp() {
    String token = vault.tokenize("user@example.com", "EMAIL").key();

    wireMock.stubFor(
        post(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matching("(?s).*\"stream\":true.*"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "text/event-stream")
                    .withBody(streamingCompletionBody(token))));

    Flux<String> content = client.prompt().user("Email user@example.com").stream().content();

    StepVerifier.create(content)
        .expectNext("Message for ")
        .expectNext("user@example.com")
        .verifyComplete();

    wireMock.verify(
        postRequestedFor(urlEqualTo("/v1/chat/completions"))
            .withRequestBody(matching("(?s).*\"stream\":true.*"))
            .withRequestBody(matching("(?s).*PRISM_EMAIL_.*")));
  }

  private static String streamingCompletionBody(String token) {
    String first = sseChunk("Message for ");
    String second = sseChunk(token.substring(0, 7));
    String third = sseChunk(token.substring(7));
    String finish =
        """
        data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1710000001,"model":"%s","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

        data: [DONE]

        """
            .formatted(MODEL);
    return first + second + third + finish;
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
}
