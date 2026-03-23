# MCP Integration

Spring Prism now protects Java applications acting as **MCP clients**.

## What Ships Today

The current MCP support lives in `prism-mcp` and focuses on the client boundary first:

- outbound payload sanitization before JSON-RPC requests leave the trusted application
- inbound token restoration after MCP responses return
- recursive walking of strings inside nested maps, lists, prompt fields, tool arguments, and textual result payloads
- protocol-aware handling for common MCP shapes such as tool arguments, message content blocks, and JSON-encoded structured argument payloads
- stronger `Streamable HTTP` event parsing for multi-line `data:` frames, progress events, request-id correlation, and `[DONE]` style trailers
- fail-open behavior by default, with strict mode available through starter properties
- runtime metrics aligned with the existing Spring AI and LangChain4j integrations
- dashboard timing cards and history rollups now include MCP transport activity instead of treating MCP traffic as invisible background load

## Supported Transports

Spring Prism currently supports the two standard MCP transport shapes:

- `stdio` for local subprocess servers launched via `npx`, binaries, or scripts
- `Streamable HTTP` for hosted, internal, or containerized MCP servers

Docker is treated as a deployment detail on top of those transports, not as a separate Prism integration layer.

## Starter Properties

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

For hosted MCP endpoints:

```yaml
spring:
  prism:
    mcp:
      enabled: true
      transport: streamable-http
      http:
        base-url: http://localhost:8081/mcp
```

Available MCP-specific settings:

- `spring.prism.mcp.enabled`
- `spring.prism.mcp.security-strict-mode`
- `spring.prism.mcp.transport`
- `spring.prism.mcp.http.base-url`
- `spring.prism.mcp.stdio.command`
- `spring.prism.mcp.stdio.args`
- `spring.prism.mcp.stdio.working-directory`
- `spring.prism.mcp.stdio.env[...]`

If `spring.prism.mcp.security-strict-mode` is not set, the starter inherits the global `spring.prism.security-strict-mode` value.

## Example App

The runnable sample lives in `prism-examples/mcp-example`.

Its integration test uses a fake local stdio MCP server to prove:

- the external MCP server only sees tokenized values
- the application receives restored PII on the way back in
- the embedded dashboard and `/actuator/prism` surface still work with MCP traffic in the mix

## Tooling Guides

Spring Prism now ships dedicated setup guides for real developer tooling and deployment shapes:

- [MCP Tooling Guides](./mcp-tooling)
- [VS Code MCP Setup](./mcp-vscode)
- [JetBrains MCP Setup](./mcp-jetbrains)
- [GitHub Copilot MCP Setup](./mcp-copilot)
- [Docker and Hosted MCP Setup](./mcp-docker-hosted)

These guides are intentionally practical and focus on repeatable setup, verification, and troubleshooting rather than protocol theory.

## Current Boundary

This milestone intentionally covers the **MCP client role** first.

Deferred MCP work:

- full server-side interception wiring, although a reusable server interceptor foundation now exists in `prism-mcp`
- binary payload rewriting
- richer long-lived streaming response session management beyond the current request-correlated transport support
