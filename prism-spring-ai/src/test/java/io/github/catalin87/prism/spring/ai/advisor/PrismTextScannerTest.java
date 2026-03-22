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

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
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
    scanner = new PrismTextScanner(packs, vault, ObservationRegistry.NOOP);
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
}
