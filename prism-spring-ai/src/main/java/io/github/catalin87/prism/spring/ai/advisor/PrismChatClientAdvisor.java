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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.vault.StreamingBuffer;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

/**
 * Spring AI {@link CallAroundAdvisor} and {@link StreamAroundAdvisor} that intercepts chat requests
 * and responses to automatically tokenize outgoing PII and detokenize incoming PII using a {@link
 * PrismVault}.
 *
 * <p>Outgoing user/system prompts are scanned by the registered rule packs. Detected PII is
 * replaced with HMAC-signed vault tokens before dispatch to the LLM. Incoming responses are scanned
 * for vault tokens and restored to the original values before returning to the caller.
 *
 * <p>For streaming responses the {@link StreamingBuffer} prevents token fragmentation across SSE
 * chunks (e.g. {@code <PRISM_} arriving in one chunk, {@code EMAIL_xyz>} in the next).
 */
public class PrismChatClientAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

  private static final int DEFAULT_ORDER = 0;

  private final PrismTextScanner scanner;
  private final int order;

  /**
   * Creates a new advisor with the supplied rule packs, vault, and default order.
   *
   * @param rulePacks The list of active PII rule packs to scan with.
   * @param vault The vault used to store and retrieve token mappings.
   * @param observationRegistry Micrometer registry for emitting redaction metrics.
   */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry) {
    this(rulePacks, vault, observationRegistry, PrismMetricsSink.NOOP, DEFAULT_ORDER);
  }

  /** Creates a new advisor with the supplied rule packs, vault, metrics sink, and default order. */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink) {
    this(rulePacks, vault, observationRegistry, metricsSink, DEFAULT_ORDER);
  }

  /**
   * Creates a new advisor with the supplied rule packs, vault, and explicit advisor chain order.
   *
   * @param rulePacks The list of active PII rule packs to scan with.
   * @param vault The vault used to store and retrieve token mappings.
   * @param observationRegistry Micrometer registry for emitting redaction metrics.
   * @param order The position of this advisor in the advisor chain.
   */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      int order) {
    this(rulePacks, vault, observationRegistry, PrismMetricsSink.NOOP, order);
  }

  /**
   * Creates a new advisor with the supplied rule packs, vault, metrics sink, and explicit advisor
   * chain order.
   */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      int order) {
    this.scanner = new PrismTextScanner(rulePacks, vault, observationRegistry, metricsSink);
    this.order = order;
  }

  // -----------------------------------------------------------------------
  // Synchronous interception
  // -----------------------------------------------------------------------

  @Override
  public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {

    AdvisedRequest sanitized = tokenizeRequest(advisedRequest);
    AdvisedResponse response = chain.nextAroundCall(sanitized);
    return detokenizeResponse(response);
  }

  // -----------------------------------------------------------------------
  // Streaming interception
  // -----------------------------------------------------------------------

  @Override
  public Flux<AdvisedResponse> aroundStream(
      AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {

    AdvisedRequest sanitized = tokenizeRequest(advisedRequest);
    StreamingBuffer buffer = new StreamingBuffer();

    return chain
        .nextAroundStream(sanitized)
        .map(
            advisedResponse -> {
              String rawChunk = extractText(advisedResponse);
              // Push through the fragmentation buffer; emit safe portion
              String safeChunk = buffer.processChunk(rawChunk);
              String restored = scanner.detokenize(safeChunk);
              return rebuildWithText(advisedResponse, restored);
            })
        .concatWith(
            Flux.defer(
                () -> {
                  // Flush any tail fragment remaining after the stream closes
                  String tail = scanner.detokenize(buffer.flush());
                  if (tail.isEmpty()) {
                    return Flux.empty();
                  }
                  return Flux.just(syntheticResponse(tail));
                }));
  }

  // -----------------------------------------------------------------------
  // Advisor metadata
  // -----------------------------------------------------------------------

  @Override
  public String getName() {
    return PrismChatClientAdvisor.class.getSimpleName();
  }

  @Override
  public int getOrder() {
    return order;
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /** Scans user and system text fields in the request and replaces PII with vault tokens. */
  private AdvisedRequest tokenizeRequest(AdvisedRequest original) {
    String sanitizedUserText = scanner.tokenize(original.userText());
    String sanitizedSystemText = scanner.tokenize(original.systemText());

    return AdvisedRequest.from(original)
        .userText(sanitizedUserText)
        .systemText(sanitizedSystemText)
        .build();
  }

  /** Restores vault tokens found in the synchronous response back to their original values. */
  private AdvisedResponse detokenizeResponse(AdvisedResponse original) {
    String raw = extractText(original);
    String restored = scanner.detokenize(raw);
    return rebuildWithText(original, restored);
  }

  /** Extracts the plain-text content from the first generation in the response, if available. */
  private static String extractText(AdvisedResponse response) {
    if (response == null || response.response() == null) {
      return "";
    }
    ChatResponse chatResponse = response.response();
    if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
      return "";
    }
    Generation first = chatResponse.getResults().get(0);
    if (first == null || first.getOutput() == null) {
      return "";
    }
    String text = first.getOutput().getText();
    return text != null ? text : "";
  }

  /**
   * Rebuilds an {@link AdvisedResponse} preserving all metadata but replacing the first
   * generation's text content with the supplied {@code newText}.
   */
  private static AdvisedResponse rebuildWithText(AdvisedResponse original, String newText) {
    if (original == null || original.response() == null) {
      return original;
    }
    ChatResponse oldChat = original.response();
    List<Generation> newGenerations =
        oldChat.getResults().stream()
            .map(
                gen -> {
                  if (gen == null || gen.getOutput() == null) {
                    return gen;
                  }
                  return new Generation(new AssistantMessage(newText), gen.getMetadata());
                })
            .collect(Collectors.toList());

    ChatResponse newChat = new ChatResponse(newGenerations, oldChat.getMetadata());
    return new AdvisedResponse(newChat, original.adviseContext());
  }

  /**
   * Creates a minimal synthetic {@link AdvisedResponse} carrying only a text payload (for flush).
   */
  private static AdvisedResponse syntheticResponse(String text) {
    List<Generation> gens = List.of(new Generation(new AssistantMessage(text)));
    ChatResponse chat = new ChatResponse(gens);
    return new AdvisedResponse(chat, java.util.Map.of());
  }
}
