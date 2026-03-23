---
sidebar_position: 11
title: MCP with GitHub Copilot
---

# MCP with GitHub Copilot

Use this guide when GitHub Copilot or another agent-style tool participates in workflows that call
an MCP server behind your application.

## Boundary To Remember

Spring Prism does **not** modify Copilot itself. It protects the Java application in the MCP client
role.

That means:

- your application sends sanitized MCP requests
- the MCP server sees tokenized values
- your application restores Prism tokens on the way back in

## Best Fit

Use this when:

- your app orchestrates tool calls for Copilot-like workflows
- you want privacy controls around MCP tools without rewriting business code
- you want the dashboard and metrics surface to show MCP traffic as first-class runtime activity

## Transport Choice

- `stdio` for local tools or subprocess servers
- `streamable-http` for hosted MCP endpoints

## Verification

- confirm outbound MCP arguments are tokenized
- confirm inbound textual results are restored
- confirm MCP integration timing appears in the dashboard
- confirm fail-open or strict-mode behavior matches your chosen configuration

## Production Advice

- keep the global app secret private and rotated through environment management
- document which tool arguments are expected to contain PII
- prefer Redis vault mode for distributed deployments
