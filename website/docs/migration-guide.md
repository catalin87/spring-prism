# Migration Guide

This guide summarizes the externally visible changes made while closing phases 1-3.

## Current Integration Shape

- Prefer the Spring Boot starter as the default integration entrypoint.
- Spring AI support is exposed through `PrismChatClientAdvisor`.
- LangChain4j support is exposed through `PrismChatModel` and `PrismStreamingChatModel`.

## Starter Defaults

- `spring.prism.enabled` defaults to `true`.
- `spring.prism.ttl` defaults to `30m`.
- `spring.prism.locales` defaults to `UNIVERSAL`.
- blank or missing `spring.prism.app-secret` falls back to `spring-prism-change-me`, but real
  deployments must override it.

## Spring AI

- The current Spring AI path is starter-first.
- If you wire manually, use the current constructor shape:

```java
new PrismChatClientAdvisor(List.of(rulePack), prismVault, ObservationRegistry.NOOP)
```

## LangChain4j

- LangChain4j support now ships as a first-class module.
- If the starter sees a single delegate `ChatModel` bean, it publishes a primary
  `PrismChatModel` wrapper.
- If the starter sees a single delegate `StreamingChatModel` bean, it publishes a primary
  `PrismStreamingChatModel` wrapper.

## Redis Vault Selection

- The starter keeps `DefaultPrismVault` by default.
- If a `StringRedisTemplate` bean is present, the starter switches to `RedisPrismVault`
  automatically.

## Recommended Upgrade Path

1. Move to `prism-spring-boot-starter` if you were wiring components manually.
2. Set an explicit `spring.prism.app-secret`.
3. Verify your active locales and disabled rules explicitly in configuration.
4. For LangChain4j applications, expose one delegate chat bean and let the starter wrap it.
