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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.jspecify.annotations.NonNull;

/**
 * A thread-safe cryptographic engine for generating deterministic pseudonymization tokens.
 * Optimized for Java 21 Virtual Threads by avoiding synchronized locks or ThreadLocals,
 * instantiating a fresh lightweight `Mac` on every concurrent execution.
 */
public class HmacSha256TokenGenerator {

  private static final String ALGORITHM = "HmacSHA256";
  private final SecretKey secretKey;

  public HmacSha256TokenGenerator(@NonNull SecretKey secretKey) {
    if (!ALGORITHM.equals(secretKey.getAlgorithm())) {
      throw new IllegalArgumentException("SecretKey must execute the HmacSHA256 algorithm.");
    }
    this.secretKey = secretKey;
  }

  /**
   * Generates an immutable PrismToken containing a deterministic HMAC-SHA256 signature.
   *
   * @param originalValue The sensitive raw data to pseudonymize.
   * @param label The semantic entity label representing the PII type (e.g. "EMAIL", "IBAN").
   * @return the cryptographic PrismToken record protecting this data space.
   */
  @NonNull
  public PrismToken generate(@NonNull String originalValue, @NonNull String label) {
    try {
      // Mac instances are NOT thread-safe. To support millions of Virtual Threads concurrently
      // without context-switching constraints from 'synchronized' locks or large heap allocations
      // via ThreadLocal arrays, we initialize local instances dynamically.
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(secretKey);

      byte[] hashBytes = mac.doFinal(originalValue.getBytes(StandardCharsets.UTF_8));

      // Generate a URL-safe Base64 signature to prevent whitespace or escape issues
      String hmacSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);

      // Extract an 8-character deterministic suffix from the start of the signature footprint
      String hashSuffix = hmacSignature.substring(0, Math.min(8, hmacSignature.length()));

      // Render LLM-compatible contextual prompt key replacing the content
      String tokenKey = String.format("<PRISM_%s_%s>", label.toUpperCase(), hashSuffix);

      return new PrismToken(tokenKey, originalValue, hmacSignature);

    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(
          "System encountered an irrecoverable cryptographic context failure.", e);
    }
  }
}
