---
sidebar_position: 8
---

# Grafana Integration

Spring Prism already exposes a rich runtime snapshot through:

- `/actuator/prism` when Spring Boot Actuator is present
- `/prism/metrics` when Actuator is absent

For `v1.1.0`, the recommended Grafana integration path is:

- use **Grafana Infinity** or another JSON-capable datasource for Prism-specific operational views
- use normal **Prometheus scraping** for JVM, process, HTTP, and Spring Boot infrastructure metrics

This keeps the guidance honest to the current implementation:

- Prism-specific cluster insight is available today through the JSON snapshot
- Prometheus remains the best path for standard node and application metrics
- a first-class Prism Prometheus metric family can be added later without blocking operators today

## Why This Approach

The Prism runtime snapshot already contains the fields operators usually want first:

- `configuredVaultMode`
- `vaultType`
- `distributedVault`
- `sharedVaultReady`
- `tokenizedCount`
- `detokenizedCount`
- `detectionErrorCount`
- `tokenBacklog`
- `privacyScore`
- `integrationMetrics`
- `historyRollups`

That makes Grafana a good fit even before Spring Prism exposes dedicated Prometheus counters and
timers for every Prism-specific signal.

## Recommended Setup

### 1. Expose the Prism endpoint

If you use Actuator, include the Prism endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prism,prometheus
```

Then your Grafana datasource can read:

- `/actuator/prism`

If you do not use Actuator, use:

- `/prism/metrics`

### 1.5. Import the starter dashboard

Spring Prism now ships a suggested Grafana dashboard JSON you can import as a starting point:

- [Download `spring-prism-overview.json`](/grafana/spring-prism-overview.json)

What it includes:

- Privacy Score
- Shared Vault Ready
- Vault mode summary
- Token backlog
- Detection errors
- history charts for backlog, detections, and scan latency
- rule-pack and entity activity tables

This dashboard is designed for the **Grafana Infinity** datasource and assumes a default endpoint of
`http://localhost:8080/actuator/prism` after import. Update the hidden `prism_endpoint` variable in
Grafana to point at your real node endpoint.

Recommended import flow:

1. Install the Grafana Infinity datasource plugin.
2. Create an Infinity datasource that your Grafana instance can use.
3. Import `spring-prism-overview.json`.
4. Select your Infinity datasource when Grafana prompts for `Prism Infinity Datasource`.
5. Update the dashboard variable `prism_endpoint` if your node uses a different host, port, or
   `/prism/metrics` path.

### 2. Keep Prometheus for infrastructure metrics

Use Prometheus normally for:

- JVM memory and GC
- CPU and process metrics
- HTTP request metrics
- pod, container, and node health

Use the Prism JSON snapshot for:

- privacy posture
- shared vault readiness
- token backlog
- integration timing summaries
- rule-pack and entity activity

## Multi-Node Guidance

In multi-node deployments, the embedded Prism endpoint is still **per node**.

That means:

- `/actuator/prism` on node A shows node A's local runtime snapshot
- `/actuator/prism` on node B shows node B's local runtime snapshot

Redis already solves **cross-node token restoration**, but it does not automatically aggregate
dashboard state across all nodes.

For `v1.1.0`, the recommended cluster pattern is:

- use Prometheus to scrape every node
- use Grafana to organize per-node Prism snapshots alongside cluster infrastructure metrics

## Suggested First Panels

The starter JSON already includes these panel ideas sourced from the Prism JSON snapshot:

- Privacy Score
  Show `privacyScore.score`
- Shared Vault Ready
  Show `sharedVaultReady`
- Vault Mode
  Show `configuredVaultMode` and `vaultType`
- Token Backlog
  Show `tokenBacklog`
- Detection Errors
  Show `detectionErrorCount`
- Spring AI Scan Latency
  Read `integrationMetrics` for `spring-ai`
- LangChain4j Scan Latency
  Read `integrationMetrics` for `langchain4j`
- MCP Scan Latency
  Read `integrationMetrics` for `mcp-stdio` and `mcp-streamable-http`

## Suggested First Alerts

Good first alerts for Prism-aware operations:

- `sharedVaultReady == false` on a node configured for Redis
- `tokenBacklog` above your normal baseline
- `detectionErrorCount` increasing unexpectedly
- scan latency rising above your operational threshold

## Example Operator Workflow

1. Prometheus shows cluster health, pod churn, and JVM pressure.
2. Grafana reads `/actuator/prism` from each Prism-enabled node.
3. Operators confirm:
   - Redis-backed restore path is active
   - `sharedVaultReady` is true
   - token backlog stays within tolerance
   - scan and restore timings remain stable

## Current Limitation

Spring Prism does **not** yet ship a dedicated Prometheus-native Prism metric set with stable metric
names for every privacy-specific signal. Today, the most complete operator surface is still the
Prism runtime snapshot exposed by `/actuator/prism` or `/prism/metrics`.

That is why the recommended guidance for now is:

- Prometheus for general application and infrastructure telemetry
- Grafana JSON integration for Prism-specific operational visibility

The provided dashboard JSON should be treated as a strong starter template, not a substitute for
your own environment-specific alert thresholds, node naming, or organization-wide observability
standards.
