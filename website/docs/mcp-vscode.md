---
sidebar_position: 41
---

# VS Code MCP Setup

Use this guide when your team works with MCP-enabled tooling in Visual Studio Code.

Spring Prism does not replace the MCP server configuration inside VS Code. Instead, Prism protects the Java application that calls the MCP server.

## Recommended Setup

Use one of these two models:

- local `stdio` server started from your Spring Boot app
- hosted `Streamable HTTP` MCP endpoint reached by your Spring Boot app

## Local Stdio Example

Spring Prism side:

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: stdio
      stdio:
        command: npx
        args:
          - -y
          - your-mcp-server-package
```

VS Code side:

Configure the same MCP server in your VS Code MCP configuration so the editor can use it directly when needed. Keep the server command, arguments, and environment aligned with the setup you validate through Spring Prism.

## Hosted HTTP Example

Spring Prism side:

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: http://localhost:8081/mcp
```

This model is often easier when multiple developers use the same MCP server and you want a predictable endpoint for local testing.

## Verify It Works

1. Start your Spring Boot app.
2. Trigger an MCP call through the application.
3. Inspect the external MCP server request or fixture output.
4. Confirm raw PII is replaced with Prism tokens.
5. Confirm `/actuator/prism` shows MCP timings and activity.

## Troubleshooting

If VS Code works but Prism does not:

- verify `spring.prism.mcp.enabled=true`
- verify the configured transport matches the actual server shape
- verify `stdio.command` is resolvable from the app process, not only from your shell
- verify hosted URLs point to the actual MCP endpoint, not a docs or health URL

If Prism works but VS Code does not:

- re-check the VS Code MCP server configuration file
- make sure the server can start outside the IDE first
- confirm your MCP tool actually supports the selected transport
