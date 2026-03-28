![Spring Prism Social Banner](social_banner.jpg)

# Spring Prism

> The reversible privacy firewall for Spring AI, LangChain4j, and MCP client flows.
> [Read the Documentation](https://catalin87.github.io/spring-prism/)

Spring Prism sits between your application and an untrusted LLM or tool endpoint. It detects
supported PII, replaces it with signed Prism tokens before the payload leaves your boundary, and
restores original values when the response comes back.

## Why teams adopt it

- **`prism-core` is zero-dependency** and strictly decoupled from Spring.
- **Enterprise-grade Redis vaults** support cluster-safe token restoration.
- **Circuit Breaker support** (`FAIL_CLOSED`) for production environments requiring high security.
- **Modular Regional Rule Packs** (Big 7: RO, US, PL, NL, DE, GB, FR) with checksum-backed validation.
- **Optional NLP extensions** (Heuristic/Hybrid) for person-name detection outside the deterministic core.
- **Optimized for RAG** through segment-aware scanning and hot-path tokenization.


## 5-Minute Start

Add the starter:

```xml
<dependency>
  <groupId>io.github.catalin87.prism</groupId>
  <artifactId>prism-spring-boot-starter</artifactId>
  <version>1.1.0</version>
</dependency>
```

Use the default local path:

```yaml
spring:
  prism:
    enabled: true
    app-secret: ${PRISM_APP_SECRET}
    failure-mode: FAIL_SAFE # Options: FAIL_SAFE, FAIL_CLOSED
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
  <version>1.1.0</version>
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

For a practical guide to where that model should live and how to mount it in containers or
multi-node deployments, see `website/docs/nlp-model-guide.md`.

## Documentation map

Recommended reading order:

1. `website/docs/quickstart.md`
2. `website/docs/configuration.md`
3. `website/docs/distributed-deployments.md`
4. `website/docs/nlp-extensions.md`
5. `website/docs/nlp-model-guide.md`
6. `website/docs/release-readiness.md`

Useful operational guides:

- `website/docs/grafana.md`
- `website/docs/troubleshooting.md`
- `website/docs/performance.md`
- `website/docs/demo-app.md`

## Clone-and-play enterprise lab

From the repository root, start the repo-only Enterprise Lab with:

```bat
run-demo.cmd
```

This spins up two demo nodes, Redis, the public proxy, and Grafana with the Spring Prism Overview
dashboard preloaded. See `website/docs/demo-app.md` for the smoke-test path and troubleshooting.

## Runnable examples

Examples live under `prism-examples/`:

- `spring-ai-example`
- `langchain4j-example`
- `mcp-example`
- `demo-app` enterprise lab

These applications stay in the repository as runnable contributor assets and manual QA tooling.
They are not part of the published Maven library surface.

The Spring AI example now has configuration variants for:

- local default: `application.yml`
- shared Redis: `application-redis.yml`
- NLP heuristic: `application-nlp-heuristic.yml`
- NLP hybrid: `application-nlp-hybrid.yml`

## Published module surface

Published Maven modules in the `v1.1.0` train:

- `prism-core`
- `prism-rulepack-common`
- `prism-rulepack-ro`
- `prism-rulepack-us`
- `prism-rulepack-pl`
- `prism-rulepack-nl`
- `prism-rulepack-gb`
- `prism-rulepack-fr`
- `prism-rulepack-de`
- `prism-spring-ai`
- `prism-langchain4j`
- `prism-mcp`
- `prism-spring-boot-starter`
- `prism-extensions-nlp`
- `prism-dashboard`

Repo-only contributor modules:

- `prism-integration-tests`
- `prism-benchmarks`
- `prism-examples`
- `demo-app`

Deferred:

- MCP server-side interception
- broader enterprise follow-ups such as multi-tenant vault strategy and cluster-wide dashboard
  aggregation

## Compatibility note for `1.x`

- `prism-rulepack-common` is now the default starter baseline for `UNIVERSAL` detection.
- Legacy `UniversalRulePack` remains functional for direct `prism-core` consumers in `1.x` and is
  kept as a compatibility shim while modular rulepacks are introduced.
- Legacy `EuropeRulePack` also remains functional for direct `prism-core` consumers in `1.x`, but
  is now deprecated and scheduled for removal in `2.0.0`.
- Existing `spring.prism.locales` values and aliases such as `UNIVERSAL`, `EN`, `US`, `EU`, `RO`,
  `ROU`, `PL`, `POL`, `NL`, `NLD`, `DE`, `DEU`, `GB`, `GBR`, `FR`, and `FRA` continue to work.
- Regional rulepack modules are additive and opt-in. If a regional module is absent, locale
  selection falls back to the legacy in-core family packs in `1.x`.

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

Enterprise lab sandbox, outside the main examples reactor:

```bash
run-demo.cmd
```

Unix shell alternative:

```bash
./run-demo.sh
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
