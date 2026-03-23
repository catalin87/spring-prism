---
sidebar_position: 43
---

# GitHub Copilot MCP Setup

Use this guide when your organization wants to combine Spring Prism with GitHub Copilot MCP-enabled workflows.

The important boundary is simple:

- GitHub Copilot talks to MCP servers using its own MCP configuration
- Spring Prism protects the Java application when that application is the MCP client

## Good Deployment Shapes

The most practical setups are:

- local development with `stdio`
- shared internal deployment with `Streamable HTTP`

## Local Example

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

## Hosted Example

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: https://mcp.internal.example.com/mcp
```

## What To Verify

- Prism sanitizes outbound MCP request payloads before they leave the app
- Prism restores inbound result payloads before business code consumes them
- MCP activity appears in runtime metrics and dashboard history

## Organization Notes

Depending on your GitHub plan and policy settings, MCP server usage in Copilot may need explicit organization or enterprise enablement.

Treat that as a Copilot platform configuration concern, separate from Spring Prism itself.

## Troubleshooting

- If Copilot does not expose MCP features, check your organization policy first.
- If the app reaches the server but PII is not protected, verify the request flows through `prism-mcp` rather than a direct client call.
- If strict mode causes requests to fail, inspect MCP error metrics before widening the allowed behavior.
