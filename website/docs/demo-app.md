# Unified Demo App

Spring Prism ships a unified demo application that lets you exercise the three supported integration surfaces from a single frontend:

- Spring AI
- LangChain4j
- MCP

The demo is designed for:

- live product demos
- manual testing with realistic prompts
- visually verifying sanitize to model to restore behavior
- opening the embedded dashboard side by side while the flow runs

## What It Shows

For every request, the demo app exposes:

- the original prompt entered in the UI
- the sanitized payload that leaves the trusted application boundary
- the raw mock model response
- the restored response returned back into the app
- the active rule packs used for the run

The mock model intentionally echoes the sanitized payload with a prefix and suffix so the restore path is easy to inspect.

## Frontend

The app serves a lightweight React frontend from:

```text
/demo-lab/index.html
```

It includes:

- integration tabs for Spring AI, LangChain4j, and MCP
- a prompt composer
- rule-pack checkboxes
- direct links to the embedded dashboard and actuator metrics snapshot

## Running It

Build the local snapshots once from the repository root:

```bash
mvn install -DskipTests
```

Then start the demo app directly from its own Maven project:

```bash
mvn -f prism-examples/demo-app/pom.xml spring-boot:run
```

Then open:

- `http://localhost:8080/demo-lab/index.html`
- `http://localhost:8080/prism/index.html`

## Notes

- The demo app does not require a real LLM API key.
- The MCP section uses a local mock HTTP endpoint through `prism-mcp`.
- The existing dedicated example apps remain available for focused framework-specific samples.
