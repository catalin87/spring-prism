# Migration Guide

This guide summarizes the externally visible changes made while closing phases 1-3.

## Backward Compatibility Contract

Spring Prism `v1.1.0` is a minor release and keeps `v1.0.0` integrations working by default.

- The legacy `PrismMetricsSnapshot` constructor remains available for custom integrations compiled
  against `v1.0.0`.
- Legacy strict-mode properties remain functional, but are deprecated in favor of
  `spring.prism.failure-mode`.
- Custom `PrismRulePack` beans no longer become active automatically unless they explicitly opt in
  through `isAutoDiscoverable()`.
- The starter now prefers the modular `prism-rulepack-common` baseline for `UNIVERSAL` detection,
  while legacy in-core `UniversalRulePack` remains available for direct `prism-core` consumers.
- In Spring applications, inject `@Qualifier("springPrismRulePacks") List<PrismRulePack>` when you
  need the resolved active rule packs after starter locale selection and filtering.
- Injecting plain `List<PrismRulePack>` gives you the available rule pack beans from the context,
  which can include optional modular contributions and compatibility fallbacks.

:::tip[Deprecation]
Property `spring.prism.security-strict-mode` is deprecated and will be removed in `v2.0.0`. Use
`spring.prism.failure-mode` instead.
:::

## Current Integration Shape

- Prefer the Spring Boot starter as the default integration entrypoint.
- Spring AI support is exposed through `PrismChatClientAdvisor`.
- LangChain4j support is exposed through `PrismChatModel` and `PrismStreamingChatModel`.

## Starter Defaults

- `spring.prism.enabled` defaults to `true`.
- `spring.prism.ttl` defaults to `30m`.
- `spring.prism.locales` defaults to `UNIVERSAL`.
- `spring.prism.failure-mode` defaults to `FAIL_SAFE`.
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

- The starter now exposes `spring.prism.vault.type` with `auto`, `in-memory`, and `redis`.
- `auto` preserves the previous ergonomic behavior:
  if a `StringRedisTemplate` bean is present, the starter switches to `RedisPrismVault`
  automatically.
- `redis` is the recommended explicit choice for multi-node deployments and fails fast when no
  shared Redis bean is configured.
- `in-memory` keeps `DefaultPrismVault` even if Redis is on the classpath.

## Recommended Upgrade Path

1. Move to `prism-spring-boot-starter` if you were wiring components manually.
2. Set an explicit `spring.prism.app-secret`.
3. Move from `spring.prism.security-strict-mode` to `spring.prism.failure-mode` when you want
   explicit policy control. Use `FAIL_CLOSED` for hard blocking and `FAIL_SAFE` to preserve the
   legacy default.
4. Choose `spring.prism.vault.type=redis` for multi-node deployments and keep all nodes on the
   same app secret and shared Redis infrastructure.
5. Verify your active locales and disabled rules explicitly in configuration.
6. If your application defines custom `PrismRulePack` beans and you want them auto-activated by the
   starter, override `isAutoDiscoverable()` to return `true`.
7. For LangChain4j applications, expose one delegate chat bean and let the starter wrap it.
8. If you instantiate `UniversalRulePack` directly from `prism-core`, you can keep doing so in
   `1.x`; the modular starter path is additive and does not remove the legacy class until `2.0.0`.
