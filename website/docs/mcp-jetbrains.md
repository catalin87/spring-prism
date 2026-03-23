---
sidebar_position: 42
---

# JetBrains MCP Setup

Use this guide for IntelliJ IDEA and other JetBrains IDEs that expose MCP support through JetBrains AI tooling.

Spring Prism remains the privacy boundary inside your Java application. The IDE still needs its own MCP server connection details.

## Recommended Approach

For local development:

- use `stdio` when the MCP server is launched as a local process

For shared environments:

- use `Streamable HTTP` when the server is hosted centrally or behind Docker

## Local Stdio Example

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: stdio
      stdio:
        command: java
        args:
          - -jar
          - fake-mcp-server.jar
```

The same command shape should be reflected in your JetBrains MCP server configuration.

## Hosted HTTP Example

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: https://mcp.internal.example.com/mcp
```

## Verify It Works

Use the same verification flow as the example app:

1. send a payload that contains test PII
2. confirm the MCP server receives tokens
3. confirm the application receives restored content
4. check MCP metrics in the dashboard or actuator endpoint

## Troubleshooting

- If the IDE can connect but the app cannot, compare the exact command or URL configured on both sides.
- If HTTP works in the browser but not in Prism, verify you are calling the MCP endpoint itself.
- If `stdio` works in a terminal but not in Prism, confirm the working directory and environment variables under `spring.prism.mcp.stdio.*`.
