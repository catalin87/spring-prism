# Enterprise Lab

Spring Prism ships a repo-only **Enterprise Lab** that demonstrates the full `v1.1.0` story in a
clone-and-play sandbox:

- Spring AI, LangChain4j, and MCP payload shapes
- two application nodes
- Redis shared vault restore
- Grafana with the default Prism dashboard preloaded
- Big 7 regional rulepack selection
- NLP heuristic and hybrid modes
- `FAIL_SAFE` and `FAIL_CLOSED`
- cross-node restore and Redis outage simulation

This lab is for:

- manual release validation
- architecture demos
- contributor debugging
- realistic smoke tests before cutting a release

It is **repo-only** and is not part of the published Maven Central surface.

## Why it matters

This lab is more than a sample page. It is a release-grade validation and marketing asset that
lets teams see the operational value of Spring Prism without wiring their own infrastructure first.

It demonstrates:

- distributed tokenization and restoration with two application nodes
- Redis-backed shared vault behavior under healthy and degraded conditions
- `FAIL_SAFE` versus `FAIL_CLOSED` posture in a realistic UI
- Big 7 regional rulepack selection in one workspace
- optional NLP rollout paths for person-name protection
- operational visibility through the embedded Prism dashboard and Grafana

## Architecture at a glance

The one-command sandbox starts a small enterprise-shaped topology:

- `demo-node-a`
- `demo-node-b`
- `redis`
- `demo-proxy`
- `grafana`

Traffic enters through `demo-proxy`, while both app nodes share the same Redis-backed vault and
the same Prism app secret. That makes it possible to sanitize on one node and restore on the
other.

## One-command startup

On Windows CMD, start the full sandbox from the repository root with:

```bat
run-demo.cmd
```

On Unix-like shells:

```bash
./run-demo.sh
```

The command starts:

- `demo-node-a`
- `demo-node-b`
- `redis`
- `demo-proxy`
- `grafana`

Docker is the only required local prerequisite. No Maven, Node.js, Redis, or Grafana setup is
needed on the host machine.

Default URLs:

- `http://localhost:8080/lab/`
- `http://localhost:8080/prism/index.html`
- `http://localhost:3000`

Grafana is preprovisioned with the Spring Prism Overview dashboard and reads live data from the
same `/actuator/prism` snapshot exposed by the demo stack.

Use these stop commands when you are done:

```bat
stop-demo.cmd
```

```bash
./stop-demo.sh
```

## What the UI demonstrates

The React-based Enterprise Lab is intentionally a single-page command center rather than a fake
multi-page product shell.

The UI provides:

- cluster posture controls for:
  - failure mode
  - NLP mode
  - integration path
  - route strategy
- Big 7 rulepack selection in the sidebar
- cluster status cards for nodes and Redis shared vault state
- direct operations shortcuts for:
  - embedded dashboard
  - Grafana
  - raw Actuator metrics JSON
- preset payloads next to the raw input editor so testers can switch scenarios quickly
- a dual workspace for:
  - raw input
  - sanitized outbound content
  - mock model response
  - restored output
- a trace flow that shows tokenize, vault, masked payload, and restore phases
- a timeline with node-aware trace events
- runtime metrics including:
  - protected fields
  - active rules
  - shared vault state
  - request and response blocks
- an outage toggle that arms the next run for degraded-vault testing

## Manual validation checklist

Use the lab to verify:

- a mixed prompt is tokenized and restored correctly
- regional packs such as `RO`, `US`, `DE`, `GB`, `FR`, `NL`, and `PL` can be toggled explicitly
- `FAIL_CLOSED` blocks requests when Redis outage simulation is enabled
- cross-node restore uses the shared Redis vault
- Grafana reflects blocked requests and active rules after several runs
- Grafana reflects protected fields, shared-vault readiness, and history charts after several runs

## Recommended smoke-test path

If you want the highest-signal walkthrough, run this exact sequence:

1. Start the lab with `run-demo.cmd` or `./run-demo.sh`.
2. Open `http://localhost:8080/lab/`.
3. Run the `Mixed Enterprise Payload` preset with all default rulepacks enabled.
4. Switch `NLP Mode` to `HYBRID` and run the `NLP Person Names` preset.
5. Switch route mode to `CROSS_NODE` and verify sanitize-on-A / restore-on-B behavior.
6. Enable `Simulate Redis outage`, switch to `FAIL_CLOSED`, and run a protected payload again.
7. Open Grafana and confirm the dashboard reflects protected fields, active rules, and blocked
   requests.

## Grafana troubleshooting

If the Spring Prism Overview dashboard shows `No data` even though `http://localhost:8080/actuator/prism`
returns a populated JSON document, restart Grafana and hard-refresh the browser:

```bat
docker compose -f prism-examples\demo-app\compose.yaml restart grafana
```

```bash
docker compose -f prism-examples/demo-app/compose.yaml restart grafana
```

Then reload:

- `http://localhost:3000/d/spring-prism-overview/spring-prism-overview`
- `http://localhost:8080/prism/index.html`

## Marketing-ready talking points

When showing Spring Prism to evaluators or internal stakeholders, the lab lets you demonstrate:

- privacy controls before the LLM boundary, not after
- distributed restore with a shared Redis vault
- visible enforcement posture through `FAIL_CLOSED`
- international coverage through modular regional rulepacks
- optional person-name protection without polluting `prism-core`
- operational readiness through dashboards and metrics instead of console logs

## Notes

- The lab does not require a real LLM API key.
- The frontend is served from `/lab/`.
- The embedded Prism dashboard remains available at `/prism/index.html`.
- The focused framework examples remain available under `prism-examples/` for narrower debugging
  scenarios.
