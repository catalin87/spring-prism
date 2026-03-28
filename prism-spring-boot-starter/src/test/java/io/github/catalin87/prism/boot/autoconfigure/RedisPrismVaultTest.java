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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
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

  @Test
  void sharedRedisStoreAllowsCrossNodeRestoration() {
    InMemoryStringRedisTemplate sharedRedis = new InMemoryStringRedisTemplate();
    RedisPrismVault writerVault =
        new RedisPrismVault(
            sharedRedis, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));
    RedisPrismVault readerVault =
        new RedisPrismVault(
            sharedRedis, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    PrismToken token = writerVault.tokenize("user@corp.local", "EMAIL");

    assertThat(readerVault.detokenize(token.key())).isEqualTo("user@corp.local");
  }

  @Test
  void sharedRedisStoreRejectsRestoreAcrossNodesWhenSecretsDiffer() {
    InMemoryStringRedisTemplate sharedRedis = new InMemoryStringRedisTemplate();
    RedisPrismVault writerVault =
        new RedisPrismVault(
            sharedRedis, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));
    RedisPrismVault readerVault =
        new RedisPrismVault(
            sharedRedis,
            new HmacSha256TokenGenerator(),
            "other-secret".getBytes(StandardCharsets.UTF_8),
            Duration.ofMinutes(5));

    PrismToken token = writerVault.tokenize("user@corp.local", "EMAIL");

    assertThat(readerVault.detokenize(token.key())).isNull();
  }

  @Test
  void verifyAvailabilityUsesAsyncNativeConnectionWithTimeout() throws Exception {
    Future<String> pingFuture = mock(Future.class);
    AsyncPingConnection asyncCommands = new AsyncPingConnection(pingFuture);
    when(pingFuture.get(50L, TimeUnit.MILLISECONDS)).thenReturn("PONG");
    when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any()))
        .thenAnswer(
            invocation -> {
              RedisCallback<String> callback = invocation.getArgument(0);
              RedisConnection connection = mock(RedisConnection.class);
              when(connection.getNativeConnection()).thenReturn(asyncCommands);
              return callback.doInRedis(connection);
            });

    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    vault.verifyAvailability(Duration.ofMillis(50));

    assertThat(asyncCommands.pingInvoked()).isTrue();
    verify(pingFuture).get(50L, TimeUnit.MILLISECONDS);
  }

  @Test
  void verifyAvailabilityCancelsAsyncPingWhenTimeoutIsExceeded() throws Exception {
    Future<String> pingFuture = mock(Future.class);
    AsyncPingConnection asyncCommands = new AsyncPingConnection(pingFuture);
    when(pingFuture.get(50L, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException("late"));
    when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisCallback<String>>any()))
        .thenAnswer(
            invocation -> {
              RedisCallback<String> callback = invocation.getArgument(0);
              RedisConnection connection = mock(RedisConnection.class);
              when(connection.getNativeConnection()).thenReturn(asyncCommands);
              return callback.doInRedis(connection);
            });

    RedisPrismVault vault =
        new RedisPrismVault(
            redisTemplate, new HmacSha256TokenGenerator(), secretKey, Duration.ofMinutes(5));

    assertThatThrownBy(() -> vault.verifyAvailability(Duration.ofMillis(50)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Redis availability check failed");
    verify(pingFuture).cancel(true);
  }

  private static final class AsyncPingConnection {

    private final Future<String> pingFuture;
    private boolean pingInvoked;

    private AsyncPingConnection(Future<String> pingFuture) {
      this.pingFuture = pingFuture;
    }

    public Future<String> ping() {
      pingInvoked = true;
      return pingFuture;
    }

    private boolean pingInvoked() {
      return pingInvoked;
    }
  }

  private static final class InMemoryStringRedisTemplate extends StringRedisTemplate {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final ValueOperations<String, String> valueOperations = createValueOperations();

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> createValueOperations() {
      return (ValueOperations<String, String>)
          Proxy.newProxyInstance(
              ValueOperations.class.getClassLoader(),
              new Class<?>[] {ValueOperations.class},
              (proxy, method, args) -> {
                String methodName = method.getName();
                if ("set".equals(methodName)) {
                  store.put((String) args[0], (String) args[1]);
                  return null;
                }
                if ("get".equals(methodName)) {
                  return store.get(args[0]);
                }
                throw new UnsupportedOperationException(methodName);
              });
    }

    @Override
    public ValueOperations<String, String> opsForValue() {
      return valueOperations;
    }

    @Override
    public Boolean delete(String key) {
      return store.remove(key) != null;
    }
  }
}
