# Spring Prism v1.1.0 Release Notes

This file tracks the `v1.1.0` release line incrementally while work lands on `v1.1.0-SNAPSHOT`.

## Unreleased

### Redis-First Cluster Safety

- Added explicit starter configuration for vault selection through `spring.prism.vault.type`.
- Preserved ergonomic `auto` behavior so the starter still uses Redis automatically when a
  `StringRedisTemplate` bean is present.
- Added startup validation so explicit `redis` mode fails fast when shared Redis infrastructure is
  not configured.
- Added cross-node Redis vault tests proving that one node can tokenize and another node can
  restore when both share the same Redis state and application secret.
- Exposed configured vault mode in the dashboard and actuator snapshot so operators can distinguish
  deployment intent from the active runtime vault class.
- Added a dedicated `sharedVaultReady` runtime signal so operators can tell when a Redis-backed
  deployment is actually ready for shared-node restoration.
- Documented the single-node versus multi-node deployment contract in the README and Docusaurus
  docs.
- Added a concrete Redis multi-node sample configuration in the Spring AI example resources.
- Added a Docusaurus Grafana integration guide based on `/actuator/prism` and `/prism/metrics`
  for Prism-specific operational visibility.
- Added dedicated distributed deployment and troubleshooting guides for enterprise multi-node
  operations.

### Large-Context and RAG Performance

- Reworked large-payload tokenization in Spring AI and LangChain4j to rebuild sanitized text in a
  single pass instead of performing repeated in-place `StringBuilder.replace(...)` operations.
- Added a detokenize fast-path that skips regex work entirely when no Prism token prefix is
  present in the response chunk.
- Reduced streaming chunk overhead in `StreamingBuffer` by avoiding unnecessary intermediate
  string copies while buffering fragmented Prism tokens.
- Reduced in-memory vault overhead for many-hit prompts by replacing probabilistic cleanup with a
  cheaper deterministic cleanup ticker and lightweight epoch-second reads.
- Added per-request tokenization and detokenization caches so repeated values or repeated Prism
  tokens do not trigger duplicate vault work inside the same large prompt/response flow.
- Added segment-aware scanning for very large prompts so detector work can be distributed across
  overlapping text windows without losing boundary matches near segment edges.
- Added focused scanner tests for large prompts in both Spring AI and LangChain4j integrations.
- Added a new JMH benchmark, `LargePromptAdvisorBenchmark`, to measure large prompt tokenization
  and tokenize-plus-restore costs through the real Spring AI advisor path.
- Strengthened the Redis multi-node integration suite with a larger RAG-style payload fixture so
  `v1.1.0` performance work is validated against production-shaped prompt sizes.

### Optional NLP Extensions

- Added a new optional module, `prism-extensions-nlp`, so person-name detection can ship outside
  `prism-core` without changing the deterministic base detector set.
- Added a heuristic person-name backend, an OpenNLP backend, and a hybrid detector that merges
  both candidate sources with contextual scoring.
- Added startup fail-fast validation for `opennlp` and `hybrid` modes when no readable model
  resource is configured.
- Extended starter rule-pack resolution so optional rule packs can be contributed from the
  classpath and still appear in runtime configuration even when their detectors are filtered.
- Added unit coverage for hybrid scoring and OpenNLP span mapping.
- Added end-to-end integration coverage proving that the extension is disabled by default, can
  tokenize and restore person names when enabled, and does not redact technical phrases such as
  `Spring Boot`.
- Added Docusaurus documentation for configuration, architecture, and test expectations of the NLP
  extension path.
