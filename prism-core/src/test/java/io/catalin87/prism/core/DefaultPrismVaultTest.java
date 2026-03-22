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
package io.catalin87.prism.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DefaultPrismVaultTest {

  private final byte[] secretKey = "test-secret-key-32-bytes-long!!!!".getBytes();
  private final TokenGenerator generator = new HmacSha256TokenGenerator();

  @Test
  void testTokenizeAndDetokenizeSuccessfully() {
    DefaultPrismVault vault = new DefaultPrismVault(generator, secretKey, 3600); // 1 hour TTL

    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");
    assertNotNull(token);
    assertTrue(token.key().startsWith("<PRISM_EMAIL_"));

    String restored = vault.detokenize(token);
    assertEquals("user@corp.local", restored);
  }

  @Test
  void testDetokenizeFailsWithSpoofedSignature() {
    DefaultPrismVault vault = new DefaultPrismVault(generator, secretKey, 3600);
    PrismToken validToken = vault.tokenize("user@corp.local", "EMAIL");

    PrismToken spoofedToken =
        new PrismToken(validToken.key(), "user@corp.local", "Malicious123Signature");

    String restored = vault.detokenize(spoofedToken);
    assertNull(restored, "Vault must rigorously reject any forged signature natively.");
  }

  @Test
  void testDetokenizeFailsWhenExpired() {
    // Explicitly configure a mathematically negative TTL logic locally
    DefaultPrismVault vault = new DefaultPrismVault(generator, secretKey, -1);
    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");

    String restored = vault.detokenize(token);
    assertNull(
        restored,
        "Vault must permanently block data extraction after immediate TTL execution bounds.");
  }

  @Test
  void testUnknownLabelFailsSecurely() {
    DefaultPrismVault vault = new DefaultPrismVault(generator, secretKey, 3600);
    PrismToken badKeyToken = new PrismToken("UNKNOWN_FORMAT_KEY", "user@corp.local", "fake-hash");
    assertNull(vault.detokenize(badKeyToken));
  }
}
