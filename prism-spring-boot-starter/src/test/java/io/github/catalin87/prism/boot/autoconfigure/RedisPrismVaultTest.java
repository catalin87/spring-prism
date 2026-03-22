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
package io.github.catalin87.prism.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Focused tests for Redis-backed Prism vault behavior. */
class RedisPrismVaultTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

  private final byte[] secretKey = "redis-secret".getBytes(StandardCharsets.UTF_8);

  RedisPrismVaultTest() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  void tokenizeStoresValueUsingConfiguredTtl() {
    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");

    verify(valueOperations).set(token.key(), "user@corp.local", Duration.ofMinutes(5));
  }

  @Test
  void invalidTtlFallsBackToOneSecond() {
    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ZERO);

    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");

    verify(valueOperations).set(token.key(), "user@corp.local", Duration.ofSeconds(1));
  }

  @Test
  void detokenizeRejectsMalformedTokenWithoutRedisLookup() {
    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    assertThat(vault.detokenize("EMAIL_123")).isNull();
    verify(valueOperations, never()).get(any());
  }

  @Test
  void detokenizeRestoresStoredValueWhenSignatureMatches() {
    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");
    when(valueOperations.get(token.key())).thenReturn("user@corp.local");

    assertThat(vault.detokenize(token.key())).isEqualTo("user@corp.local");
    verify(redisTemplate, never()).delete(token.key());
  }

  @Test
  void detokenizeDeletesTamperedRedisValue() {
    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    PrismToken token = vault.tokenize("user@corp.local", "EMAIL");
    when(valueOperations.get(token.key())).thenReturn("other@corp.local");

    assertThat(vault.detokenize(token.key())).isNull();
    verify(redisTemplate).delete(eq(token.key()));
  }
}
