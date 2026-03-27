# Spring Prism v1.1.0 Release Notes

This file tracks the `v1.1.0` release line incrementally while work lands on `release/1.1.0`.

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
