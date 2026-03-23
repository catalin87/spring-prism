---
sidebar_position: 8
title: MCP Tooling Guides
---

# MCP Tooling Guides

Use these guides when you want Spring Prism to protect MCP traffic in real developer tools, not
just in the sample app.

## Pick Your Setup

- [VS Code](/docs/mcp-vscode)
- [JetBrains / IntelliJ](/docs/mcp-jetbrains)
- [GitHub Copilot and Agent Tooling](/docs/mcp-copilot)
- [Docker and Hosted Streamable HTTP](/docs/mcp-docker-hosted)

## What These Guides Cover

Each guide is intentionally practical:

- where MCP runs
- which transport to choose
- the minimum `spring.prism.mcp.*` configuration
- how to verify that Prism tokenizes outbound traffic and restores inbound results
- what to check first when something does not work

## Quick Rule of Thumb

- Use `stdio` for local subprocess tools such as `npx`, shell scripts, or local binaries.
- Use `streamable-http` when the MCP server is running elsewhere, including Docker or internal
  hosted deployments.
- Keep `spring.prism.app-secret` private and override it in every real deployment.
- Keep fail-open as the default unless you explicitly want strict blocking behavior.
