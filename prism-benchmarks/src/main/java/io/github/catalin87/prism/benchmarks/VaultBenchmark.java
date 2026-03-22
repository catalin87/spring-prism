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
package io.github.catalin87.prism.benchmarks;

import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.TokenGenerator;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Measures in-memory vault tokenization and restoration throughput. */
@State(Scope.Benchmark)
public class VaultBenchmark {

  private DefaultPrismVault vault;
  private String tokenKey;

  /** Prepares a stable token for repeated in-memory detokenization measurements. */
  @Setup
  public void setUp() {
    TokenGenerator generator = new HmacSha256TokenGenerator();
    vault =
        new DefaultPrismVault(
            generator, "benchmark-secret-material-32bytes".getBytes(StandardCharsets.UTF_8), 3600L);
    PrismToken token = vault.tokenize("user@example.com", "EMAIL");
    tokenKey = token.key();
  }

  /** Measures token creation throughput in the default in-memory vault. */
  @Benchmark
  public PrismToken tokenizeEmail() {
    return vault.tokenize("user@example.com", "EMAIL");
  }

  /** Measures token restoration throughput in the default in-memory vault. */
  @Benchmark
  public String detokenizeEmail() {
    return vault.detokenize(tokenKey);
  }
}
