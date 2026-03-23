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

## 1.0.0 Release Scope

Spring Prism `1.0.0` now covers the intended first production release surface:

- zero-dependency core redaction and restoration primitives
- Spring AI, LangChain4j, and MCP client-side integrations
- Redis-backed distributed vault support through the starter
- an embedded operator dashboard with live history, alerts, exports, and the redesigned Privacy Score
- runnable example applications and Docusaurus documentation for the supported transports and tooling flows

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
- embedded dashboard coverage for the redesigned control-plane UI and live Privacy Score rendering

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

## Final 1.0.0 Checklist

Use this as the final gate before cutting `v1.0.0`.

### Already Complete

- Product scope for `1.0.0` is implemented on `main`
- MCP client-side support is shipped and documented
- Dashboard redesign and Privacy Score are shipped
- Example apps build and boot in CI
- `release` profile produces `sources.jar` and `javadoc.jar`
- `prism-dashboard` now produces a valid Javadoc artifact
- GitHub secrets for Central publishing and GPG signing are configured
- Public signing key is committed in the repository

### Must Pass Right Before Tagging

- `main` is green in GitHub Actions
- No open release-blocking PRs remain
- Full local verification succeeds:

```bash
mvn clean verify
```

- Release-profile packaging succeeds without publishing:

```bash
mvn -Prelease -Dgpg.skip=true -DskipTests package
```

- Docusaurus docs build succeeds:

```bash
cd website && npm run build
```

### Central Publishing Readiness

- `io.github.catalin87` namespace is verified in Sonatype Central
- GitHub environment `maven-central-release` exists
- GitHub Action secrets exist and are current:
  - `OSSRH_USERNAME`
  - `OSSRH_TOKEN`
  - `GPG_PRIVATE_KEY`
  - `GPG_PASSPHRASE`
- Release workflow references the same signing key already registered publicly

### Release Cut

1. Update root `pom.xml` from `1.0.0-SNAPSHOT` to `1.0.0`
2. Commit with:

```bash
git add pom.xml
git commit -s -m "release: v1.0.0"
git push origin main
```

3. Tag and push:

```bash
git tag v1.0.0
git push origin v1.0.0
```

4. Monitor `release.yml` until:
   - build and tests pass
   - signing succeeds
   - Central publish succeeds
   - GitHub Release is created

### Immediately After Release

1. Bump root `pom.xml` to the next development version, for example `1.1.0-SNAPSHOT`
2. Commit and push:

```bash
git add pom.xml
git commit -s -m "chore: begin 1.1.0-SNAPSHOT development cycle"
git push origin main
```

## Notes

- `spring.prism.app-secret` must be overridden in every real deployment.
- Fail-open remains the default behavior; strict mode is opt-in through `spring.prism.security-strict-mode=true`.
- Redis is the supported distributed vault path for this release boundary.
- MCP support in this release boundary covers the client role first. Server-side MCP interception remains a later milestone.
- Prism tokens remain HMAC-SHA256 signed, and restoration only succeeds for valid vault-backed tokens produced inside the trusted application boundary.
