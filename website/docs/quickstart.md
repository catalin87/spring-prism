---
sidebar_position: 2
---

# Quickstart

This is the fastest production-shaped path into Spring Prism.

## 1. Add the starter

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

## 2. Configure the local default path

```yaml
spring:
  prism:
    enabled: true
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: auto
    locales: UNIVERSAL
```

Use a non-default secret in every real environment.

## 3. Keep your normal Spring AI wiring

```java
@Configuration
public class AiConfiguration {

  @Bean
  ChatClient protectedChatClient(ChatClient.Builder builder) {
    return builder.build();
  }
}
```

The starter auto-configures the Prism advisor and active rule packs.

## 4. Choose the right runtime path

### Local or single-node

```yaml
spring:
  prism:
    vault:
      type: in-memory
```

### Multi-node or Kubernetes

```yaml
spring:
  data:
    redis:
      host: redis.internal
      port: 6379
  prism:
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: redis
    ttl: 30m
```

### Optional person-name redaction

Add:

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-extensions-nlp</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Then start with the conservative mode:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: heuristic
```

Use `hybrid` only when you also provide a valid OpenNLP model resource.
For the actual file placement and deployment path, use [NLP Model Guide](/docs/nlp-model-guide).

## 5. Verify the runtime posture

With Actuator:

- `/actuator/prism`

Without Actuator:

- `/prism/metrics`

For distributed deployments, the important fields are:

- `configuredVaultMode`
- `vaultType`
- `sharedVaultReady`
- `vaultReadinessStatus`

## 6. Keep the examples close

Canonical example configurations live in:

- `prism-examples/spring-ai-example/src/main/resources/application.yml`
- `prism-examples/spring-ai-example/src/main/resources/application-redis.yml`
- `prism-examples/spring-ai-example/src/main/resources/application-nlp-heuristic.yml`
- `prism-examples/spring-ai-example/src/main/resources/application-nlp-hybrid.yml`

## Next pages

- [Configuration](/docs/configuration)
- [Distributed Deployments](/docs/distributed-deployments)
- [NLP Extensions](/docs/nlp-extensions)
- [NLP Model Guide](/docs/nlp-model-guide)
- [Release Readiness](/docs/release-readiness)
