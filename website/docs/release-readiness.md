# Release Readiness

Spring Prism currently ships a production-ready library baseline for the privacy firewall itself.

The current `main` branch validates this baseline with a full root:

```bash
mvn clean verify
```

## Shipped Today

The following modules are part of the supported library surface:

| Module | Role | Status |
| --- | --- | --- |
| `prism-core` | Zero-dependency detectors, rule packs, vault, tokenization, streaming buffer | Supported |
| `prism-spring-ai` | Spring AI advisor integration for synchronous and streaming chat flows | Supported |
| `prism-langchain4j` | LangChain4j `ChatModel` and `StreamingChatModel` wrappers | Supported |
| `prism-mcp` | MCP client-side protection for stdio and Streamable HTTP transports | Supported |
| `prism-spring-boot-starter` | Spring Boot properties, auto-configuration, Redis selection, metrics surface | Supported |
| `prism-dashboard` | Embedded dashboard with retained history, filters, exports, and alerts | Supported |
| `prism-examples` | Runnable Spring Boot sample apps for Spring AI, LangChain4j, and MCP | Supported |

## Deferred

The following surfaces are intentionally outside the current release boundary:

| Surface | Status |
| --- | --- |
| MCP server-side interception | Deferred |
| Optional NLP/person-name detection | Deferred |

## What We Validate

The current verification baseline covers:

- full root `mvn verify`
- `prism-core` JaCoCo coverage gate at `90%+`
- Spotless, Checkstyle, and Enforcer policies across the reactor
- WireMock-backed Spring AI integration tests
- LangChain4j wrapper tests
- MCP stdio + Streamable HTTP transport tests with structured payload sanitization/restoration
- starter auto-configuration tests, including Redis-absent startup safety
- runnable Spring AI, LangChain4j, and MCP example applications that boot and prove redaction/restoration
- a dedicated `prism-benchmarks` JMH module for scan, vault, streaming, and Redis-vault measurements

## Release Profile

The Maven `release` profile is configured to attach:

- source jars
- javadoc jars
- GPG signing
- Central publishing metadata

For local release-profile verification without publishing, use:

```bash
mvn -Prelease -Dgpg.skip=true -DskipTests package
```

## Notes

- `spring.prism.app-secret` must be overridden in every real deployment.
- Fail-open remains the default behavior; strict mode is opt-in through `spring.prism.security-strict-mode=true`.
- Redis is the supported distributed vault path for this release boundary.
- MCP support in this release boundary covers the client role first. Server-side MCP interception remains a later milestone.
