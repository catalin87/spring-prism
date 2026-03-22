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

import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Benchmarks the Redis-backed vault code path using an in-memory {@link StringRedisTemplate}
 * substitute so normal builds do not require a running Redis server.
 */
@State(Scope.Benchmark)
public class RedisVaultBenchmark {

  private RedisPrismVault vault;
  private String tokenKey;

  /** Prepares a Redis-backed vault path using the in-memory benchmark template. */
  @Setup
  public void setUp() {
    InMemoryStringRedisTemplate redisTemplate = new InMemoryStringRedisTemplate();
    vault =
        new RedisPrismVault(
            redisTemplate,
            new HmacSha256TokenGenerator(),
            "redis-benchmark-secret-material".getBytes(StandardCharsets.UTF_8),
            Duration.ofMinutes(30));
    PrismToken token = vault.tokenize("user@example.com", "EMAIL");
    tokenKey = token.key();
  }

  @Benchmark
  public PrismToken tokenizeEmail() {
    return vault.tokenize("user@example.com", "EMAIL");
  }

  @Benchmark
  public String detokenizeEmail() {
    return vault.detokenize(tokenKey);
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
