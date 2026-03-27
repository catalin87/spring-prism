# Integration Test Tracker

This page tracks the release-critical integration suites that back the `1.1.0-SNAPSHOT` line.

## Current Priority

Current branch focus:

- `feature/31-redis-first-cluster-safety`

Primary release claim:

- Redis-backed shared vault storage is safe for multi-node Prism deployments

## Active Suites

| Suite | Location | Purpose | Status |
| --- | --- | --- | --- |
| Redis multi-node suite | `prism-integration-tests` | Cross-node restore, TTL expiry, Redis outage handling, noisy LLM restore, large-payload coverage | Green |
| Spring AI WireMock suite | `prism-spring-ai` | Advisor interception and OpenAI-compatible restore behavior | Green |
| Starter auto-configuration suite | `prism-spring-boot-starter` | Redis vault selection, readiness, actuator metrics | Green |
| Example app smoke suites | `prism-examples` | Real application boot and wiring coverage | Green |

## Redis Multi-Node Scenarios

| Scenario | Status |
| --- | --- |
| Determinism and regression baseline | Passing |
| Cross-node token restore | Passing |
| TTL expiry | Passing |
| Secret mismatch safety | Passing |
| Large-payload flow | Passing |
| Redis outage handling | Passing |

## Validation Commands

Focused suite:

```bash
mvn -pl prism-integration-tests -am test -Dtest=RedisMultiNodeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Full reactor validation:

```bash
mvn verify
```

## Notes

- `prism-integration-tests` is a test-only module and is configured with `maven.deploy.skip=true`.
- Redis outage coverage currently validates fail-closed behavior by asserting that tokenize and
  restore operations fail in a controlled way while Redis is unavailable.
