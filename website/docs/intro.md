---
sidebar_position: 1
---

# Introduction

Spring Prism is a reversible PII (Personally Identifiable Information) pseudonymization firewall for **Spring AI** and **LangChain4j**.

In the era of LLMs, data privacy is paramount. Spring Prism acts as a transparent layer that detects, redacts, and pseudonymizes sensitive data before it leaves your infrastructure, and restores it when it returns.

## Key Features

- **Automated Detection**: Built-in support for multiple locales (US, EU, Universal).
- **Reversible Tokens**: Non-reversible HMAC-SHA256 signatures ensure data security while allowing seamless restoration.
- **Spring AI Integration**: Plugs directly into `ChatClient` via Advisors.
- **LangChain4j Integration**: Wraps `ChatModel` and `StreamingChatModel` without leaking Spring dependencies into `prism-core`.
- **Type-Safe & Performance-Oriented**: Built with Java 21, utilizing Virtual Threads for efficient I/O-bound scanning.
- **Decoupled Architecture**: `prism-core` is zero-dependency, making it portable and lightweight.

## Getting Started

To include Spring Prism in your project, add the following dependency (once published to Maven Central):

```xml
<dependency>
  <groupId>io.github.catalin87</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Then use the runnable examples in `prism-examples/` as the canonical onboarding path:

- `demo-app`
- `spring-ai-example`
- `langchain4j-example`
- `mcp-example`

If you are adopting MCP in real tools, continue with the dedicated tooling docs:

- [MCP Tooling Guides](/docs/mcp-tooling)

For interactive product demos and manual privacy testing across all supported integrations, see the [Unified Demo App](/docs/demo-app).

## How it Works

1. **Scan**: Intercepts the prompt sent to the LLM.
2. **Redact**: Identifies PII using a `PrismRulePack` and replaces it with unique cryptographic tokens.
3. **Vault**: Stores the mapping between tokens and original values in a secure, TTL-managed `PrismVault`.
4. **LLM**: The LLM receives "sanitized" text.
5. **Restore**: When the LLM responds, Spring Prism scans for tokens and replaces them with the original values from the vault.
