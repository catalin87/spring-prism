# Performance

Spring Prism is designed to keep privacy protection practical for normal application traffic. The
main cost centers are detector scans across prompt text and vault lookups during response
restoration, especially when a distributed Redis vault is enabled.

## What Is Optimized

- Universal and European detectors now perform cheap fast-path checks before regex or checksum
  work.
- Spring AI and LangChain4j integrations now record scan, tokenize, and detokenize durations.
- Runtime metrics expose timing snapshots without logging raw PII.

## Runtime Timing Metrics

The starter metrics snapshot now includes `durationMetrics` entries keyed by integration and
operation:

- `spring-ai:scan`
- `spring-ai:vault-tokenize`
- `spring-ai:vault-detokenize`
- `langchain4j:scan`
- `langchain4j:vault-tokenize`
- `langchain4j:vault-detokenize`

Each entry reports:

- `samples`
- `totalNanos`
- `averageNanos`

When Spring Boot Actuator is present, the runtime snapshot is exposed from `/actuator/prism`.
Without Actuator, the starter exposes the same payload from `/prism/metrics`.

## Benchmarks

The `prism-benchmarks` module contains JMH microbenchmarks for:

- detector scan throughput
- in-memory vault tokenization and detokenization
- streaming token fragment restoration
- Redis-backed vault code path overhead

Build the benchmark jar:

```bash
mvn -pl prism-benchmarks -am package -DskipTests
```

Run all benchmarks:

```bash
java -jar prism-benchmarks/target/benchmarks.jar
```

Run a subset:

```bash
java -jar prism-benchmarks/target/benchmarks.jar DetectorBenchmark
```

The Redis benchmark uses an in-memory template substitute so the benchmark suite can run without a
live Redis server during standard development flows.
