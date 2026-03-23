---
sidebar_position: 44
---

# Docker and Hosted MCP Setup

Docker is not a separate Prism transport. In practice, Docker-backed MCP setups usually still look like one of these:

- a local `stdio` process started from the app
- a hosted `Streamable HTTP` endpoint exposed by a container

## Recommended Production Model

For teams beyond single-developer local testing, `Streamable HTTP` is usually the cleanest option.

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: https://mcp.internal.example.com/mcp
```

This keeps:

- application configuration simple
- MCP server hosting independent from the Java app lifecycle
- Docker and orchestration concerns outside the app process

## When To Use Stdio Instead

Use `stdio` when:

- the MCP server is a local tool
- startup simplicity matters more than shared hosting
- you want a fully local reproducible dev environment

## Production Checklist

- protect the hosted endpoint with your normal network and authentication controls
- keep secrets in environment variables or secret managers, not in committed config files
- use Redis for multi-node Prism deployments
- monitor MCP latency and failure metrics through `/actuator/prism` or the dashboard

## Troubleshooting

If Docker is healthy but Prism still fails:

- verify container port mapping and the exact MCP path
- verify the endpoint speaks `Streamable HTTP`, not an older or unrelated HTTP shape
- verify reverse proxies do not buffer or rewrite event-stream responses unexpectedly
