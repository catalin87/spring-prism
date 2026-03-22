# Configuration Guide

Spring Prism is configured using standard Spring Boot property files such as `application.yml`.

## Configuration Properties

| Property | Default | Description |
|---|---|---|
| `spring.prism.enabled` | `true` | Globally enable or disable Spring Prism. |
| `spring.prism.security-strict-mode` | `false` | If true, any failure in detection or vaulting will result in a hard failure (Fail Closed). |
| `spring.prism.app-secret` | `spring-prism-change-me` | The HMAC secret used to sign Prism tokens. Override this in every real deployment. |
| `spring.prism.ttl` | `30m` | The time-to-live for vault entries. Invalid values fall back to the starter default. |
| `spring.prism.locales` | `UNIVERSAL` | The active locale set. Common values include `UNIVERSAL`, `EU`, `RO`, `PL`, `DE`, `GB`, `EN`, and `US`. |
| `spring.prism.disabled-rules` | empty | Entity types to suppress from the resolved rule packs, such as `EMAIL` or `SSN`. |
| `spring.prism.custom-rules[n].name` | empty | Entity type name for a property-backed custom regex detector. |
| `spring.prism.custom-rules[n].pattern` | empty | Regex pattern for a property-backed custom regex detector. Blank custom rules are ignored. |

## Starter-First Setup

Add the starter dependency:

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Then configure the starter:

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    security-strict-mode: false
    ttl: 30m
    locales: EU,EN
    disabled-rules: SSN
    custom-rules:
      - name: INTERNAL_ID
        pattern: "ID-\\d{5}"
```

The starter auto-configures:
- the active `PrismRulePack` list
- the `PrismVault`
- the `PrismChatClientAdvisor`
- primary LangChain4j `PrismChatModel` and `PrismStreamingChatModel` wrappers when a single delegate `ChatModel` or `StreamingChatModel` bean is present
- the runtime metrics endpoint at `/actuator/prism`

When a `StringRedisTemplate` bean is present, the starter switches to the Redis-backed vault automatically. Otherwise it keeps the default in-memory vault.

## Spring AI Runtime Behavior

Once configured, Prism sanitizes the outbound chat content before dispatching it to the LLM and restores Prism tokens on the way back. The underlying text sent to the model will look like:

```text
My email is <PRISM_EMAIL_h8a2...]
```

The response returned to the application has the original values restored transparently.

## LangChain4j Runtime Behavior

If your application already defines a single LangChain4j `ChatModel` bean, the starter publishes a
primary `PrismChatModel` wrapper around it. The same pattern applies to a single
`StreamingChatModel` bean, which is wrapped as a primary `PrismStreamingChatModel`.

This keeps your original delegate bean available while making the Prism-protected wrapper the
default injection target for application code.
