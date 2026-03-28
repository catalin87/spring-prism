# Performance

Spring Prism is designed to keep privacy protection practical for normal application traffic. The main cost centers are detector scans across prompt text and vault lookups during response restoration, especially when a distributed Redis vault is enabled.

## What Is Optimized

- **Fast-Path Scanning**: Universal and European detectors perform cheap fast-path checks before executing regex or checksum logic, significantly reducing overhead for clean text.
- **Virtual Threads**: Built with Java 21, Spring Prism utilizes virtual threads for efficient, non-blocking I/O during scanning and vault operations.
- **Large-Prompt Reconstruction**: Spring AI and LangChain4j rebuild sanitized text in a single pass instead of repeatedly shifting large `StringBuilder` buffers.
- **Response Fast Paths**: Detokenization now skips regex work entirely when a chunk does not contain the Prism token prefix.
- **Repeated-Value Caching**: Within a single prompt or response, repeated values and repeated Prism tokens reuse cached vault results instead of repeating the same work.
- **Segment-Aware Scanning**: Very large prompts are scanned in overlapping windows so sparse, document-style payloads avoid paying the full detector cost over one monolithic string.
- **Streaming Safety**: The `StreamingBuffer` now avoids unnecessary intermediate string copies while buffering fragmented SSE token chunks.
- **Vault Hot Path**: The default in-memory vault uses a deterministic cleanup ticker instead of per-token random cleanup checks, reducing overhead when many entities are tokenized in one prompt.
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

## v1.1.0 Large-Context Focus

The `v1.1.0` line adds performance work aimed specifically at long prompts and RAG-style payloads:

- a larger Redis multi-node integration scenario with dense PII coverage
- single-pass large-text tokenization in both AI integrations
- cheaper fast-path detokenization for clean chunks
- a dedicated `LargePromptAdvisorBenchmark` in `prism-benchmarks`
- dedicated NLP benchmarks for heuristic, OpenNLP, hybrid, and cold model-load paths

The goal is not just microbenchmark speed, but keeping the end-to-end Prism path credible when a prompt contains many retrieved records or large pasted documents.

## v1.1.0 Benchmark Snapshot (JMH + GC)

The following results were captured on OpenJDK 21.0.10 under WSL (1 thread, 5 forks, 5x10s warmup, 5x10s measurement) with `-prof gc` enabled and a real OpenNLP `en-ner-person.bin` model.

| Benchmark | Throughput (ops/s) | Allocation | Takeaway |
| :--- | ---: | ---: | :--- |
| `DetectorBenchmark.detectMixedCommonPii` | 13,888 | 5.8 KB/op | Baseline mixed common detection remains fast enough for normal prompt scanning. |
| `DetectorBenchmark.detectMixedEnterpriseBig7Pii` | 453 | 123.0 KB/op | Big 7 regional coverage is much heavier, but still practical for enterprise-grade payload inspection. |
| `DetectorBenchmark.skipCleanTextViaEnterpriseFastPaths` | 5,900 | 3.7 KB/op | Clean-text fast paths continue to pay off in the enterprise detector profile. |
| `LargePromptAdvisorBenchmark.tokenizeAndRestoreLargePrompt (COMMON)` | 765,125 | 2.1 KB/op | The restore-inclusive advisor path stays very fast on the common profile. |
| `LargePromptAdvisorBenchmark.tokenizeAndRestoreLargePrompt (BIG7)` | 150,777 | 2.1 KB/op | Big 7 reduces throughput, but the full advisor round-trip remains strong for large prompts. |
| `LargePromptAdvisorBenchmark.tokenizeLargePrompt (COMMON)` | 28.2 | 2.0 MB/op | Full prompt tokenization is meaningfully more expensive than restore-inclusive round trips. |
| `LargePromptAdvisorBenchmark.tokenizeLargePrompt (BIG7)` | 3.1 | 18.97 MB/op | **Worst-case scenario**: Full large-prompt tokenization with Big 7 is the heaviest shipped benchmark. |
| `NlpBenchmark.heuristicDetectWarm` | 183,090 | 792 B/op | Heuristic NLP is extremely cheap and is the best default for cost-sensitive deployments. |
| `NlpBenchmark.openNlpDetectWarm` | 2,582 | 328.5 KB/op | Warm OpenNLP is viable, but materially heavier than heuristic mode. |
| `NlpBenchmark.hybridDetectWarm` | 2,320 | 337.1 KB/op | Hybrid mode stays close to pure OpenNLP in throughput and allocation cost. |
| `NlpBenchmark.loadOpenNlpModelCold` | 1.41 | 73.4 MB/op | Cold model loading is expensive and must stay off the request path. |
| `StreamingBufferBenchmark.processFragmentedToken` | 4,979,739 | 432 B/op | Streaming token restoration remains one of the fastest paths in the system. |
| `VaultBenchmark.tokenizeEmail` | 498,668 | 1.9 KB/op | Core in-memory vault tokenization remains comfortably sub-millisecond at scale. |
| `VaultBenchmark.detokenizeEmail` | 516,078 | 2.0 KB/op | Detokenization cost stays symmetric with tokenization in the core vault path. |
| `RedisVaultBenchmark.tokenizeEmail` | 529,019 | 1.8 KB/op | The Redis-backed code path remains in the same performance class as the in-memory benchmark path. |
| `RedisVaultBenchmark.detokenizeEmail` | 510,918 | 1.9 KB/op | Redis restore-path plumbing does not introduce a dramatic local overhead regression. |

## What The v1.1.0 Results Mean

- **OpenNLP is correctly pre-warmed at startup**. Warm detection throughput (`2,582 ops/s`) is orders of magnitude higher than the cold model load path (`1.41 ops/s`), which confirms that model loading is not happening on the request hot path.
- **Heuristic NLP remains the efficiency default**. At `183,090 ops/s` and only `792 B/op`, heuristic detection is dramatically cheaper than OpenNLP or hybrid detection.
- **Hybrid NLP is feasible, but not free**. `HybridDetectWarm` lands close to `OpenNlpDetectWarm`, which is good for predictability, but both allocate around `330 KB/op` and generate visible GC activity under sustained load.
- **Big 7 coverage is a deliberate throughput-for-coverage tradeoff**. The detector profile drops from `13,888 ops/s` for common mixed PII to `453 ops/s` for the enterprise Big 7 scenario, with a much higher allocation footprint.
- **Streaming and vault paths remain strong**. The streaming buffer, in-memory vault, and Redis-backed vault benchmarks all stay in a healthy range for production usage and do not show catastrophic GC behavior.

## GC Profiling Guidance

- Use `HEURISTIC` NLP mode when low allocation and low pause pressure are more important than highest recall.
- Use `HYBRID` when you need higher person-name recall and can afford the additional allocation pressure.
- Keep OpenNLP model loading at startup. The cold-load benchmark shows a very high one-time cost that should never sit on a user request path.
- When evaluating enterprise profiles, benchmark both:
  - `COMMON` or default profiles for baseline traffic
  - `BIG7` plus NLP for worst-case operational sizing

## Running Benchmarks

To verify performance on your own infrastructure, build the benchmark jar:

```bash
mvn -pl prism-benchmarks -am package -DskipTests
```

Run all benchmarks:

```bash
java -Dprism.bench.nlpModel=prism-benchmarks/models/en-ner-person.bin \
  -jar prism-benchmarks/target/benchmarks.jar
```

Or run a specific suite:

```bash
java -jar prism-benchmarks/target/benchmarks.jar DetectorBenchmark
```

Large-prompt advisor benchmark:

```bash
java -jar prism-benchmarks/target/benchmarks.jar LargePromptAdvisorBenchmark
```

NLP benchmark with a real OpenNLP model:

```bash
./scripts/download-nlp-model.sh
java -Dprism.bench.nlpModel=prism-benchmarks/models/en-ner-person.bin \
  -jar prism-benchmarks/target/benchmarks.jar NlpBenchmark
```

NLP benchmark with GC profiling:

```bash
java -Dprism.bench.nlpModel=prism-benchmarks/models/en-ner-person.bin \
  -jar prism-benchmarks/target/benchmarks.jar NlpBenchmark -prof gc
```

The benchmark suite uses an in-memory substitute for Redis templates to allow local execution without a live Redis instance.

For OpenNLP and hybrid benchmarks, the model is loaded once at setup time for warm-detect measurements, while `loadOpenNlpModelCold` captures the startup load cost separately.