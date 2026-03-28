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

import io.github.catalin87.prism.core.PiiCandidate;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.PrismVaultAvailability;
import io.github.catalin87.prism.core.TokenGenerator;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed {@link PrismVault} implementation for multi-node deployments. */
final class RedisPrismVault implements PrismVault, PrismVaultAvailability {

  private static final Duration MINIMUM_TTL = Duration.ofSeconds(1);

  private final StringRedisTemplate redisTemplate;
  private final TokenGenerator tokenGenerator;
  private final byte[] secretKey;
  private final Duration ttl;

  RedisPrismVault(
      @NonNull StringRedisTemplate redisTemplate,
      @NonNull TokenGenerator tokenGenerator,
      byte @NonNull [] secretKey,
      @NonNull Duration ttl) {
    this.redisTemplate = redisTemplate;
    this.tokenGenerator = tokenGenerator;
    this.secretKey = secretKey;
    this.ttl = ttl.isZero() || ttl.isNegative() ? MINIMUM_TTL : ttl;
  }

  @Override
  public @NonNull PrismToken tokenize(@NonNull String value, @NonNull String label) {
    PiiCandidate candidate = new PiiCandidate(value, 0, value.length(), label);
    PrismToken token = tokenGenerator.generate(candidate, secretKey);
    redisTemplate.opsForValue().set(token.key(), value, ttl);
    return token;
  }

  @Override
  public @Nullable String detokenize(@NonNull String tokenKey) {
    if (!isPrismToken(tokenKey)) {
      return null;
    }

    String originalValue = redisTemplate.opsForValue().get(tokenKey);
    if (originalValue == null) {
      return null;
    }

    PiiCandidate verificationCandidate =
        new PiiCandidate(originalValue, 0, originalValue.length(), extractLabel(tokenKey));
    PrismToken expected = tokenGenerator.generate(verificationCandidate, secretKey);
    if (!expected.key().equals(tokenKey)) {
      redisTemplate.delete(tokenKey);
      return null;
    }
    return originalValue;
  }

  @Override
  public void verifyAvailability(Duration timeout) {
    long timeoutMillis = Math.max(1L, timeout.toMillis());
    try {
      String result =
          redisTemplate.execute(
              (RedisCallback<String>)
                  connection -> {
                    Object nativeConnection = connection.getNativeConnection();
                    String asyncResult = pingWithTimeout(nativeConnection, timeout);
                    if (asyncResult != null) {
                      return asyncResult;
                    }
                    Object pingResult = pingSynchronouslyWithTimeout(connection, timeoutMillis);
                    return pingResult == null ? null : pingResult.toString();
                  });
      if (result == null || !"PONG".equalsIgnoreCase(result)) {
        throw new IllegalStateException("Redis availability check returned an unexpected result");
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Redis availability check failed", exception);
    }
  }

  private static @Nullable String pingWithTimeout(
      @Nullable Object nativeConnection, Duration timeout) {
    if (nativeConnection == null) {
      return null;
    }
    Method pingMethod;
    try {
      pingMethod = nativeConnection.getClass().getMethod("ping");
    } catch (NoSuchMethodException exception) {
      return null;
    }
    Object pingResult;
    try {
      pingResult = pingMethod.invoke(nativeConnection);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Redis availability check failed", exception);
    }
    if (!(pingResult instanceof Future<?> pingFuture)) {
      return null;
    }
    long timeoutMillis = Math.max(1L, timeout.toMillis());
    try {
      Object resolved = pingFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
      return resolved == null ? null : resolved.toString();
    } catch (TimeoutException exception) {
      pingFuture.cancel(true);
      throw new IllegalStateException("Redis availability check timed out", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Redis availability check interrupted", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Redis availability check failed", exception);
    }
  }

  private static @Nullable Object pingSynchronouslyWithTimeout(
      Object connection, long timeoutMillis) {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    try {
      Future<Object> future = executor.submit(() -> invokeSynchronousPing(connection));
      return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new IllegalStateException("Redis availability check timed out", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Redis availability check interrupted", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      throw new IllegalStateException(
          "Redis availability check failed", cause != null ? cause : exception);
    } finally {
      executor.shutdownNow();
    }
  }

  private static @Nullable Object invokeSynchronousPing(Object connection) {
    try {
      Method pingMethod = connection.getClass().getMethod("ping");
      return pingMethod.invoke(connection);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Redis availability check failed", exception);
    }
  }

  private String extractLabel(String tokenKey) {
    if (!isPrismToken(tokenKey)) {
      return "UNKNOWN";
    }
    String inner = tokenKey.substring(1, tokenKey.length() - 1);
    int firstUnderscore = inner.indexOf('_');
    int lastUnderscore = inner.lastIndexOf('_');
    if (firstUnderscore < 0 || lastUnderscore <= firstUnderscore) {
      return "UNKNOWN";
    }
    return inner.substring(firstUnderscore + 1, lastUnderscore).toUpperCase(Locale.ROOT);
  }

  private boolean isPrismToken(String tokenKey) {
    return tokenKey.startsWith("<PRISM_") && tokenKey.endsWith(">");
  }
}
