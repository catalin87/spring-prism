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

import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.guard.ExfiltrationGuard;
import io.github.catalin87.prism.core.vault.StreamingBuffer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
 *
 * <p><strong>Active Defense (since 1.2.0):</strong> when an {@link ActiveDefenseConfig} with an
 * enabled threshold is supplied, the advisor counts vault tokens restored per synchronous response
 * and per streaming subscription. Exceeding the ceiling raises a {@link PrismProtectionException}
 * with reason {@link PrismProtectionReason#EXFILTRATION_THRESHOLD_EXCEEDED} and increments the
 * {@code gen_ai.prism.protection.exfiltration.blocked} counter.
 */
public class PrismChatClientAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(PrismChatClientAdvisor.class);
  private static final int DEFAULT_ORDER = 0;
  private static final String INTEGRATION = PrismMetricsSink.SPRING_AI_INTEGRATION;
  private static final String EXFILTRATION_BLOCKED_COUNTER_NAME =
      "gen_ai.prism.protection.exfiltration.blocked";
  private static final String VAULT_ERRORS_COUNTER_NAME = "gen_ai.prism.protection.vault.errors";

  private final PrismTextScanner scanner;
  private final int order;
  private final ActiveDefenseConfig activeDefenseConfig;
  private final Counter exfiltrationBlockedCounter;
  private final Counter vaultErrorsCounter;

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
    this(
        rulePacks,
        vault,
        observationRegistry,
        PrismMetricsSink.NOOP,
        false,
        DEFAULT_ORDER,
        ActiveDefenseConfig.DISABLED,
        null);
  }

  /** Creates a new advisor with the supplied rule packs, vault, metrics sink, and default order. */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink) {
    this(
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        false,
        DEFAULT_ORDER,
        ActiveDefenseConfig.DISABLED,
        null);
  }

  /** Creates a new advisor with the supplied rule packs, vault, metrics sink, and strict mode. */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode) {
    this(
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        strictMode,
        DEFAULT_ORDER,
        ActiveDefenseConfig.DISABLED,
        null);
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
    this(
        rulePacks,
        vault,
        observationRegistry,
        PrismMetricsSink.NOOP,
        false,
        order,
        ActiveDefenseConfig.DISABLED,
        null);
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
      boolean strictMode,
      int order) {
    this(
        rulePacks,
        vault,
        observationRegistry,
        metricsSink,
        strictMode,
        order,
        ActiveDefenseConfig.DISABLED,
        null);
  }

  /**
   * Full constructor exposing the Active Defense configuration and the Micrometer meter registry.
   *
   * <p>The starter auto-configuration wires this constructor so the {@code
   * spring.prism.active-defense.max-restored-tokens-per-response} property is honoured and the
   * exfiltration counter is registered against the application registry. Hand-wired applications
   * may also call this constructor directly to opt into Active Defense without going through the
   * starter.
   *
   * @param rulePacks The list of active PII rule packs to scan with.
   * @param vault The vault used to store and retrieve token mappings.
   * @param observationRegistry Micrometer observation registry for emitting redaction metrics.
   * @param metricsSink The metrics sink used to record detokenization and detection telemetry.
   * @param strictMode When {@code true}, escalates failures to Fail-Closed.
   * @param order The position of this advisor in the advisor chain.
   * @param activeDefenseConfig The Active Defense configuration. Use {@link
   *     ActiveDefenseConfig#DISABLED} to opt out.
   * @param meterRegistry Optional Micrometer registry. When {@code null}, {@link
   *     Metrics#globalRegistry} is used.
   * @since 1.2.0
   */
  public PrismChatClientAdvisor(
      @NonNull List<PrismRulePack> rulePacks,
      @NonNull PrismVault vault,
      @NonNull ObservationRegistry observationRegistry,
      @NonNull PrismMetricsSink metricsSink,
      boolean strictMode,
      int order,
      @NonNull ActiveDefenseConfig activeDefenseConfig,
      @Nullable MeterRegistry meterRegistry) {
    Objects.requireNonNull(activeDefenseConfig, "activeDefenseConfig");
    this.scanner =
        new PrismTextScanner(rulePacks, vault, observationRegistry, metricsSink, strictMode);
    this.order = order;
    this.activeDefenseConfig = activeDefenseConfig;
    MeterRegistry effectiveRegistry =
        meterRegistry != null ? meterRegistry : Metrics.globalRegistry;
    this.exfiltrationBlockedCounter =
        Counter.builder(EXFILTRATION_BLOCKED_COUNTER_NAME)
            .tag("integration", INTEGRATION)
            .description(
                "Number of LLM responses or streams blocked because the Active Defense restored"
                    + " token ceiling was exceeded.")
            .register(effectiveRegistry);
    this.vaultErrorsCounter =
        Counter.builder(VAULT_ERRORS_COUNTER_NAME)
            .tag("integration", INTEGRATION)
            .description(
                "Number of vault errors encountered during streaming detokenization. In FAIL_SAFE"
                    + " mode the stream continues with the unrestored chunk; in FAIL_CLOSED mode"
                    + " the subscription is terminated with a PrismProtectionException.")
            .register(effectiveRegistry);
  }

  // -----------------------------------------------------------------------
  // Synchronous interception
  // -----------------------------------------------------------------------

  @Override
  public @NonNull AdvisedResponse aroundCall(
      @NonNull AdvisedRequest advisedRequest, @NonNull CallAroundAdvisorChain chain) {

    AdvisedRequest sanitized = tokenizeRequest(advisedRequest);
    AdvisedResponse response = Objects.requireNonNull(chain.nextAroundCall(sanitized));
    return detokenizeResponse(response);
  }

  // -----------------------------------------------------------------------
  // Streaming interception
  // -----------------------------------------------------------------------

  @Override
  public @NonNull Flux<AdvisedResponse> aroundStream(
      @NonNull AdvisedRequest advisedRequest, @NonNull StreamAroundAdvisorChain chain) {

    AdvisedRequest sanitized = tokenizeRequest(advisedRequest);

    // Flux.defer ensures each subscriber gets its own StreamingBuffer + ExfiltrationGuard pair, so
    // counts never bleed across concurrent streams (critical under Virtual Threads / reactive
    // re-subscription).
    return Flux.defer(
        () -> {
          StreamingBuffer buffer = new StreamingBuffer();
          ExfiltrationGuard guard = new ExfiltrationGuard(activeDefenseConfig.maxRestoredTokens());

          Flux<AdvisedResponse> body =
              chain
                  .nextAroundStream(sanitized)
                  .concatMap(
                      advisedResponse -> {
                        String rawChunk = extractText(advisedResponse);
                        String safeChunk = buffer.processChunk(rawChunk);
                        SafeDetokenize attempt = safeDetokenize(safeChunk);
                        if (attempt.vaultError() != null) {
                          // FAIL_CLOSED → propagate; FAIL_SAFE → emit unmodified safeChunk.
                          if (scanner.failureMode()
                              == io.github.catalin87.prism.core.PrismFailureMode.FAIL_CLOSED) {
                            return Flux.error(attempt.vaultError());
                          }
                          return Flux.just(rebuildWithText(advisedResponse, safeChunk));
                        }
                        PrismTextScanner.DetokenizeResult result = attempt.result();
                        if (guard.recordAndCheck(result.restoredCount())) {
                          return Flux.error(exfiltrationException(guard));
                        }
                        return Flux.just(rebuildWithText(advisedResponse, result.text()));
                      });

          Flux<AdvisedResponse> tail =
              Flux.defer(
                  () -> {
                    String pending = buffer.flush();
                    SafeDetokenize attempt = safeDetokenize(pending);
                    if (attempt.vaultError() != null) {
                      if (scanner.failureMode()
                          == io.github.catalin87.prism.core.PrismFailureMode.FAIL_CLOSED) {
                        return Flux.error(attempt.vaultError());
                      }
                      if (pending.isEmpty()) {
                        return Flux.empty();
                      }
                      return Flux.just(syntheticResponse(pending));
                    }
                    PrismTextScanner.DetokenizeResult tailResult = attempt.result();
                    if (guard.recordAndCheck(tailResult.restoredCount())) {
                      return Flux.error(exfiltrationException(guard));
                    }
                    if (tailResult.text().isEmpty()) {
                      return Flux.empty();
                    }
                    return Flux.just(syntheticResponse(tailResult.text()));
                  });

          return body.concatWith(tail);
        });
  }

  /**
   * Runs {@link PrismTextScanner#detokenizeCounted(String)} and turns vault failures into a
   * structured outcome instead of letting them tear down the stream. {@link
   * PrismProtectionException}s raised in FAIL_CLOSED mode and any other vault {@link
   * RuntimeException} are captured, counted on {@code gen_ai.prism.protection.vault.errors}, logged
   * at warn level (no chunk content, only the exception class and message), and surfaced so the
   * caller can decide whether to propagate or substitute the raw chunk based on {@link
   * PrismTextScanner#failureMode()}.
   */
  private @NonNull SafeDetokenize safeDetokenize(@NonNull String chunk) {
    try {
      return new SafeDetokenize(scanner.detokenizeCounted(chunk), null);
    } catch (RuntimeException vaultError) {
      vaultErrorsCounter.increment();
      LOG.warn(
          "Spring Prism vault error during streaming detokenization [{}]: {} ({})",
          scanner.failureMode(),
          vaultError.getClass().getSimpleName(),
          vaultError.getMessage());
      return new SafeDetokenize(null, vaultError);
    }
  }

  /**
   * Tagged union of either a successful detokenization or the vault {@link RuntimeException} that
   * derailed it. Exactly one field is non-null; the {@code aroundStream} pipeline branches on
   * which.
   */
  private record SafeDetokenize(
      @Nullable PrismTextScanner.DetokenizeResult result, @Nullable RuntimeException vaultError) {}

  // -----------------------------------------------------------------------
  // Advisor metadata
  // -----------------------------------------------------------------------

  @Override
  public @NonNull String getName() {
    return Objects.requireNonNull(PrismChatClientAdvisor.class.getSimpleName());
  }

  @Override
  public int getOrder() {
    return order;
  }

  // -----------------------------------------------------------------------
  // Private helpers
  // -----------------------------------------------------------------------

  /** Scans user and system text fields in the request and replaces PII with vault tokens. */
  private @NonNull AdvisedRequest tokenizeRequest(@NonNull AdvisedRequest original) {
    String sanitizedUserText = scanner.tokenize(original.userText());
    String sanitizedSystemText = scanner.tokenize(original.systemText());

    return Objects.requireNonNull(
        AdvisedRequest.from(original)
            .userText(sanitizedUserText)
            .systemText(sanitizedSystemText)
            .build());
  }

  /** Restores vault tokens found in the synchronous response back to their original values. */
  private @NonNull AdvisedResponse detokenizeResponse(@NonNull AdvisedResponse original) {
    String raw = extractText(original);
    PrismTextScanner.DetokenizeResult result = scanner.detokenizeCounted(raw);
    ExfiltrationGuard guard = new ExfiltrationGuard(activeDefenseConfig.maxRestoredTokens());
    if (guard.recordAndCheck(result.restoredCount())) {
      throw exfiltrationException(guard);
    }
    return rebuildWithText(original, result.text());
  }

  /**
   * Builds the protection exception thrown when the Active Defense ceiling is breached. The
   * Micrometer counter is incremented as a side-effect so every throw site is metered uniformly.
   */
  private @NonNull PrismProtectionException exfiltrationException(
      @NonNull ExfiltrationGuard guard) {
    exfiltrationBlockedCounter.increment();
    return new PrismProtectionException(
        PrismProtectionPhase.DETOKENIZE,
        PrismProtectionReason.EXFILTRATION_THRESHOLD_EXCEEDED,
        INTEGRATION,
        scanner.failureMode(),
        guard.thresholdMessage());
  }

  /** Extracts the plain-text content from the first generation in the response, if available. */
  private static @NonNull String extractText(@NonNull AdvisedResponse response) {
    ChatResponse chatResponse = response.response();
    if (chatResponse == null) {
      return "";
    }
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
  private static @NonNull AdvisedResponse rebuildWithText(
      @NonNull AdvisedResponse original, @NonNull String newText) {
    ChatResponse oldChat = original.response();
    if (oldChat == null) {
      return original;
    }
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
    Map<String, Object> adviseContext = Objects.requireNonNull(original.adviseContext());
    return new AdvisedResponse(newChat, adviseContext);
  }

  /**
   * Creates a minimal synthetic {@link AdvisedResponse} carrying only a text payload (for flush).
   */
  private static @NonNull AdvisedResponse syntheticResponse(@NonNull String text) {
    List<Generation> gens = List.of(new Generation(new AssistantMessage(text)));
    ChatResponse chat = new ChatResponse(gens);
    return new AdvisedResponse(chat, java.util.Map.of());
  }

  /**
   * Configuration for the Active Defense exfiltration ceiling. A value of {@link
   * ExfiltrationGuard#UNLIMITED} disables enforcement; positive values cap restored tokens per
   * synchronous response or per streaming subscription.
   *
   * @param maxRestoredTokens The ceiling (or {@link ExfiltrationGuard#UNLIMITED} to disable).
   * @since 1.2.0
   */
  public record ActiveDefenseConfig(int maxRestoredTokens) {

    /** Configuration that disables Active Defense exfiltration enforcement entirely. */
    public static final ActiveDefenseConfig DISABLED =
        new ActiveDefenseConfig(ExfiltrationGuard.UNLIMITED);

    /**
     * Compact constructor that rejects values below {@link ExfiltrationGuard#UNLIMITED} so the
     * advisor never silently demotes misconfiguration into an unsafe state.
     */
    public ActiveDefenseConfig {
      if (maxRestoredTokens < ExfiltrationGuard.UNLIMITED) {
        throw new IllegalArgumentException(
            "maxRestoredTokens must be >= -1 (use ExfiltrationGuard.UNLIMITED to disable); got "
                + maxRestoredTokens);
      }
    }
  }
}
