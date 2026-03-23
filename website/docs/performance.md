# Performance

Spring Prism is designed to keep privacy protection practical for normal application traffic. The main cost centers are detector scans across prompt text and vault lookups during response restoration, especially when a distributed Redis vault is enabled.

## What Is Optimized

- **Fast-Path Scanning**: Universal and European detectors perform cheap fast-path checks before executing regex or checksum logic, significantly reducing overhead for clean text.
- **Virtual Threads**: Built with Java 21, Spring Prism utilizes virtual threads for efficient, non-blocking I/O during scanning and vault operations.
- **Streaming Safety**: The `StreamingBuffer` is optimized for sub-microsecond token restoration across SSE chunks.
- **Observability**: Spring AI and LangChain4j integrations record precise scan, tokenize, and detokenize durations exposed via Micrometer.

## Runtime Timing Metrics

The starter metrics snapshot includes `durationMetrics` entries keyed by integration and operation:

- `spring-ai:scan`
- `spring-ai:vault-tokenize`
- `spring-ai:vault-detokenize`
- `langchain4j:scan`
- `langchain4j:vault-tokenize`
- `langchain4j:vault-detokenize`

Each entry reports `samples`, `totalNanos`, and `averageNanos`. These are exposed via `/actuator/prism` (with Actuator) or `/prism/metrics` (without Actuator).

## v1.0.0 Performance Baseline (JMH)

The following results were captured using the `prism-benchmarks` module (OpenJDK 21, 1 thread, 5 forks, 10s iterations).

| Benchmark | Throughput (ops/s) | Avg. Latency | Description |
| :--- | :--- | :--- | :--- |
| **Full PII Scan** | 21,140 | **~47.3 μs** | Mixed Universal PII detection (Email, SSN, CC, etc.). |
| **Fast-Path Skip** | 74,265 | **~13.5 μs** | Scanning clean text via fast-paths. |
| **Token Restore** | 3,551,336 | **~281 ns** | Fragmented token restoration in streams. |
| **In-Memory Vault** | 501,539 | **~1.9 μs** | Tokenization/Detokenization logic. |
| **Redis Vault Path** | 492,448 | **~2.0 μs** | Internal code path for Redis integration. |

> **Note**: The Redis benchmark measures the internal code path overhead. Real-world Redis performance will be primarily determined by your network latency to the Redis server.

## Running Benchmarks

To verify performance on your own infrastructure, build the benchmark jar:

```bash
mvn -pl prism-benchmarks -am package -DskipTests
```

Run all benchmarks:

```bash
java -jar prism-benchmarks/target/benchmarks.jar
```

Or run a specific suite:

```bash
java -jar prism-benchmarks/target/benchmarks.jar DetectorBenchmark
```

The benchmark suite uses an in-memory substitute for Redis templates to allow local execution without a live Redis instance.
