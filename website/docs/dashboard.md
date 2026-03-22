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
- Masked recent-activity audit feed with action/source/limit filters
- Refraction-flow explainer

## Demo mode

The dashboard includes a bundled fixture for visual verification without a live application runtime.

Use either:

- the **Open Demo Data** button in the UI
- or `/prism/?demo=1`

Demo mode loads the packaged [`demo-metrics.json`](/mnt/d/OpenSource/SpringPrism/spring-prism/prism-dashboard/src/main/resources/META-INF/resources/prism/demo-metrics.json) fixture and keeps all values masked.

## Privacy boundary

- The dashboard only renders aggregate counters, timing summaries, and masked audit events.
- It does not expose raw PII.
- Audit history is intentionally bounded in memory and currently retains the most recent 12 masked events.
