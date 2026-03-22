# Configuration Guide

Spring Prism is configured using standard Spring Boot property files (e.g., `application.yml`).

## Configuration Properties

| Property | Default | Description |
|---|---|---|
| `spring.prism.enabled` | `true` | Globally enable or disable Spring Prism. |
| `spring.prism.security-strict-mode` | `false` | If true, any failure in detection or vaulting will result in a hard failure (Fail Closed). |
| `spring.prism.security-salt` | (random) | The salt used for deterministic HMAC signature calculation. This value MUST be kept secret. |
| `spring.prism.vault-ttl-seconds` | `3600` | The Time-to-Live for PII in the vault. |
| `spring.prism.rule-pack` | `UNIVERSAL` | The active rule set: `UNIVERSAL` or `EUROPE`. |

## Spring AI Integration

To use Spring Prism with your `ChatClient`, use the `PrismChatClientAdvisor`:

```java
ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        new PrismChatClientAdvisor(List.of(rulePack), prismVault, ObservationRegistry.NOOP))
    .build();

String response = chatClient.prompt()
    .user("My email is user@corp.local")
    .call()
    .content();
```

The underlying text sent to the LLM will be:

```text
My email is <PRISM_EMAIL_h8a2...]
```

But the `response` string returned to the `chatClient` will have the original email restored.
