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
package io.github.catalin87.prism.spring.ai.advisor;

import io.github.catalin87.prism.core.PrismRulePack;
import io.github.catalin87.prism.core.PrismToken;
import io.github.catalin87.prism.core.PrismVault;
import io.github.catalin87.prism.core.ruleset.UniversalRulePack;
import io.github.catalin87.prism.core.token.HmacSha256TokenGenerator;
import io.github.catalin87.prism.core.vault.DefaultPrismVault;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH microbenchmark comparing the legacy {@link PrismTextScanner#detokenize(String)} path against
 * the v1.2.0 {@link PrismTextScanner#detokenizeCounted(String)} variant on a representative ~5000
 * character RAG payload containing 50 vault tokens.
 *
 * <p>Run via {@link #main(String[])} or {@code mvn -pl prism-spring-ai test-compile exec:java
 * -Dexec.classpathScope=test
 * -Dexec.mainClass=io.github.catalin87.prism.spring.ai.advisor.ScannerBenchmark}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ScannerBenchmark {

  private static final int TOKEN_COUNT = 50;
  private static final int TARGET_LENGTH = 5000;
  private static final byte[] APP_SECRET =
      "scanner-benchmark-secret".getBytes(StandardCharsets.UTF_8);
  private static final long VAULT_TTL_SECONDS = 3_600L;

  private PrismTextScanner scanner;
  private String payload;

  @Setup
  public void setup() {
    PrismVault vault =
        new DefaultPrismVault(new HmacSha256TokenGenerator(), APP_SECRET, VAULT_TTL_SECONDS);

    List<PrismRulePack> rulePacks = List.of(new UniversalRulePack());
    this.scanner =
        new PrismTextScanner(rulePacks, vault, ObservationRegistry.NOOP, PrismMetricsSink.NOOP);

    String[] tokenKeys = new String[TOKEN_COUNT];
    for (int i = 0; i < TOKEN_COUNT; i++) {
      PrismToken token = vault.tokenize("benchmark-" + i + "@example.com", "EMAIL");
      tokenKeys[i] = token.key();
    }

    this.payload = buildPayload(tokenKeys, TARGET_LENGTH);
  }

  @Benchmark
  public String legacyDetokenize() {
    return scanner.detokenize(payload);
  }

  @Benchmark
  public void countedDetokenize(Blackhole bh) {
    PrismTextScanner.DetokenizeResult result = scanner.detokenizeCounted(payload);
    bh.consume(result.text());
    bh.consume(result.restoredCount());
  }

  private static String buildPayload(String[] tokenKeys, int targetLength) {
    String filler =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vault-backed retrieval"
            + " augmented generation responses regularly interleave model prose with restored"
            + " entity tokens, which the scanner must rewrite in a single pass. ";
    StringBuilder sb = new StringBuilder(targetLength + 256);
    int tokenIndex = 0;
    while (sb.length() < targetLength) {
      sb.append(filler);
      sb.append(tokenKeys[tokenIndex % tokenKeys.length]);
      sb.append(' ');
      tokenIndex++;
    }
    return sb.toString();
  }

  public static void main(String[] args) throws RunnerException {
    Options opts =
        new OptionsBuilder()
            .include(ScannerBenchmark.class.getSimpleName())
            .forks(0)
            .warmupIterations(1)
            .measurementIterations(2)
            .warmupTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .measurementTime(org.openjdk.jmh.runner.options.TimeValue.seconds(2))
            .build();
    new Runner(opts).run();
  }
}
