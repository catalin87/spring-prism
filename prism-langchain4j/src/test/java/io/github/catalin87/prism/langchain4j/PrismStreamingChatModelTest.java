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
package io.github.catalin87.prism.langchain4j;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PrismStreamingChatModel}. */
class PrismStreamingChatModelTest {

  private static final byte[] SECRET = "langchain4j-streaming-secret-mat!".getBytes();

  private PrismVault vault;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
  }

  @Test
  void chatRestoresFragmentedTokensAcrossStreamingCallbacks() {
    String token = vault.tokenize("user@example.com", "EMAIL").key();
    RecordingStreamingChatModel delegate =
        new RecordingStreamingChatModel(
            handler -> {
              handler.onPartialResponse("Message for ");
              handler.onPartialResponse(token.substring(0, 7));
              handler.onPartialResponse(token.substring(7));
              handler.onCompleteResponse(
                  ChatResponse.builder().aiMessage(AiMessage.from("Message for " + token)).build());
            });

    PrismStreamingChatModel prismModel =
        new PrismStreamingChatModel(
            delegate, List.of(new UniversalRulePack()), vault, ObservationRegistry.NOOP);

    List<String> partials = new ArrayList<>();
    AtomicReference<ChatResponse> completed = new AtomicReference<>();

    prismModel.chat(
        ChatRequest.builder().messages(List.of(UserMessage.from("email user@example.com"))).build(),
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partialResponse) {
            partials.add(partialResponse);
          }

          @Override
          public void onCompleteResponse(ChatResponse completeResponse) {
            completed.set(completeResponse);
          }

          @Override
          public void onError(Throwable error) {
            throw new AssertionError(error);
          }
        });

    assertThat(((UserMessage) delegate.recordedRequest.messages().get(0)).singleText())
        .contains("<PRISM_EMAIL_")
        .doesNotContain("user@example.com");
    assertThat(partials).containsExactly("Message for ", "user@example.com");
    assertThat(completed.get()).isNotNull();
    assertThat(completed.get().aiMessage().text()).isEqualTo("Message for user@example.com");
  }

  @Test
  void chatStrictModeReportsDetectorFailureToHandler() {
    RecordingStreamingChatModel delegate = new RecordingStreamingChatModel(handler -> {});
    PrismStreamingChatModel prismModel =
        new PrismStreamingChatModel(
            delegate,
            List.of(new FailingRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            true);

    AtomicReference<Throwable> failure = new AtomicReference<>();

    prismModel.chat(
        ChatRequest.builder().messages(List.of(UserMessage.from("safe"))).build(),
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partialResponse) {}

          @Override
          public void onCompleteResponse(ChatResponse completeResponse) {}

          @Override
          public void onError(Throwable error) {
            failure.set(error);
          }
        });

    assertThat(failure.get())
        .isInstanceOfSatisfying(
            PrismProtectionException.class,
            error -> {
              assertThat(error.phase()).isEqualTo(PrismProtectionPhase.DETECT);
              assertThat(error.reason()).isEqualTo(PrismProtectionReason.DETECTOR_FAILURE);
              assertThat(error.failureMode()).isEqualTo(PrismFailureMode.FAIL_CLOSED);
              assertThat(error.integration()).isEqualTo(PrismMetricsSink.LANGCHAIN4J_INTEGRATION);
              assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
            });
    assertThat(delegate.recordedRequest).isNull();
  }

  private static final class RecordingStreamingChatModel implements StreamingChatModel {
    private final StreamBehavior behavior;
    private ChatRequest recordedRequest;

    private RecordingStreamingChatModel(StreamBehavior behavior) {
      this.behavior = behavior;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
      this.recordedRequest = chatRequest;
      behavior.run(handler);
    }
  }

  @FunctionalInterface
  private interface StreamBehavior {
    void run(StreamingChatResponseHandler handler);
  }

  private static final class FailingRulePack implements PrismRulePack {
    @Override
    public @NonNull List<PiiDetector> getDetectors() {
      return List.of(new FailingDetector());
    }

    @Override
    public @NonNull String getName() {
      return "FAILING";
    }
  }

  private static final class FailingDetector implements PiiDetector {
    @Override
    public @NonNull String getEntityType() {
      return "FAIL";
    }

    @Override
    public @NonNull List<PiiCandidate> detect(@NonNull String text) {
      throw new IllegalStateException("boom");
    }
  }
}
