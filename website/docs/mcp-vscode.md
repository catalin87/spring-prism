---
sidebar_position: 9
title: MCP with VS Code
---

# MCP with VS Code

This is the simplest “developer workstation” setup for Spring Prism MCP.

## When To Use It

Use this flow when:

- your MCP server runs locally through `npx`, a script, or a local binary
- your Spring Boot app runs on the same machine
- you want the quickest way to prove tokenization and restoration

## Recommended Transport

Use `stdio`.

Spring Prism protects the Java application in the MCP client role, so the external MCP process
sees tokenized values while your application receives restored values back inside the trusted
boundary.

## Minimal Configuration

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    mcp:
      enabled: true
      transport: stdio
      stdio:
        command: npx
        args:
          - -y
          - your-mcp-server-package
```

## What To Verify

1. Trigger one MCP-backed action from the application.
2. Confirm the MCP server receives `<PRISM_...>` tokens instead of raw PII.
3. Confirm the final application response restores the original value.
4. Open `/actuator/prism` or `/prism/metrics` and check that MCP metrics are present.

## Troubleshooting

- If the subprocess does not start, verify `command`, `args`, and working directory.
- If no redaction happens, confirm the input actually matches an enabled Prism detector.
- If tokens are not restored, verify the same app secret and vault are used throughout the flow.
- If you want hard failures instead of fail-open behavior, set `spring.prism.failure-mode=FAIL_CLOSED`.
  See [Configuration: Failure Mode](/docs/configuration#failure-mode).
