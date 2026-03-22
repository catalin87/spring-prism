# Architecture Overview

Spring Prism follows a decoupled and strictly modular design to ensure portability, performance, and security.

## Modules

### `prism-core`
The heartbeat of the system.
- **Languages**: Java 21 (Virtual Threads for scanning).
- **Dependencies**: Zero Spring or AI dependencies.
- **Componentry**:
  - `PiiDetector`: Interface for regional or data-type specific detection.
  - `PrismRulePack`: Aggregate of detectors for a specific locale (e.g., `EuropeRulePack`).
  - `PrismVault`: Interface for secure, TTL-managed token-to-data mapping.
  - `TokenGenerator`: Deterministic HMAC-SHA256 signature generator for pseudonymization.
  - Core detectors stay deterministic and checksum-driven. NLP models are intentionally excluded from this module.

### `prism-spring-ai`
The Spring AI interception layer.
- **Frameworks**: Spring AI 1.x, Reactor, Micrometer Observation.
- **Entry Point**: `PrismChatClientAdvisor` wraps `ChatClient` flows for synchronous and streaming requests.
- **Streaming Safety**: Uses `StreamingBuffer` so fragmented Prism tokens can still be restored across SSE chunks.

### `prism-langchain4j`
The LangChain4j chat integration layer.
- **Frameworks**: LangChain4j chat APIs, Micrometer Observation.
- **Entry Point**: `PrismChatModel` and `PrismStreamingChatModel` decorate LangChain4j `ChatModel` and `StreamingChatModel`.
- **Boundary Rule**: Keeps Spring-specific concerns out of the integration so `prism-core` remains portable and zero-dependency.

### `prism-spring-boot-starter`
The Spring Boot 3 autoconfiguration bridge.
- **Frameworks**: Spring Boot 3.4+, Micrometer Observation.
- **Safety**: "Fail Open" by default (standard security practice) with Micrometer error metrics. "Fail Closed" only if `spring.prism.security-strict-mode=true`.
- **Deployments**: Uses the in-memory `DefaultPrismVault` by default and switches to `RedisPrismVault` automatically when a `StringRedisTemplate` bean is available.
- **Integrations**: Publishes `PrismChatClientAdvisor` for Spring AI and primary LangChain4j wrappers when delegate chat beans are present.
- **Optional NLP Extensions**: Person-name detection may be wired here or in `prism-spring-ai` through a lazily loaded backend such as Apache OpenNLP, keeping the core zero-dependency.

### `prism-dashboard`
The embedded observability surface.
- **Packaging**: Static assets are served from `META-INF/resources/prism/` inside the dashboard jar.
- **Data Source**: Reads the Prism runtime snapshot from `/actuator/prism` when Actuator is present and falls back to `/prism/metrics` otherwise.
- **Current Scope**: Dashboard shell, top-redacted metrics, vault/runtime health, and a visual refraction-flow explainer.

## The Request Lifecycle

1. **Interception**: A Spring AI advisor or LangChain4j chat wrapper captures the prompt.
2. **Detection**: `PiiDetector` implementations (e.g., `EmailDetector`) scan the text for PII candidates.
3. **Tokenization**: `TokenGenerator` creates deterministic HMAC-SHA256 tokens.
4. **Vaulting**: `PrismVault` stores the `original` ↔ `token` mapping.
5. **Redaction**: The original text is replaced with labels like `<PRISM_EMAIL_h8a2...]`.
6. **Execution**: The LLM processes the sanitized request.
7. **Detokenization**: The response is scanned for `<PRISM_...>` patterns, and `PrismVault` restores the original PII based on a valid HMAC signature.

## Security Controls

- **HMAC Signatures**: Tokens include an HMAC-SHA256 signature calculated with `spring.prism.app-secret`. This ensures that even if an attacker gains access to the LLM interaction logs, they cannot reverse the tokens without the application's secret key.
- **TTL Lifecycle**: Tokens in the vault automatically expire after a configurable period (default: 30 minutes).
- **Distributed Vault Option**: Redis-backed deployments preserve the same signature validation model as the local vault while allowing multiple application nodes to restore the same Prism token set.
- **No PII Logging**: Spring Prism emits Micrometer metrics (`gen_ai.prism.redacted.count`) rather than logging sensitive values.
