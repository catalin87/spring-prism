---
sidebar_position: 1
---

# Introduction

Spring Prism is a reversible PII pseudonymization firewall for Spring AI, LangChain4j, and MCP
client flows.

It detects supported sensitive values before they leave your trusted application boundary, replaces
them with signed Prism tokens, and restores original values when those tokens return in a model or
tool response.

## Key Features

- Automated detection through universal and locale-specific rule packs
- HMAC-SHA256 Prism tokens with vault-backed restoration
- Spring AI and LangChain4j integration paths through the starter
- Redis-backed shared vault support for distributed deployments
- Optional NLP extensions for person-name redaction without changing `prism-core`
- Java 21 baseline with a performance-oriented implementation
- zero-dependency `prism-core`

## Getting Started

Start with the Spring Boot starter:

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Then continue with:

- [Quickstart](/docs/quickstart)
- [Configuration](/docs/configuration)
- [Production Playbook](/docs/production-playbook)

## How it Works

1. **Scan**: Intercepts the prompt sent to the LLM.
2. **Redact**: Identifies PII using a `PrismRulePack` and replaces it with unique cryptographic tokens.
3. **Vault**: Stores the mapping between tokens and original values in a secure, TTL-managed `PrismVault`.
4. **LLM**: The LLM receives "sanitized" text.
5. **Restore**: When the LLM responds, Spring Prism scans for tokens and replaces them with the original values from the vault.

## Canonical onboarding assets

Use these as the first real samples:

- `prism-examples/spring-ai-example`
- `prism-examples/langchain4j-example`
- `prism-examples/mcp-example`
- [Enterprise Lab](/docs/demo-app)

These apps live in the repository for onboarding, contributor workflows, and manual verification.
They are not part of the published Maven library surface.

If you are adopting MCP in real tools, continue with [MCP Tooling Guides](/docs/mcp-tooling).
