# Release Readiness

Spring Prism `v1.1.0-SNAPSHOT` now carries a broader production-ready release train than the
original `1.0.0` cut. This page is the operational definition of what the train currently ships,
what must stay green, and what every new feature must bring before merge.

The current release branch validates this baseline with:

```bash
mvn clean verify
```

## Shipped Today

The following modules are part of the supported published library surface:

| Module | Role | Status |
| --- | --- | --- |
| `prism-core` | Zero-dependency detectors, rule packs, vault, tokenization, streaming buffer | Supported |
| `prism-spring-ai` | Spring AI advisor integration for synchronous and streaming chat flows | Supported |
| `prism-langchain4j` | LangChain4j `ChatModel` and `StreamingChatModel` wrappers | Supported |
| `prism-mcp` | MCP client-side protection for stdio and Streamable HTTP transports | Supported |
| `prism-spring-boot-starter` | Spring Boot properties, auto-configuration, Redis selection, metrics surface, readiness signals | Supported |
| `prism-rulepack-ro/us/pl/nl/gb/fr/de` | Optional regional Big 7 rulepacks with checksum-backed validation | Supported |
| `prism-extensions-nlp` | Optional person-name redaction through heuristic, OpenNLP, and hybrid modes | Supported |
| `prism-dashboard` | Embedded dashboard with retained history, filters, exports, and alerts | Supported |

The following modules remain in the repository for contributor workflows, manual QA, and release
verification, but are not part of the published Maven library surface:

| Module | Role | Status |
| --- | --- | --- |
| `prism-integration-tests` | Testcontainers Redis and WireMock-backed multi-node release validation | Repo-only |
| `prism-benchmarks` | JMH benchmark suite for detector, vault, streaming, and Redis-backed paths | Repo-only |
| `prism-examples` | Runnable Spring Boot sample apps for Spring AI, LangChain4j, and MCP | Repo-only |
| `demo-app` | Unified manual-test application covering Spring AI, LangChain4j, MCP, dashboards, and rulepack selection | Repo-only |

## v1.1.0 Release Scope

Spring Prism `v1.1.0-SNAPSHOT` currently covers:

- zero-dependency core redaction and restoration primitives
- Spring AI, LangChain4j, and MCP client-side integrations
- Redis-backed distributed vault support through the starter
- large-context and RAG-oriented performance improvements
- optional NLP extensions for person-name redaction outside `prism-core`
- an embedded operator dashboard with live history, alerts, exports, and readiness posture
- runnable repository example applications and Docusaurus documentation for supported deployment paths

## Deferred

The following surfaces are intentionally outside the current release boundary:

| Surface | Status |
| --- | --- |
| MCP server-side interception | Deferred |
| Cluster-wide dashboard aggregation across nodes | Deferred |
| Multi-tenant vault separation | Deferred |
| Redis encryption-at-rest strategy | Deferred |

## What We Validate

The current verification baseline covers:

- full root `mvn verify`
- `prism-core` JaCoCo coverage gate at `90%+`
- Spotless, Checkstyle, and Enforcer policies across the reactor
- WireMock-backed Spring AI integration tests
- LangChain4j wrapper tests
- MCP stdio + Streamable HTTP transport tests with structured payload sanitization/restoration
- starter auto-configuration tests, including Redis-absent startup safety
- dedicated `prism-integration-tests` coverage for cross-node Redis restore, TTL expiry, noisy LLM restoration, and large-payload distributed flows
- dedicated `prism-integration-tests` coverage for Redis outage handling during tokenize and restore flows
- dedicated `prism-integration-tests` coverage for optional NLP disabled-by-default behavior and
  person-name restore flows
- runnable Spring AI, LangChain4j, and MCP example applications that boot and prove redaction/restoration
- a dedicated `prism-benchmarks` JMH module for scan, vault, streaming, and Redis-vault measurements
- embedded dashboard coverage for control-plane UI and readiness rendering
- Docusaurus documentation for onboarding, deployment, troubleshooting, and release posture

See [Integration Test Tracker](./integration-test-tracker.md) for the current suite inventory and branch-level status.

## Definition of Done for new features

Every new feature or runtime behavior change merged into the release line should include:

- module-local unit coverage
- `prism-integration-tests` coverage when the feature changes real runtime behavior across module
  boundaries
- Docusaurus documentation updates before merge
- `release_notes.v1.1.0.md` updates while the `v1.1.0-SNAPSHOT` train is active
- example config or example-app updates when the feature changes adoption guidance
- operationally truthful metrics, readiness signals, or troubleshooting notes when behavior changes
  operator expectations

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

## Final v1.1.0 Checklist

Use this as the final gate before cutting `v1.1.0`.

### Already Complete

- Redis-first distributed restore path is shipped and documented
- Large-context and RAG performance work is shipped and documented
- Optional NLP extension path is shipped and documented
- Example apps and integration suites exist for the supported release train paths
- Example apps, benchmarks, and the unified demo app remain repo-only contributor assets and are not published to Maven Central
- `release` profile produces `sources.jar` and `javadoc.jar`

### Must Pass Right Before Tagging

- `v1.1.0-SNAPSHOT` is green in GitHub Actions
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

- Release notes describe the final shipped scope
- README still reflects the recommended onboarding path

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

1. Update root `pom.xml` from `1.1.0-SNAPSHOT` to `1.1.0`
2. Commit with:

```bash
git add pom.xml
git commit -s -m "release: v1.1.0"
git push origin main
```

3. Tag and push:

```bash
git tag v1.1.0
git push origin v1.1.0
```

4. Monitor `release.yml` until:
   - build and tests pass
   - signing succeeds
   - Central publish succeeds
   - GitHub Release is created

### Immediately After Release

1. Bump root `pom.xml` to the next development version, for example `1.2.0-SNAPSHOT`
2. Commit and push:

```bash
git add pom.xml
git commit -s -m "chore: begin 1.2.0-SNAPSHOT development cycle"
git push origin main
```

## Notes

- `spring.prism.app-secret` must be overridden in every real deployment.
- Fail-open remains the default behavior through `spring.prism.failure-mode=FAIL_SAFE`.
- `spring.prism.security-strict-mode` and `spring.prism.mcp.security-strict-mode` are deprecated
  compatibility properties and will be removed in `v2.0.0`. Use `spring.prism.failure-mode`
  instead.
- Redis is the supported distributed vault path for this release boundary.
- optional NLP remains opt-in and must not change the deterministic default detector behavior
- MCP support in this release boundary covers the client role first. Server-side MCP interception remains a later milestone.
- Prism tokens remain HMAC-SHA256 signed, and restoration only succeeds for valid vault-backed tokens produced inside the trusted application boundary.
