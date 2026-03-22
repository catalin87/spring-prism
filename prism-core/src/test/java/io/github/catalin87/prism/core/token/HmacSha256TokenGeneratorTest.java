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
package io.github.catalin87.prism.core.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.TokenGenerator;
import org.junit.jupiter.api.Test;

class HmacSha256TokenGeneratorTest {

  private final byte[] sharedSecretKey = "super-secret-mathematical-key-32-byte".getBytes();
  private final TokenGenerator generator = new HmacSha256TokenGenerator();

  @Test
  void testDeterministicSignatureSucceeds() {
    PiiCandidate candidate = new PiiCandidate("user@corp.local", 10, 25, "EMAIL");

    PrismToken token1 = generator.generate(candidate, sharedSecretKey);
    PrismToken token2 = generator.generate(candidate, sharedSecretKey);

    assertNotNull(token1.hmacSignature());
    assertNotNull(token1.key());
    assertEquals(
        token1.key(),
        token2.key(),
        "Token prefix and footprint must be universally deterministic.");
    assertEquals(
        token1.hmacSignature(),
        token2.hmacSignature(),
        "HMAC payload must be mathematically equivalent.");
  }

  @Test
  void testDistinctKeysGenerateDistinctSignatures() {
    PiiCandidate candidate = new PiiCandidate("user@corp.local", 10, 25, "EMAIL");
    byte[] alternativeSecretKey = "different-secret-mathematical-key-byte".getBytes();

    PrismToken token1 = generator.generate(candidate, sharedSecretKey);
    PrismToken token2 = generator.generate(candidate, alternativeSecretKey);

    assertNotEquals(
        token1.key(), token2.key(), "Tokens using vastly different keys must branch securely.");
    assertNotEquals(
        token1.hmacSignature(),
        token2.hmacSignature(),
        "Signatures utilizing different secrets must differ.");
  }
}
