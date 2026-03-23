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

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.TokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.NonNull;

/**
 * Strict hashing component isolated locally per thread execution sequence natively without
 * threadlocals.
 */
public final class HmacSha256TokenGenerator implements TokenGenerator {

  private static final String ALGORITHM = "HmacSHA256";

  @Override
  public @NonNull PrismToken generate(@NonNull PiiCandidate candidate, byte @NonNull [] secretKey) {
    try {
      SecretKeySpec keySpec = new SecretKeySpec(secretKey, ALGORITHM);
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(keySpec);

      byte[] digest = mac.doFinal(candidate.text().getBytes(StandardCharsets.UTF_8));
      String signature =
          Objects.requireNonNull(Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
      String suffix = toHex(digest).substring(0, 8);

      String tokenKey =
          Objects.requireNonNull(
              String.format(
                  Locale.ROOT,
                  "<PRISM_%s_%s>",
                  candidate.label().toUpperCase(Locale.ROOT),
                  suffix));

      return new PrismToken(tokenKey, candidate.text(), signature);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Cryptographic algorithm constraint violated natively.", e);
    }
  }

  private static @NonNull String toHex(byte @NonNull [] digest) {
    StringBuilder builder = new StringBuilder(digest.length * 2);
    for (byte value : digest) {
      builder.append(Character.forDigit((value >> 4) & 0xF, 16));
      builder.append(Character.forDigit(value & 0xF, 16));
    }
    return builder.toString().toUpperCase();
  }
}
