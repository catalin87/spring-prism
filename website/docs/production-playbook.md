---
sidebar_position: 5
---

# Production Playbook

This page is the operator-oriented map for adopting Spring Prism in real environments.

## Decision Matrix

| Need | Recommended Path |
| --- | --- |
| Single application instance | `vault.type=in-memory` |
| Multiple nodes or Kubernetes | `vault.type=redis` |
| Deterministic detector set only | starter without NLP extension |
| Person-name redaction with conservative rollout | NLP extension with `backend=heuristic` |
| Person-name redaction with stronger recall | NLP extension with `backend=hybrid` and explicit model |

## Minimal production baseline

Every serious deployment should have:

- a non-default `spring.prism.app-secret`
- starter autoconfiguration enabled
- metrics endpoint available through Actuator or `/prism/metrics`
- integration tests covering the runtime path you actually use
- docs and rollout notes synchronized with the chosen deployment mode

`spring.prism.failure-mode=FAIL_SAFE` remains the default posture and preserves the established
fail-open behavior with metrics. For stricter regulated environments, prefer
`spring.prism.failure-mode=FAIL_CLOSED` once you have validated the rollout and operational
dependencies. See [Configuration: Failure Mode](/docs/configuration#failure-mode).

## Single-node rollout

Use this when request tokenization and response restoration happen inside the same process or node.

Recommended settings:

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: in-memory
    ttl: 30m
```

Use this path when:

- the app is local or developer-facing
- the workload is simple and non-distributed
- you do not need cross-node restore

## Multi-node rollout

Use this when restore can happen on a different pod or node.

Recommended settings:

```yaml
spring:
  data:
    redis:
      host: redis.internal
      port: 6379
  prism:
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: redis
    ttl: 30m
```

This path is only production-ready when:

- every node uses the same shared Redis deployment
- every node uses the same non-default `spring.prism.app-secret`
- TTL covers the realistic request-to-restore latency window

## NLP rollout

### Phase 1: heuristic

Start here when you want person-name redaction with low operational friction.

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: heuristic
        confidence-threshold: 4
```

### Phase 2: hybrid

Promote to this path only after validating the model and blocked phrases on realistic text.

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: file:${PRISM_NLP_MODEL}
        confidence-threshold: 4
```

Production rules for `hybrid`:

- the model artifact must be versioned and deployed consistently on every node
- startup should fail if the model is missing or unreadable
- blocked phrases should include domain-specific technical vocabulary

## Definition of done for feature work

Any new runtime feature or behavior change should include:

- unit tests in the owning module
- at least one end-to-end scenario in `prism-integration-tests` when behavior crosses module or
  runtime boundaries
- Docusaurus docs updates before merge
- `release_notes.v1.1.0.md` updates while the release line is active

## Release-train checklist

Before merging into `v1.1.0`, confirm:

- the chosen runtime mode is documented
- example config exists or is updated
- integration tests cover the user-facing behavior
- metrics or readiness output is still truthful after the change
- README still reflects the recommended onboarding path

## Where to go next

- [Distributed Deployments](/docs/distributed-deployments)
- [Troubleshooting](/docs/troubleshooting)
- [Integration Test Tracker](/docs/integration-test-tracker)
- [Release Readiness](/docs/release-readiness)
