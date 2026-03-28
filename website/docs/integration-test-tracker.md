# Integration Test Tracker

This page tracks the release-critical integration suites that back the `1.1.0` line.

## Current Priority

Current branch focus:

- `feature/37-regional-rulepacks-big-7`

Primary release claim:

- Spring Prism can ship modular regional rulepacks without breaking `1.x` locale fallback behavior

## Active Suites

| Suite | Location | Purpose | Status |
| --- | --- | --- | --- |
| Redis multi-node suite | `prism-integration-tests` | Cross-node restore, TTL expiry, Redis outage handling, noisy LLM restore, large-payload coverage | Green |
| Optional NLP extension suite | `prism-integration-tests` | Disabled-by-default behavior, heuristic opt-in redaction, false-positive guardrails on technical text | Green |
| Regional rulepack suite | `prism-integration-tests` | Locale selection for `RO`, `US`, `PL`, `NL`, `GB`, `FR`, and `DE` plus runtime snapshot totals | Green |
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
| RAG-style dense payload flow | Passing in suite design, local Docker execution required |
| Redis outage handling | Passing |

## Optional NLP Scenarios

| Scenario | Status |
| --- | --- |
| Disabled by default | Passing |
| Heuristic person-name redaction | Passing |
| Person-name restore after LLM response | Passing |
| Technical phrase false-positive guardrail | Passing |
| Hybrid backend startup validation without model | Passing |

## Validation Commands

Focused suite:

```bash
mvn -pl prism-integration-tests -am test -Dtest=RedisMultiNodeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl prism-integration-tests -am test -Dtest=OptionalNlpExtensionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

Full reactor validation:

```bash
mvn verify
```

## Notes

- `prism-integration-tests` is a test-only module and is configured with `maven.deploy.skip=true`.
- Redis outage coverage currently validates fail-closed behavior by asserting that tokenize and
  restore operations fail in a controlled way while Redis is unavailable.
- Large-context and optional NLP work in `v1.1.0` should keep this suite updated whenever runtime
  behavior changes in a way that affects end-to-end sanitization or restoration.
