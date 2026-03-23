---
sidebar_position: 10
title: MCP with JetBrains
---

# MCP with JetBrains

JetBrains-based workflows follow the same runtime boundary as VS Code: Spring Prism protects the
Java application, not the IDE itself.

## Best Fit

Use this setup when:

- you develop or run the Spring Boot app from IntelliJ IDEA
- your MCP server runs locally as a subprocess
- you want a repeatable local integration flow for development or demos

## Recommended Transport

Use `stdio` for local MCP servers.

## Minimal Configuration

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    mcp:
      enabled: true
      transport: stdio
      stdio:
        command: java
        args:
          - -jar
          - fake-mcp-server.jar
```

## Verification Checklist

- Start the Spring Boot app from IntelliJ.
- Execute one MCP-driven action.
- Confirm the external MCP process sees only Prism tokens.
- Confirm the application-side result restores the original value.
- Check the dashboard or `/actuator/prism` for MCP timing visibility.

## Troubleshooting

- If IntelliJ starts the app with a different environment than your shell, verify `PRISM_APP_SECRET`.
- If the MCP process depends on local files, set `spring.prism.mcp.stdio.working-directory`.
- If the MCP process depends on environment variables, provide them through `spring.prism.mcp.stdio.env[...]`.
