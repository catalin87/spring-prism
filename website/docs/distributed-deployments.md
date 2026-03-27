---
sidebar_position: 4
---

# Distributed Deployments

This guide is the production path for running Spring Prism safely across multiple nodes.

## Single-Node vs Multi-Node

Use `in-memory` when:

- one application node handles both request tokenization and response restoration
- you do not rely on cross-node response handling

Use `redis` when:

- you run multiple pods or nodes behind a load balancer
- requests and responses may land on different nodes
- you use asynchronous or streaming flows where restore can happen elsewhere in the fleet

## Production Rules for Multi-Node

Every node must:

- use `spring.prism.vault.type=redis`
- connect to the same Redis deployment or logical shared vault
- use the same non-default `spring.prism.app-secret`
- use compatible TTL values for the expected restore window

If any of those are inconsistent, cross-node restoration can fail by design.

## Recommended Configuration

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
    locales: UNIVERSAL
```

Use a shared secret source for every node:

- Kubernetes secret
- Vault-backed environment injection
- your platform's encrypted configuration system

Do not hardcode secrets per pod or per instance.

## Load Balancers and Sticky Sessions

Sticky sessions are not required when:

- all nodes share the same Redis-backed vault
- all nodes share the same non-default `spring.prism.app-secret`

That is the whole point of the distributed vault path: request tokenization can happen on node A,
while response restoration happens on node B.

## TTL Guidance

Choose TTL based on the time window between tokenization and restoration.

Good starting guidance:

- synchronous chat flows: `15m` to `30m`
- streaming responses: `30m` to `60m`
- asynchronous or queue-backed restore paths: long enough to cover the slowest realistic round trip

Do not choose TTL only for storage minimization. If TTL is too short, valid cross-node restores can
fail after the entry expires.

## Rolling Deployments

Rolling deployments are safe only when:

- the new and old nodes use the same `spring.prism.app-secret`
- both versions still understand the same token contract
- both versions point at the same shared Redis vault

If you rotate the app secret during a rolling deployment, old tokens become non-restorable on nodes
using the new secret.

## Secret Rotation Guidance

Spring Prism currently uses one active application secret for token verification.

For safe production rotation:

1. stop issuing new traffic on the old secret
2. allow old in-flight token TTL windows to drain
3. roll the new secret across every node together
4. resume normal traffic once the fleet is consistent

Do not rotate secrets mid-flight if old tokens still need to be restored.

## Operator Checklist

Before marking a multi-node rollout production-ready, confirm:

- `configuredVaultMode = REDIS`
- `vaultType = RedisPrismVault`
- `sharedVaultReady = true`
- `vaultReadinessStatus = READY`
- `spring.prism.app-secret` is not the default value
- Redis is shared by every node that can participate in restore
- TTL covers the real request-to-restore latency window

## What the Dashboard Means

The embedded dashboard is still per-node, but it now exposes distributed posture clearly:

- `configuredVaultMode`
- `vaultType`
- `distributedVault`
- `sharedVaultReady`
- `vaultReadinessStatus`
- `vaultReadinessDetails`

That gives operators a fast node-level readiness signal even before adding wider fleet dashboards in
Grafana.
