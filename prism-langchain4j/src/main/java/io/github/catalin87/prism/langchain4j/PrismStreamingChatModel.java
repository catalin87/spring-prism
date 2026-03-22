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

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.vault.StreamingBuffer;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

/**
 * LangChain4j {@link StreamingChatModel} decorator that protects streaming chat requests and
 * restores fragmented Prism tokens across partial responses.
 */
public final class PrismStreamingChatModel implements StreamingChatModel {

  private final StreamingChatModel delegate;
  private final PrismTextScanner scanner;

  /**
   * Creates a Prism-protected wrapper with fail-open defaults and no custom metrics sink.
   *
   * @param delegate the underlying LangChain4j streaming chat model
   * @param rulePacks active rule packs used for PII detection
   * @param vault vault used to store and restore token mappings
   * @param observationRegistry Micrometer observation registry
   */
  public PrismStreamingChatModel(
      @NonNull StreamingChatModel delegate,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry) {
    this(delegate, rulePacks, vault, observationRegistry, PrismMetricsSink.NOOP, false);
  }

  /**
   * Creates a Prism-protected wrapper with an explicit metrics sink and strict-mode setting.
   *
   * @param delegate the underlying LangChain4j streaming chat model
   * @param rulePacks active rule packs used for PII detection
   * @param vault vault used to store and restore token mappings
   * @param observationRegistry Micrometer observation registry
   * @param metricsSink optional runtime metrics callback
   * @param strictMode when true, detector failures abort the call instead of failing open
   */
  public PrismStreamingChatModel(
      @NonNull StreamingChatModel delegate,
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.scanner =
        new PrismTextScanner(rulePacks, vault, observationRegistry, metricsSink, strictMode);
  }

  /**
   * Sanitizes the outbound request before delegation and restores streamed token fragments on the
   * way back.
   */
  @Override
  public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
    ChatRequest sanitized;
    try {
      sanitized = PrismChatModel.sanitizeRequest(chatRequest, scanner);
    } catch (RuntimeException e) {
      handler.onError(e);
      return;
    }

    StreamingBuffer buffer = new StreamingBuffer();
    delegate.chat(
        sanitized,
        new StreamingChatResponseHandler() {
          @Override
          public void onPartialResponse(String partialResponse) {
            String safeChunk = buffer.processChunk(partialResponse);
            String restored = scanner.detokenize(safeChunk);
            if (!restored.isEmpty()) {
              handler.onPartialResponse(restored);
            }
          }

          @Override
          public void onCompleteResponse(ChatResponse completeResponse) {
            buffer.flush();
            handler.onCompleteResponse(PrismChatModel.restoreResponse(completeResponse, scanner));
          }

          @Override
          public void onError(Throwable error) {
            handler.onError(error);
          }
        });
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
}
