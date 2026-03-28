# Spring Prism v1.1.0 Release Notes

This file tracks the `v1.1.0` release line incrementally while work lands on `v1.1.0-SNAPSHOT`.

## Unreleased

### Backward Compatibility and Migration from v1.0.0

- Preserved the legacy `PrismMetricsSnapshot` constructor so custom integrations compiled against
  `v1.0.0` keep working while the runtime snapshot grows in `v1.1.0`.
- Marked the legacy constructor as deprecated and documented the exact fallback values used for new
  fields: `totalActiveRules = 0`, `failureMode = "FAIL_SAFE"`,
  `blockedRequestCount = 0L`, `blockedResponseCount = 0L`,
  `configuredVaultMode = "AUTO"`, `customAppSecretConfigured = false`,
  `sharedVaultReady = false`, `vaultReadinessStatus = "UNKNOWN"`, and
  `vaultReadinessDetails = ""`.
- Preserved legacy strict-mode properties for compatibility and marked them deprecated in both Java
  API and Spring Boot configuration metadata.
- Property `spring.prism.security-strict-mode` is deprecated and will be removed in `v2.0.0`. Use
  `spring.prism.failure-mode` instead.
- Property `spring.prism.mcp.security-strict-mode` is deprecated and will be removed in `v2.0.0`.
  Use the top-level `spring.prism.failure-mode` instead.
- Added `PrismRulePack.isAutoDiscoverable()` with a safe default of `false` so custom user-defined
  rule pack beans from `v1.0.0` do not become active automatically after upgrading to `v1.1.0`.
- Official Spring Prism rule packs explicitly opt in to auto-discovery, which preserves extension
  support without introducing surprise redaction behavior for existing applications.
- Added `PrismRulePack.getActivationAliases()` as an additive SPI method so modular rule packs can
  participate in `spring.prism.locales` without breaking existing `1.x` custom implementations.
- Preserved the legacy in-core `UniversalRulePack` for direct `prism-core` consumers while the
  starter begins preferring the modular `prism-rulepack-common` baseline.
- Clarified that Spring applications should inject `@Qualifier("springPrismRulePacks")` for the
  resolved active rule pack list, while plain `List<PrismRulePack>` remains the contract for
  discovering available rule pack beans in the application context.
- Preserved the legacy in-core `EuropeRulePack` for direct `prism-core` consumers while marking it
  deprecated for removal in `v2.0.0`.

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

### Developer Experience and Guides

- Reorganized the Docusaurus information architecture into clearer groups for onboarding,
  operations, feature guides, and release quality.
- Added a dedicated quickstart guide for the fastest production-shaped starter adoption path.
- Added a production playbook that maps single-node, Redis, heuristic NLP, and hybrid NLP rollout
  choices.
- Updated release-readiness guidance to reflect the `v1.1.0-SNAPSHOT` train instead of the older
  `1.0.0` release checklist.
- Refined README and configuration docs so example paths, rollout choices, and required validation
  steps are easier to find.
- Added a downloadable starter Grafana dashboard JSON for the Prism runtime snapshot path.
- Trimmed Maven Central publication noise by keeping the runnable examples, unified demo app, and
  benchmark suite as repo-only contributor assets instead of published library artifacts.
- Moved the unified demo app out of the main examples reactor while keeping it runnable through its
  own standalone Maven project for manual QA and release smoke testing.

### Rulepack SPI / Modular Rulepacks

- Added a new `prism-rulepack-common` module as the default modular baseline for `UNIVERSAL`
  detection in the starter.
- The Spring Boot starter now depends on `prism-rulepack-common` by default and prefers the
  modular common pack when it is present on the classpath.
- Added an additive `PrismRulePack.getActivationAliases()` SPI method so locale activation can
  evolve without breaking `1.x` implementations.
- Preserved compatibility fallback to the legacy in-core `UniversalRulePack` when the modular
  common module is absent.
- Ensured the starter keeps a single baseline rule pack active per locale family, so custom
  auto-discoverable `UNIVERSAL` packs can replace the default modular baseline without silently
  duplicating detectors after a `1.0.0 -> 1.1.0` upgrade.
- Marked the legacy in-core `UniversalRulePack` deprecated for future `2.0.0` removal while
  keeping it fully functional in `1.x`.
- Added module-level, starter-level, and integration-level validation for the new modular common
  pack path.

### Regional Rulepacks / Big 7 Coverage

- Added new modular regional rulepacks:
  `prism-rulepack-ro`, `prism-rulepack-us`, `prism-rulepack-pl`, `prism-rulepack-nl`,
  `prism-rulepack-gb`, `prism-rulepack-fr`, and `prism-rulepack-de`.
- Added checksum-backed regional detectors for `CIF`, `EIN`, `ABA_ROUTING`, `NIP`, `BSN`,
  `NHS`, `NIR`, `SIREN`, `SIRET`, and `STEUER_ID`.
- Extended starter rulepack resolution so regional modules can be selected through
  `spring.prism.locales` and through locale aliases such as `FRA`, `NLD`, and `GBR` without
  breaking `1.x` fallback behavior.
- Preserved compatibility fallback to the legacy in-core `EuropeRulePack` when a regional module
  is absent, while deprecating that in-core pack for removal in `v2.0.0`.
- Added starter and integration coverage proving that `RO`, `US`, `PL`, `NL`, `GB`, `FR`, and
  `DE` resolve to the correct active rulepack and publish the expected `totalActiveRules`
  snapshots.
- Community feedback is explicitly encouraged for regional edge cases so new country-specific
  formats, separators, and validation refinements can land through focused GitHub Issues instead
  of ad-hoc breaking changes.
