---
sidebar_position: 40
---

# MCP Tooling Guides

These guides focus on one goal: getting Spring Prism MCP protection working in real developer tools without guesswork.

Use this section when you want copy-paste setup help for:

- VS Code
- JetBrains IDEs such as IntelliJ IDEA
- GitHub Copilot and agent-style tooling
- local `npx` or binary-backed MCP servers
- Docker-backed or hosted MCP HTTP endpoints

## Before You Start

Make sure your Spring Boot app already has:

- `prism-spring-boot-starter`
- `prism-mcp`
- `spring.prism.mcp.enabled=true`
- either `stdio` or `streamable-http` transport configured

The runnable reference app lives in `prism-examples/mcp-example`.

## Pick the Right Guide

- [VS Code MCP Setup](./mcp-vscode)
- [JetBrains MCP Setup](./mcp-jetbrains)
- [GitHub Copilot MCP Setup](./mcp-copilot)
- [Docker and Hosted MCP Setup](./mcp-docker-hosted)

## Quick Verification Checklist

After you configure a tool, verify the integration with this short checklist:

1. Start the Spring Prism application with MCP enabled.
2. Trigger one MCP request containing test PII such as an email address.
3. Confirm the external MCP server sees Prism tokens instead of raw PII.
4. Confirm the application receives restored values on the response path.
5. Check `/actuator/prism` or `/prism/metrics` and verify MCP timings appear.

If one of those steps fails, jump to the troubleshooting section in the relevant guide.
