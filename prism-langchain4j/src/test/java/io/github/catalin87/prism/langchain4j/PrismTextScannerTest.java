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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
import io.github.catalin87.prism.core.PrismFailureMode;
import io.github.catalin87.prism.core.PrismProtectionException;
import io.github.catalin87.prism.core.PrismProtectionPhase;
import io.github.catalin87.prism.core.PrismProtectionReason;
import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrismTextScannerTest {

  private static final List<PrismRulePack> RULE_PACKS = List.of(new UniversalRulePack());

  @Test
  void tokenizesAndRestoresLargePromptInSinglePass() {
    PrismTextScanner scanner = newScanner();
    String prompt = largePrompt();

    String sanitized = scanner.tokenize(prompt);

    assertThat(sanitized)
        .contains("<PRISM_EMAIL_", "<PRISM_SSN_", "<PRISM_CREDIT_CARD_")
        .doesNotContain("rag-user-00@example.com", "123-45-6700", "4111 1111 1111 0006");

    String restored = scanner.detokenize(sanitized);
    assertThat(restored).isEqualTo(prompt);
  }

  @Test
  void returnsOriginalInstanceWhenNoTokenPrefixExists() {
    PrismTextScanner scanner = newScanner();
    String text = "A large LangChain4j prompt with no vault markers at all.";

    assertThat(scanner.detokenize(text)).isSameAs(text);
  }

  @Test
  void tokenizationCachesRepeatedValuesWithinSinglePrompt() {
    PrismVault vault = mock(PrismVault.class);
    when(vault.tokenize("same@example.com", "EMAIL"))
        .thenReturn(new PrismToken("<PRISM_EMAIL_REPEAT>", "same@example.com", "EMAIL"));
    PrismTextScanner scanner =
        new PrismTextScanner(
            List.of(new RepeatedEmailRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            false);

    String sanitized = scanner.tokenize("same@example.com then same@example.com");

    assertThat(sanitized).isEqualTo("<PRISM_EMAIL_REPEAT> then <PRISM_EMAIL_REPEAT>");
    verify(vault, times(1)).tokenize("same@example.com", "EMAIL");
  }

  @Test
  void detokenizationCachesRepeatedTokensWithinSingleResponse() {
    PrismVault vault = mock(PrismVault.class);
    when(vault.detokenize("<PRISM_EMAIL_REPEAT>")).thenReturn("same@example.com");
    PrismTextScanner scanner =
        new PrismTextScanner(
            RULE_PACKS, vault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP, false);

    String restored = scanner.detokenize("<PRISM_EMAIL_REPEAT> then <PRISM_EMAIL_REPEAT> again");

    assertThat(restored).isEqualTo("same@example.com then same@example.com again");
    verify(vault, times(1)).detokenize("<PRISM_EMAIL_REPEAT>");
  }

  @Test
  void tokenizesEmailCrossingSegmentBoundaryInLargePrompt() {
    PrismTextScanner scanner = newScanner();
    String prompt = boundaryPrompt();

    String sanitized = scanner.tokenize(prompt);

    assertThat(sanitized).contains("<PRISM_EMAIL_").doesNotContain("edge@example.com");
    assertThat(scanner.detokenize(sanitized)).isEqualTo(prompt);
  }

  @Test
  void failClosedBlocksWhenDetectorThrows() {
    PrismTextScanner scanner =
        new PrismTextScanner(
            List.of(new FailingRulePack()),
            new DefaultPrismVault(
                new HmacSha256TokenGenerator(),
                "benchmark-secret-material-32bytes".getBytes(StandardCharsets.UTF_8),
                3600L),
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            PrismFailureMode.FAIL_CLOSED);

    assertThatThrownBy(() -> scanner.tokenize("user@example.com"))
        .isInstanceOf(PrismProtectionException.class)
        .extracting(
            failure -> ((PrismProtectionException) failure).phase(),
            failure -> ((PrismProtectionException) failure).reason(),
            failure -> ((PrismProtectionException) failure).failureMode())
        .containsExactly(
            PrismProtectionPhase.DETECT,
            PrismProtectionReason.DETECTOR_FAILURE,
            PrismFailureMode.FAIL_CLOSED);
  }

  private static PrismTextScanner newScanner() {
    TokenGenerator tokenGenerator = new HmacSha256TokenGenerator();
    DefaultPrismVault vault =
        new DefaultPrismVault(
            tokenGenerator,
            "benchmark-secret-material-32bytes".getBytes(StandardCharsets.UTF_8),
            3600L);
    return new PrismTextScanner(
        RULE_PACKS, vault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP, false);
  }

  private static String largePrompt() {
    StringBuilder builder = new StringBuilder(4096);
    for (int index = 0; index < 80; index++) {
      builder
          .append("Chunk ")
          .append(index)
          .append(": customer rag-user-")
          .append(String.format("%02d", index))
          .append("@example.com with SSN 123-45-")
          .append(String.format("%04d", 6700 + index))
          .append(" and card 4111 1111 1111 ")
          .append(String.format("%04d", index))
          .append(". ");
    }
    return builder.toString();
  }

  private static String boundaryPrompt() {
    StringBuilder builder = new StringBuilder(10_000);
    builder.append("A".repeat(4_090)).append("edge@example.com");
    while (builder.length() < 9_500) {
      builder.append(" filler text without pii ");
    }
    return builder.toString();
  }

  private static final class RepeatedEmailRulePack implements PrismRulePack {

    @Override
    public String getName() {
      return "TEST";
    }

    @Override
    public List<PiiDetector> getDetectors() {
      return List.of(new RepeatedEmailDetector());
    }
  }

  private static final class RepeatedEmailDetector implements PiiDetector {

    @Override
    public String getEntityType() {
      return "EMAIL";
    }

    @Override
    public List<PiiCandidate> detect(String text) {
      return List.of(
          new PiiCandidate("same@example.com", 0, 16, "EMAIL"),
          new PiiCandidate("same@example.com", 22, 38, "EMAIL"));
    }
  }

  private static final class FailingRulePack implements PrismRulePack {

    @Override
    public String getName() {
      return "FAILING";
    }

    @Override
    public List<PiiDetector> getDetectors() {
      return List.of(new FailingDetector());
    }
  }

  private static final class FailingDetector implements PiiDetector {

    @Override
    public String getEntityType() {
      return "EMAIL";
    }

    @Override
    public List<PiiCandidate> detect(String text) {
      throw new IllegalStateException("boom");
    }
  }
}
