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

- Vault health and tokenization counts
- Top redacted entity types
- Runtime pulse and tracked timer count
- Rule-pack activity bars
- Trend cards for leading pack, total detections, slowest timer, and audit retention
- Integration drill-down cards for Spring AI and LangChain4j timing paths
- Alert cards for detection errors, scan latency, token backlog, and vault mode
- Entity drill-down cards grouped by rule pack and detector type
- Vault insight cards describing local vs Redis posture, shared-vs-local topology, and restore pressure
- Masked recent-activity audit feed with action/source/limit filters
- Server-side retained history charts for detections, errors, scan latency, and token backlog
- Polling controls plus snapshot export
- Operational filters for integration, rule pack, and entity type
- Refraction-flow explainer

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
