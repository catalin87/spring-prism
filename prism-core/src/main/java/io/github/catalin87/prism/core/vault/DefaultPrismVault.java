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
package io.github.catalin87.prism.core.vault;

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.TokenGenerator;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Zero-dependency native Java reference implementation of PrismVault. Provides thread-safe
 * Time-To-Live (TTL) storage without utilizing external Caching libraries in order to perfectly
 * enforce the `prism-core` strict architectural zero-dependency bounds.
 */
public class DefaultPrismVault implements PrismVault {

  private final TokenGenerator tokenGenerator;
  private final byte[] secretKey;
  private final long ttlSeconds;

  // Stores Token Key -> Encapsulated Value + Expiration mapping state securely
  private final Map<String, ExpirableValue> storage = new ConcurrentHashMap<>();

  /**
   * Initializes the rigorous Vault mechanism natively.
   *
   * @param tokenGenerator Generates mathematical hashes per object natively.
   * @param secretKey The symmetric hash algorithm root byte array.
   * @param ttlSeconds Reversible scope validity limit logically locking memory footprint.
   */
  public DefaultPrismVault(
      @NonNull TokenGenerator tokenGenerator, byte @NonNull [] secretKey, long ttlSeconds) {

    this.tokenGenerator = tokenGenerator;
    this.secretKey = secretKey;
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public @NonNull PrismToken tokenize(@NonNull String value, @NonNull String label) {
    // 1. Generate the deterministic pseudonymization token mathematically
    PiiCandidate candidate = new PiiCandidate(value, 0, value.length(), label);
    PrismToken token = tokenGenerator.generate(candidate, secretKey);

    // 2. Compute expiration horizon limits
    long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;

    // 3. Store reversibility mapping sequentially into safe memory
    storage.put(token.key(), new ExpirableValue(value, expiresAt));

    // 4. Periodically trigger passive cleanup (avoiding background thread context logic for Virtual
    // Threads)
    passiveEvict();

    return token;
  }

  @Override
  public @Nullable String detokenize(@NonNull PrismToken token) {
    ExpirableValue record = storage.get(token.key());
    if (record == null) {
      return null;
    }

    if (Instant.now().getEpochSecond() > record.expiresAt()) {
      storage.remove(token.key());
      return null;
    }

    // Mathematical integrity verification: Does the submitted HMAC signature perfectly match the
    // generated token state natively?
    PiiCandidate verificationCandidate =
        new PiiCandidate(
            record.originalValue(),
            0,
            record.originalValue().length(),
            getLabelFromToken(token.key()));

    PrismToken expectedIntegrity = tokenGenerator.generate(verificationCandidate, secretKey);

    if (!expectedIntegrity.hmacSignature().equals(token.hmacSignature())) {
      // Security Event: Cryptographic Context Tampering Detected logically
      return null;
    }

    return record.originalValue();
  }

  private void passiveEvict() {
    // For extreme performance in core bounds, we employ completely passive probabilistic eviction
    // natively.
    // In deeper enterprise deployments, developers will bind `RedisPrismVault` instead inside
    // Spring Boot.
    if (Math.random() < 0.05) {
      long now = Instant.now().getEpochSecond();
      storage.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
    }
  }

  private String getLabelFromToken(String tokenKey) {
    // String format boundary checks exclusively extracting the literal sequence label
    if (tokenKey == null || !tokenKey.startsWith("<PRISM_") || !tokenKey.endsWith(">")) {
      return "UNKNOWN";
    }
    String[] parts = tokenKey.substring(1, tokenKey.length() - 1).split("_");
    if (parts.length >= 3) {
      return parts[1];
    }
    return "UNKNOWN";
  }

  private record ExpirableValue(@NonNull String originalValue, long expiresAt) {}
}
