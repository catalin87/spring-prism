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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
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
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PrismChatModel}. */
class PrismChatModelTest {

  private static final byte[] SECRET = "langchain4j-test-secret-material!!".getBytes();

  private PrismVault vault;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
  }

  @Test
  void chatSanitizesUserAndSystemMessagesAndRestoresAssistantText() {
    RecordingChatModel delegate =
        new RecordingChatModel(
            request ->
                ChatResponse.builder()
                    .aiMessage(
                        AiMessage.from(
                            "Reply for " + vault.tokenize("user@example.com", "EMAIL").key()))
                    .build());

    PrismChatModel prismModel =
        new PrismChatModel(
            delegate, List.of(new UniversalRulePack()), vault, ObservationRegistry.NOOP);

    ChatRequest request =
        ChatRequest.builder()
            .messages(
                List.of(
                    SystemMessage.from("Escalate to user@example.com"),
                    UserMessage.from("Call +40 712 345 678 and email user@example.com")))
            .modelName("test-model")
            .temperature(0.1)
            .build();

    ChatResponse response = prismModel.chat(request);

    assertThat(delegate.recordedRequest).isNotNull();
    assertThat(delegate.recordedRequest.messages()).hasSize(2);
    assertThat(((SystemMessage) delegate.recordedRequest.messages().get(0)).text())
        .contains("<PRISM_EMAIL_")
        .doesNotContain("user@example.com");
    assertThat(((UserMessage) delegate.recordedRequest.messages().get(1)).singleText())
        .contains("<PRISM_EMAIL_")
        .contains("<PRISM_PHONE_NUMBER_")
        .doesNotContain("user@example.com")
        .doesNotContain("+40 712 345 678");
    assertThat(delegate.recordedRequest.modelName()).isEqualTo("test-model");
    assertThat(delegate.recordedRequest.temperature()).isEqualTo(0.1);
    assertThat(response.aiMessage().text()).isEqualTo("Reply for user@example.com");
  }

  @Test
  void chatSanitizesStructuredUserMessageTextContentAndPreservesName() {
    RecordingChatModel delegate =
        new RecordingChatModel(
            request -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

    PrismChatModel prismModel =
        new PrismChatModel(
            delegate, List.of(new UniversalRulePack()), vault, ObservationRegistry.NOOP);

    UserMessage userMessage =
        UserMessage.builder()
            .name("operator")
            .addContent(TextContent.from("Contact user@example.com"))
            .build();

    prismModel.chat(ChatRequest.builder().messages(List.of(userMessage)).build());

    UserMessage sanitized = (UserMessage) delegate.recordedRequest.messages().get(0);
    assertThat(sanitized.name()).isEqualTo("operator");
    assertThat(((TextContent) sanitized.contents().get(0)).text())
        .contains("<PRISM_EMAIL_")
        .doesNotContain("user@example.com");
  }

  @Test
  void chatFailOpenLeavesRequestUntouchedWhenDetectorFails() {
    RecordingChatModel delegate =
        new RecordingChatModel(
            request -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

    PrismChatModel prismModel =
        new PrismChatModel(
            delegate,
            List.of(new FailingRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            false);

    prismModel.chat(
        ChatRequest.builder().messages(List.of(UserMessage.from("safe input"))).build());

    assertThat(((UserMessage) delegate.recordedRequest.messages().get(0)).singleText())
        .isEqualTo("safe input");
  }

  @Test
  void chatStrictModeThrowsWhenDetectorFails() {
    RecordingChatModel delegate =
        new RecordingChatModel(
            request -> ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

    PrismChatModel prismModel =
        new PrismChatModel(
            delegate,
            List.of(new FailingRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            true);

    assertThatThrownBy(
            () ->
                prismModel.chat(
                    ChatRequest.builder().messages(List.of(UserMessage.from("safe"))).build()))
        .isInstanceOfSatisfying(
            PrismProtectionException.class,
            error -> {
              assertThat(error.phase()).isEqualTo(PrismProtectionPhase.DETECT);
              assertThat(error.reason()).isEqualTo(PrismProtectionReason.DETECTOR_FAILURE);
              assertThat(error.failureMode()).isEqualTo(PrismFailureMode.FAIL_CLOSED);
              assertThat(error.integration()).isEqualTo(PrismMetricsSink.LANGCHAIN4J_INTEGRATION);
              assertThat(error)
                  .hasMessageContaining(
                      "Request blocked: Privacy constraints could not be enforced.");
              assertThat(error.getCause()).isInstanceOf(IllegalStateException.class);
            });
  }

  @Test
  void chatRecordsTimingMetrics() {
    RecordingMetricsSink metricsSink = new RecordingMetricsSink();
    RecordingChatModel delegate =
        new RecordingChatModel(
            request ->
                ChatResponse.builder()
                    .aiMessage(
                        AiMessage.from(
                            "Reply for " + vault.tokenize("user@example.com", "EMAIL").key()))
                    .build());

    PrismChatModel prismModel =
        new PrismChatModel(
            delegate,
            List.of(new UniversalRulePack()),
            vault,
            ObservationRegistry.NOOP,
            metricsSink,
            false);

    prismModel.chat(
        ChatRequest.builder().messages(List.of(UserMessage.from("user@example.com"))).build());

    assertThat(metricsSink.scanDurationCalls).isEqualTo(1);
    assertThat(metricsSink.tokenizeDurationCalls).isEqualTo(1);
    assertThat(metricsSink.detokenizeDurationCalls).isEqualTo(1);
    assertThat(metricsSink.lastIntegration).isEqualTo(PrismMetricsSink.LANGCHAIN4J_INTEGRATION);
    assertThat(metricsSink.lastRecordedNanos).isGreaterThanOrEqualTo(0L);
  }

  private static final class RecordingChatModel implements ChatModel {
    private final ResponseFactory factory;
    private ChatRequest recordedRequest;

    private RecordingChatModel(ResponseFactory factory) {
      this.factory = factory;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
      this.recordedRequest = chatRequest;
      return factory.create(chatRequest);
    }
  }

  @FunctionalInterface
  private interface ResponseFactory {
    ChatResponse create(ChatRequest request);
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

  private static final class RecordingMetricsSink implements PrismMetricsSink {
    private int scanDurationCalls;
    private int tokenizeDurationCalls;
    private int detokenizeDurationCalls;
    private String lastIntegration = "";
    private long lastRecordedNanos;

    @Override
    public void onDetected(@NonNull String rulePackName, @NonNull String entityType, int count) {}

    @Override
    public void onDetectionError(@NonNull String rulePackName, @NonNull String entityType) {}

    @Override
    public void onTokenized(int count) {}

    @Override
    public void onDetokenized(int count) {}

    @Override
    public void onScanDuration(@NonNull String integration, long nanos) {
      scanDurationCalls++;
      lastIntegration = integration;
      lastRecordedNanos = nanos;
    }

    @Override
    public void onVaultTokenizeDuration(@NonNull String integration, long nanos) {
      tokenizeDurationCalls++;
      lastIntegration = integration;
      lastRecordedNanos = nanos;
    }

    @Override
    public void onVaultDetokenizeDuration(@NonNull String integration, long nanos) {
      detokenizeDurationCalls++;
      lastIntegration = integration;
      lastRecordedNanos = nanos;
    }
  }
}
