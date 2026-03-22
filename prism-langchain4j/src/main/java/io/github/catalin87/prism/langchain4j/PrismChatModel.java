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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * LangChain4j {@link ChatModel} decorator that tokenizes outbound user/system text and restores
 * Prism tokens in assistant responses.
 */
public final class PrismChatModel implements ChatModel {

  private final ChatModel delegate;
  private final PrismTextScanner scanner;

  /**
   * Creates a Prism-protected wrapper with fail-open defaults and no custom metrics sink.
   *
   * @param delegate the underlying LangChain4j chat model
   * @param rulePacks active rule packs used for PII detection
   * @param vault vault used to store and restore token mappings
   * @param observationRegistry Micrometer observation registry
   */
  public PrismChatModel(
      @NonNull ChatModel delegate,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry) {
    this(delegate, rulePacks, vault, observationRegistry, PrismMetricsSink.NOOP, false);
  }

  /**
   * Creates a Prism-protected wrapper with an explicit metrics sink and strict-mode setting.
   *
   * @param delegate the underlying LangChain4j chat model
   * @param rulePacks active rule packs used for PII detection
   * @param vault vault used to store and restore token mappings
   * @param observationRegistry Micrometer observation registry
   * @param metricsSink optional runtime metrics callback
   * @param strictMode when true, detector failures abort the call instead of failing open
   */
  public PrismChatModel(
      @NonNull ChatModel delegate,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.scanner =
        new PrismTextScanner(rulePacks, vault, observationRegistry, metricsSink, strictMode);
  }

  /** Sanitizes the request before delegating to the wrapped model and restores response tokens. */
  @Override
  public ChatResponse chat(ChatRequest chatRequest) {
    ChatResponse response = delegate.chat(sanitizeRequest(chatRequest));
    return restoreResponse(response);
  }

  @Override
  public ChatRequestParameters defaultRequestParameters() {
    return delegate.defaultRequestParameters();
  }

  @Override
  public List<ChatModelListener> listeners() {
    return delegate.listeners();
  }

  @Override
  public ModelProvider provider() {
    return delegate.provider();
  }

  @Override
  public Set<Capability> supportedCapabilities() {
    return delegate.supportedCapabilities();
  }

  static ChatRequest sanitizeRequest(ChatRequest original, PrismTextScanner scanner) {
    List<ChatMessage> originalMessages = original.messages();
    List<ChatMessage> sanitizedMessages = new ArrayList<>(originalMessages.size());
    for (ChatMessage message : originalMessages) {
      sanitizedMessages.add(sanitizeMessage(message, scanner));
    }

    ChatRequest.Builder builder = ChatRequest.builder().messages(sanitizedMessages);
    copyRequestMetadata(original, builder);
    return builder.build();
  }

  private ChatRequest sanitizeRequest(ChatRequest original) {
    return sanitizeRequest(original, scanner);
  }

  static ChatResponse restoreResponse(ChatResponse original, PrismTextScanner scanner) {
    if (original == null || original.aiMessage() == null || original.aiMessage().text() == null) {
      return original;
    }

    AiMessage aiMessage = original.aiMessage();
    String restoredText = scanner.detokenize(aiMessage.text());
    AiMessage restoredMessage =
        aiMessage.hasToolExecutionRequests()
            ? AiMessage.from(restoredText, aiMessage.toolExecutionRequests())
            : AiMessage.from(restoredText);

    ChatResponse.Builder builder = ChatResponse.builder().aiMessage(restoredMessage);
    if (original.metadata() != null) {
      builder.metadata(original.metadata());
    }
    if (original.id() != null) {
      builder.id(original.id());
    }
    if (original.modelName() != null) {
      builder.modelName(original.modelName());
    }
    if (original.tokenUsage() != null) {
      builder.tokenUsage(original.tokenUsage());
    }
    if (original.finishReason() != null) {
      builder.finishReason(original.finishReason());
    }
    return builder.build();
  }

  private ChatResponse restoreResponse(ChatResponse original) {
    return restoreResponse(original, scanner);
  }

  private static ChatMessage sanitizeMessage(ChatMessage message, PrismTextScanner scanner) {
    if (message instanceof SystemMessage systemMessage) {
      return SystemMessage.from(sanitizeText(systemMessage.text(), scanner));
    }

    if (message instanceof UserMessage userMessage) {
      if (userMessage.hasSingleText()) {
        String sanitized = sanitizeText(userMessage.singleText(), scanner);
        return userMessage.name() == null
            ? UserMessage.from(sanitized)
            : UserMessage.from(userMessage.name(), sanitized);
      }

      List<Content> contents = new ArrayList<>(userMessage.contents().size());
      for (Content content : userMessage.contents()) {
        if (content instanceof TextContent textContent) {
          contents.add(TextContent.from(sanitizeText(textContent.text(), scanner)));
        } else {
          contents.add(content);
        }
      }
      return userMessage.name() == null
          ? UserMessage.from(contents)
          : UserMessage.from(userMessage.name(), contents);
    }

    return message;
  }

  private static String sanitizeText(String text, PrismTextScanner scanner) {
    String sanitized = scanner.tokenize(text);
    return sanitized != null ? sanitized : text;
  }

  private static void copyRequestMetadata(ChatRequest original, ChatRequest.Builder builder) {
    if (original.parameters() != null) {
      builder.parameters(original.parameters());
      return;
    }
    if (original.modelName() != null) {
      builder.modelName(original.modelName());
    }
    if (original.temperature() != null) {
      builder.temperature(original.temperature());
    }
    if (original.topP() != null) {
      builder.topP(original.topP());
    }
    if (original.topK() != null) {
      builder.topK(original.topK());
    }
    if (original.frequencyPenalty() != null) {
      builder.frequencyPenalty(original.frequencyPenalty());
    }
    if (original.presencePenalty() != null) {
      builder.presencePenalty(original.presencePenalty());
    }
    if (original.maxOutputTokens() != null) {
      builder.maxOutputTokens(original.maxOutputTokens());
    }
    if (original.stopSequences() != null) {
      builder.stopSequences(original.stopSequences());
    }
    if (original.toolSpecifications() != null) {
      builder.toolSpecifications(original.toolSpecifications());
    }
    if (original.toolChoice() != null) {
      builder.toolChoice(original.toolChoice());
    }
    if (original.responseFormat() != null) {
      builder.responseFormat(original.responseFormat());
    }
  }
}
