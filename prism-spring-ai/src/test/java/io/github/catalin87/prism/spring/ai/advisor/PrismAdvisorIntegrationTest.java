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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Integration test verifying that PrismChatClientAdvisor correctly intercepts calls and tokenizes
 * PII.
 */
class PrismAdvisorIntegrationTest {

  private static final byte[] SECRET = "test-key-32-bytes-long-padding!!".getBytes();

  private PrismVault vault;
  private PrismChatClientAdvisor advisor;
  private ChatModel chatModel;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
    List<PrismRulePack> packs = List.of(new UniversalRulePack());
    advisor = new PrismChatClientAdvisor(packs, vault, ObservationRegistry.NOOP);
    chatModel = mock(ChatModel.class);
  }

  @Test
  void advisorTokenizesPromptBeforeSendingToModel() {
    // --- Arrange ---
    ChatResponse mockResponse =
        new ChatResponse(List.of(new Generation(new AssistantMessage("OK"))));
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

    ChatClient client = ChatClient.builder(chatModel).defaultAdvisors(advisor).build();

    // --- Act ---
    client.prompt().user("Help user@example.com").call().content();

    // --- Assert ---
    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    org.mockito.Mockito.verify(chatModel).call(promptCaptor.capture());

    String sentContent = promptCaptor.getValue().getContents();
    assertThat(sentContent).doesNotContain("user@example.com");
    assertThat(sentContent).contains("PRISM_EMAIL_");
  }

  @Test
  void advisorRestoresFragmentedTokensAcrossStreamingChunks() {
    PrismToken token = vault.tokenize("user@example.com", "EMAIL");
    String key = token.key();

    AdvisedRequest request =
        AdvisedRequest.builder()
            .chatModel(chatModel)
            .userText("Hello")
            .systemText("")
            .userParams(java.util.Map.of())
            .systemParams(java.util.Map.of())
            .advisorParams(java.util.Map.of())
            .adviseContext(java.util.Map.of())
            .toolContext(java.util.Map.of())
            .messages(List.of())
            .media(List.of())
            .functionNames(List.of())
            .functionCallbacks(List.of())
            .advisors(List.of())
            .build();

    StreamAroundAdvisorChain chain = mock(StreamAroundAdvisorChain.class);
    when(chain.nextAroundStream(eq(request)))
        .thenReturn(
            Flux.just(
                advisedChunk("Message for "),
                advisedChunk(key.substring(0, 6)),
                advisedChunk(key.substring(6))));

    Flux<AdvisedResponse> result = advisor.aroundStream(request, chain);

    StepVerifier.create(
            result.map(response -> response.response().getResults().get(0).getOutput().getText()))
        .expectNext("Message for ")
        .expectNext("")
        .expectNext("user@example.com")
        .verifyComplete();
  }

  private static AdvisedResponse advisedChunk(String text) {
    return new AdvisedResponse(
        new ChatResponse(List.of(new Generation(new AssistantMessage(text)))), java.util.Map.of());
  }
}
