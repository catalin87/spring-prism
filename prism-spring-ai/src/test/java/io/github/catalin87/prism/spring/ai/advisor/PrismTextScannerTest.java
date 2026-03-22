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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PiiDetector;
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

/** Unit tests for {@link PrismTextScanner}. */
class PrismTextScannerTest {

  private static final byte[] SECRET = "test-key-32-bytes-long-padding!!".getBytes();

  private PrismVault vault;
  private PrismTextScanner scanner;

  @BeforeEach
  void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault = new DefaultPrismVault(generator, SECRET, 3600L);
    List<PrismRulePack> packs = List.of(new UniversalRulePack());
    scanner = new PrismTextScanner(packs, vault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP);
  }

  @Test
  void tokenize_replacesEmailWithVaultToken() {
    String result = scanner.tokenize("Please contact user@example.com for support.");

    assertThat(result).doesNotContain("user@example.com");
    assertThat(result).containsPattern("<PRISM_EMAIL_[A-Za-z0-9_-]+>");
  }

  @Test
  void tokenize_replacesCreditCardWithVaultToken() {
    String result = scanner.tokenize("card: 4111111111111111 was charged.");

    assertThat(result).doesNotContain("4111111111111111");
    assertThat(result).containsPattern("<PRISM_CREDIT_CARD_[A-Za-z0-9_-]+>");
  }

  @Test
  void tokenize_replacesPhoneNumberWithVaultToken() {
    String result = scanner.tokenize("Call +40 712 345 678 for support.");

    assertThat(result).doesNotContain("+40 712 345 678");
    assertThat(result).containsPattern("<PRISM_PHONE_NUMBER_[A-Za-z0-9_-]+>");
  }

  @Test
  void tokenize_noopOnCleanText() {
    String clean = "The weather today is sunny.";
    String result = scanner.tokenize(clean);

    assertThat(result).isEqualTo(clean);
  }

  @Test
  void tokenize_emptyStringReturnsEmpty() {
    assertThat(scanner.tokenize("")).isEmpty();
  }

  @Test
  void detokenize_restoresOriginalEmail() {
    String tokenized = scanner.tokenize("Contact user@example.com please.");

    String restored = scanner.detokenize(tokenized);

    assertThat(restored).contains("user@example.com");
    assertThat(restored).doesNotContainPattern("<PRISM_[A-Z0-9_]+>");
  }

  @Test
  void detokenize_multipleTokensRestoredCorrectly() {
    String input = "From user@corp.com to admin@corp.com, card 4111111111111111";
    String tokenized = scanner.tokenize(input);

    String restored = scanner.detokenize(tokenized);

    assertThat(restored).contains("user@corp.com");
    assertThat(restored).contains("admin@corp.com");
    assertThat(restored).contains("4111111111111111");
  }

  @Test
  void detokenize_unknownTokenLeftAsIs() {
    String withFakeToken = "Hello <PRISM_UNKNOWN_fakeXYZ> world.";
    String result = scanner.detokenize(withFakeToken);

    // Unknown/expired token must be left in place (Fail-Open)
    assertThat(result).contains("<PRISM_UNKNOWN_fakeXYZ>");
  }

  @Test
  void detokenize_emptyStringReturnsEmpty() {
    assertThat(scanner.detokenize("")).isEmpty();
  }

  @Test
  void tokenize_failOpenReturnsOriginalTextWhenDetectorFails() {
    PrismTextScanner failOpenScanner =
        new PrismTextScanner(
            List.of(new FailingRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            false);

    assertThat(failOpenScanner.tokenize("safe input")).isEqualTo("safe input");
  }

  @Test
  void tokenize_strictModeThrowsWhenDetectorFails() {
    PrismTextScanner strictScanner =
        new PrismTextScanner(
            List.of(new FailingRulePack()),
            vault,
            ObservationRegistry.NOOP,
            PrismMetricsSink.NOOP,
            true);

    assertThatThrownBy(() -> strictScanner.tokenize("safe input"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Strict mode blocked Prism processing");
  }

  @Test
  void tokenizeAndDetokenizeRecordTimingMetrics() {
    RecordingMetricsSink metricsSink = new RecordingMetricsSink();
    PrismTextScanner timedScanner =
        new PrismTextScanner(
            List.of(new UniversalRulePack()), vault, ObservationRegistry.NOOP, metricsSink, false);

    String tokenized = timedScanner.tokenize("Contact user@example.com.");
    timedScanner.detokenize(tokenized);

    assertThat(metricsSink.scanDurationCalls).isEqualTo(1);
    assertThat(metricsSink.tokenizeDurationCalls).isEqualTo(1);
    assertThat(metricsSink.detokenizeDurationCalls).isEqualTo(1);
    assertThat(metricsSink.lastIntegration).isEqualTo(PrismMetricsSink.SPRING_AI_INTEGRATION);
    assertThat(metricsSink.lastRecordedNanos).isGreaterThanOrEqualTo(0L);
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
