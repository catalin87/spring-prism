---
sidebar_position: 12
---

# Troubleshooting

This page focuses on the real failure modes teams are likely to hit first in production.

## Response Returned With Prism Tokens Still Present

Likely causes:

- token restore happened on a different node with no shared Redis vault
- `spring.prism.app-secret` differs between nodes
- token TTL expired before restoration

Check:

- `configuredVaultMode`
- `vaultType`
- `sharedVaultReady`
- `vaultReadinessStatus`

Fix:

- move to `spring.prism.vault.type=redis`
- ensure every node shares the same Redis deployment
- ensure every node shares the same non-default app secret
- increase TTL if the restore window is too short

## Startup Fails in Redis Mode

Symptom:

- startup fails with a message saying `spring.prism.vault.type=redis requires a StringRedisTemplate bean`

Cause:

- Prism was explicitly told to use Redis, but the application has no Redis client bean

Fix:

- add Spring Data Redis and a working Redis configuration
- or change `spring.prism.vault.type` back to `auto` or `in-memory` for local-only deployments

## Shared Vault Ready Is False

Likely causes:

- Redis-backed vault is active but the default app secret is still configured
- the node is still using a local vault

Interpretation:

- distributed restore may be enabled
- but the deployment is not yet in a production-ready posture

Fix:

- override `spring.prism.app-secret` with a shared non-default secret on every node
- ensure the runtime vault is actually `RedisPrismVault`

## Restore Works On One Node But Fails After Deployment

Likely causes:

- rolling deployment introduced different secrets
- some nodes point at a different Redis instance or namespace
- TTL is too short for the real request/restore delay

Fix:

- verify every node shares the same secret and Redis target
- avoid in-flight secret rotation
- size TTL for the actual end-to-end latency window

## Token Backlog Keeps Growing

Likely causes:

- responses are not being restored
- traffic is partially one-way
- restore latency is higher than expected

Check:

- `tokenBacklog`
- `detectionErrorCount`
- integration timing summaries
- Grafana or dashboard views for request/response activity

Fix:

- verify the restore path is active
- verify distributed vault readiness
- inspect integration-specific timing spikes

## Detection Errors Increase Unexpectedly

Interpretation:

- Prism is still fail-open by default
- protection continues, but reliability posture is degrading

Fix:

- inspect the affected integration path
- review recent deployment changes
- verify custom rules and locale configuration

## Recommended First Escalation Data

When debugging a production issue, collect:

- `configuredVaultMode`
- `vaultType`
- `sharedVaultReady`
- `vaultReadinessStatus`
- `tokenBacklog`
- `detectionErrorCount`
- top affected integration timing values
- current TTL
- confirmation that all nodes share the same secret and Redis target
