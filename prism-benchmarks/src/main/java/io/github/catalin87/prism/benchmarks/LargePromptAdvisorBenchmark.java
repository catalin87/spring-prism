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
package io.github.catalin87.prism.benchmarks;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.github.catalin87.prism.rulepack.common.CommonRulePack;
import io.github.catalin87.prism.rulepack.de.GermanyRulePack;
import io.github.catalin87.prism.rulepack.fr.FranceRulePack;
import io.github.catalin87.prism.rulepack.gb.UnitedKingdomRulePack;
import io.github.catalin87.prism.rulepack.nl.NetherlandsRulePack;
import io.github.catalin87.prism.rulepack.pl.PolandRulePack;
import io.github.catalin87.prism.rulepack.ro.RomaniaRulePack;
import io.github.catalin87.prism.rulepack.us.UsRulePack;
import io.github.catalin87.prism.spring.ai.advisor.PrismChatClientAdvisor;
import io.github.catalin87.prism.spring.ai.advisor.PrismMetricsSink;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/** Measures end-to-end large-prompt tokenization and restore through the Spring AI advisor path. */
@State(Scope.Benchmark)
public class LargePromptAdvisorBenchmark {

  private static final ChatModel BENCHMARK_CHAT_MODEL =
      prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("ACK"))));

  @Param({"COMMON", "BIG7"})
  public String profile;

  private PrismChatClientAdvisor advisor;
  private AdvisedRequest tokenizeRequest;
  private AdvisedRequest restoreRequest;
  private CallAroundAdvisorChain sanitizedEchoChain;

  /** Prepares a realistic large RAG-style prompt and a simple echo chain for advisor benchmarks. */
  @Setup
  public void setUp() {
    TokenGenerator tokenGenerator = new HmacSha256TokenGenerator();
    DefaultPrismVault vault =
        new DefaultPrismVault(
            tokenGenerator,
            "benchmark-secret-material-32bytes".getBytes(StandardCharsets.UTF_8),
            3600L);
    advisor =
        new PrismChatClientAdvisor(
            rulePacksForProfile(), vault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP, false);
    tokenizeRequest = baseRequest(largePrompt());
    restoreRequest = baseRequest("restore-large-rag-payload");
    sanitizedEchoChain =
        sanitizedRequest ->
            new AdvisedResponse(
                new ChatResponse(
                    List.of(new Generation(new AssistantMessage(sanitizedRequest.userText())))),
                Map.of());
  }

  /**
   * Measures outbound tokenization cost for a large prompt when the model returns a small reply.
   */
  @Benchmark
  public AdvisedResponse tokenizeLargePrompt() {
    return advisor.aroundCall(
        tokenizeRequest,
        sanitizedRequest ->
            new AdvisedResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("ACK")))), Map.of()));
  }

  /**
   * Measures the combined tokenize-and-restore path for a large prompt echoed back by the model.
   */
  @Benchmark
  public AdvisedResponse tokenizeAndRestoreLargePrompt() {
    return advisor.aroundCall(restoreRequest, sanitizedEchoChain);
  }

  private static AdvisedRequest baseRequest(String userText) {
    return AdvisedRequest.builder()
        .chatModel(BENCHMARK_CHAT_MODEL)
        .userText(userText)
        .systemText("")
        .build();
  }

  private List<PrismRulePack> rulePacksForProfile() {
    if ("BIG7".equals(profile)) {
      return List.of(
          new CommonRulePack(),
          new RomaniaRulePack(),
          new UsRulePack(),
          new PolandRulePack(),
          new NetherlandsRulePack(),
          new UnitedKingdomRulePack(),
          new FranceRulePack(),
          new GermanyRulePack());
    }
    return List.of(new CommonRulePack());
  }

  private String largePrompt() {
    StringBuilder builder = new StringBuilder(16384);
    for (int index = 0; index < 120; index++) {
      builder
          .append("Retrieved document ")
          .append(index)
          .append(" for profile ")
          .append(profile)
          .append(": contact rag-user-")
          .append(String.format("%03d", index))
          .append("@example.com, SSN 123-45-")
          .append(String.format("%04d", 6700 + index))
          .append(", card 401200003333")
          .append(String.format("%04d", index))
          .append(", phone +40 712 345 ")
          .append(String.format("%03d", index))
          .append(", CNP 1751015412728")
          .append(", DE75512108001245126199")
          .append(", FR1420041010050500013M02606")
          .append(", GB29NWBK60161331926819")
          .append(", NL91ABNA0417164300")
          .append(", PL61109010140000071219812874")
          .append(", RO49AAAA1B31007593840000")
          .append(", EIN 12-3456789")
          .append(", ABA 021000021")
          .append(", SIREN 732829320")
          .append(". ");
    }
    return builder.toString();
  }
}
