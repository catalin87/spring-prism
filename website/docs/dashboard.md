---
sidebar_position: 7
---

# Dashboard

The `prism-dashboard` module packages a lightweight embedded observability UI inside the jar at `/prism/`.

## Data sources

The dashboard prefers the Actuator endpoint:

- `/actuator/prism`

If Actuator is not on the classpath, it falls back to:

- `/prism/metrics`

## Current panels

- Global Privacy Score with live Coverage, Reliability, and Posture subscores over the last 60 minutes
- Vault health and tokenization counts
- Top redacted entity types
- Runtime pulse and tracked timer count
- Rule-pack activity bars
- Trend cards for leading pack, total detections, slowest timer, and audit retention
- Server-side rollup cards for recent, 5 minute, 15 minute, and 1 hour windows
- Integration drill-down cards for Spring AI and LangChain4j timing paths
- Threshold-aware alert cards for detection errors, scan latency, token backlog, and vault mode
- Entity drill-down cards grouped by rule pack and detector type
- Vault insight cards describing local vs Redis posture, shared-vs-local topology, and restore pressure
- Admin cards showing current retention, polling, and alert-threshold settings
- Masked recent-activity audit feed with action/source/limit filters
- Server-side retained history charts for detections, errors, scan latency, and token backlog
- Polling controls plus JSON, CSV, and incident-summary export
- Operational filters for integration, rule pack, entity type, and trend window
- Refraction-flow explainer

## Privacy Score

The dashboard now renders a live `Privacy Score` as the primary hero signal.

It is calculated from the last 60 minutes of retained dashboard history using:

```text
PrivacyScore = 100 * (
  0.50 * Coverage +
  0.30 * Reliability +
  0.20 * Posture
)
```

Where:

- `Coverage` reflects recent protected activity
- `Reliability` penalizes recent detector and restore errors
- `Posture` reflects strict-mode, vault mode, secret hygiene, and backlog posture

The goal is not to pretend the score is a universal security grade. It is a live operational signal that makes Prism's recent value and runtime health immediately visible.

## Dashboard configuration

The embedded UI now reads its defaults and alert thresholds from `spring.prism.dashboard.*`:

```yaml
spring:
  prism:
    dashboard:
      default-polling-seconds: 30
      audit-retention: 12
      history-retention: 120
      alert-thresholds:
        scan-latency-warn-ms: 25
        scan-latency-critical-ms: 75
        token-backlog-warn: 5
        token-backlog-critical: 20
        detection-error-warn: 1
        detection-error-critical: 5
```

These values shape both the server-side snapshot and the UI rendering, which keeps the dashboard behavior truthful to the running app configuration.

## Live example integration

Both example apps now embed the dashboard and expose the Prism Actuator endpoint:

- `spring-ai-example`
- `langchain4j-example`

Run either example, trigger one demo request, then open:

- `/prism/index.html`
- `/actuator/prism`

That gives you a live visual verification path in addition to demo mode.

## Demo mode

The dashboard includes a bundled fixture for visual verification without a live application runtime.

Use either:

- the **Open Demo Data** button in the UI
- or `/prism/?demo=1`

Demo mode loads the packaged `demo-metrics.json` fixture and keeps all values masked.

## Privacy boundary

- The dashboard only renders aggregate counters, timing summaries, and masked audit events.
- It does not expose raw PII.
- Audit history is intentionally bounded in memory and currently retains the most recent 12 masked events.
- Snapshot history is also bounded server-side and only retains aggregate values plus masked vault posture signals.
- Exported snapshots only contain the same masked dashboard payload already visible in the UI.

## Production access guidance

- Treat `/prism/`, `/prism/metrics`, and `/actuator/prism` as operational surfaces.
- In production, protect them with your application authentication, reverse-proxy controls, or network policy.
- Spring Prism keeps the payload masked, but the endpoints still expose meaningful operational metadata and should not be left broadly public.

For Spring Security-based applications, a simple pattern is to require an operator role for the dashboard routes:

```java
http.authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/prism/**", "/actuator/prism").hasRole("PRISM_OPERATOR")
    .anyRequest().authenticated());
```

If you do not use Spring Security, place the routes behind an internal gateway, VPN, or ingress rule instead.
