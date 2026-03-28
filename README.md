![Spring Prism Social Banner](social_banner.jpg)

# Spring Prism

> The reversible privacy firewall for Spring AI, LangChain4j, and MCP client flows.
> [Read the Documentation](https://catalin87.github.io/spring-prism/)

Spring Prism sits between your application and an untrusted LLM or tool endpoint. It detects
supported PII, replaces it with signed Prism tokens before the payload leaves your boundary, and
restores original values when the response comes back.

## Why teams adopt it

- `prism-core` stays zero-dependency and portable
- Spring Boot starter support keeps adoption simple
- the starter now brings the modular `prism-rulepack-common` baseline by default
- Redis-backed vaults support real multi-node deployments
- large-prompt and RAG paths are optimized for practical latency
- optional NLP extensions can add person-name redaction without changing the deterministic core

## 5-Minute Start

Add the starter:

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Use the default local path:

```yaml
spring:
  prism:
    enabled: true
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: auto
    locales: UNIVERSAL
```

If you already build a `ChatClient` through the standard Spring AI builder, the starter wires the
Prism advisor automatically:

```java
@Configuration
public class AiConfiguration {

  @Bean
  ChatClient protectedChatClient(ChatClient.Builder builder) {
    return builder.build();
  }
}
```

## Choose your deployment path

### Single node

Use this when the same application instance handles both tokenization and restoration.

```yaml
spring:
  prism:
    app-secret: ${PRISM_APP_SECRET}
    vault:
      type: in-memory
```

### Multi-node or Kubernetes

Use Redis when requests and restores can land on different nodes.

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

Add the NLP extension only when you want person-name coverage beyond the deterministic detector
set.

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-extensions-nlp</artifactId>
  <version>1.1.0-SNAPSHOT</version>
</dependency>
```

Conservative rollout:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: heuristic
```

Higher-recall rollout with an explicit model:

```yaml
spring:
  prism:
    extensions:
      nlp:
        enabled: true
        backend: hybrid
        model-resource: file:${PRISM_NLP_MODEL}
```

## Documentation map

Recommended reading order:

1. `website/docs/quickstart.md`
2. `website/docs/configuration.md`
3. `website/docs/distributed-deployments.md`
4. `website/docs/nlp-extensions.md`
5. `website/docs/release-readiness.md`

Useful operational guides:

- `website/docs/grafana.md`
- `website/docs/troubleshooting.md`
- `website/docs/performance.md`

## Runnable examples

Examples live under `prism-examples/`:

- `spring-ai-example`
- `langchain4j-example`
- `mcp-example`
- `demo-app`

The Spring AI example now has configuration variants for:

- local default: `application.yml`
- shared Redis: `application-redis.yml`
- NLP heuristic: `application-nlp-heuristic.yml`
- NLP hybrid: `application-nlp-hybrid.yml`

## Module surface

Supported modules in the `v1.1.0-SNAPSHOT` train:

- `prism-core`
- `prism-rulepack-common`
- `prism-spring-ai`
- `prism-langchain4j`
- `prism-mcp`
- `prism-spring-boot-starter`
- `prism-extensions-nlp`
- `prism-dashboard`
- `prism-integration-tests`
- `prism-examples`

Deferred:

- MCP server-side interception
- broader enterprise follow-ups such as multi-tenant vault strategy and cluster-wide dashboard
  aggregation

## Compatibility note for `1.x`

- `prism-rulepack-common` is now the default starter baseline for `UNIVERSAL` detection.
- Legacy `UniversalRulePack` remains functional for direct `prism-core` consumers in `1.x` and is
  kept as a compatibility shim while modular rulepacks are introduced.
- Existing `spring.prism.locales` values such as `UNIVERSAL`, `EN`, `US`, `EU`, `RO`, `PL`, `DE`,
  and `GB` continue to work.

## Validation baseline

Full verification:

```bash
mvn clean verify
```

Docs build:

```bash
cd website && npm run build
```

Benchmark packaging:

```bash
mvn -pl prism-benchmarks -am package -DskipTests
```

## Security posture

- Prism tokens are signed with HMAC-SHA256
- restore succeeds only for valid vault-backed tokens
- fail-open remains the default behavior unless strict mode is enabled
- raw PII should be monitored through metrics, not logs

## Governance and licensing

Spring Prism is dual-licensed under EUPL 1.2 and a commercial enterprise license. See
[LICENSE.md](./LICENSE.md), [GOVERNANCE.md](./GOVERNANCE.md), and
[CONTRIBUTING.md](./CONTRIBUTING.md) for the current project rules.
