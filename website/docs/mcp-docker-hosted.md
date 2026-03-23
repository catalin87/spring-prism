---
sidebar_position: 12
title: MCP with Docker or Hosted HTTP
---

# MCP with Docker or Hosted HTTP

Use this setup when the MCP server is not a local subprocess.

## Recommended Transport

Use `streamable-http`.

This is the right choice for:

- Dockerized MCP servers exposed over HTTP
- internal hosted MCP services
- shared remote MCP deployments

## Minimal Configuration

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: http://localhost:8081/mcp
```

## What To Verify

1. Send one request containing known PII through the MCP path.
2. Confirm the HTTP payload sent to the MCP server contains Prism tokens instead of raw values.
3. Confirm the final application response restores the original values.
4. Confirm MCP activity is visible in the dashboard and metrics endpoint.

## Deployment Advice

- Treat Docker as a packaging mode, not as a separate Prism transport.
- Use Redis vault mode for horizontally scaled deployments.
- Protect the hosted MCP endpoint with your normal network and application controls.
- Keep `spring.prism.app-secret` out of images and source control.

## Troubleshooting

- If the request reaches the server but no redaction happens, verify detector coverage for that payload.
- If streamed responses look incomplete, confirm the endpoint really behaves as Streamable HTTP.
- If restoration fails across nodes, move from in-memory vaulting to Redis.
