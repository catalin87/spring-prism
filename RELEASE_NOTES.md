# Spring Prism 1.2.0 Release Notes

**Release date:** 2026-05-21
**Theme:** Dynamic Rule Engine — runtime rule-set updates without restart.

Spring Prism 1.2.0 introduces a hot-reloadable rule registry, an actuator-driven
management endpoint, fail-safe execution semantics, and first-class Micrometer
observability. Every change in this release is **additive and binary-backward
compatible** with 1.1.x: existing constructors, bean signatures, and public APIs
continue to work unchanged.

---

## Highlights

### 1. Dynamic Rule Engine — Hot-Reloading Support

Rule packs are now managed through `PrismRulePackRegistry`, a thread-safe holder
that publishes the active `List<PrismRulePack>` as an atomic, immutable
snapshot. Operators can replace the active rule set at runtime without
restarting the application:

```bash
curl -X POST http://localhost:8080/actuator/prism-rules \
     -H 'Content-Type: application/json' \
     -d '["europeRulePack","customMedicalRulePack"]'
```

The endpoint resolves requested names against `PrismRulePack` beans already
registered in the Spring context — arbitrary detector definitions cannot be
injected over HTTP. Read operations expose both the currently active packs and
the discoverable pack-bean names, so operators can introspect the system before
issuing a swap.

> **Security note:** The endpoint is intentionally unguarded by Spring Prism
> itself. Host applications **must** authenticate and authorise calls to
> `/actuator/prism-rules`, typically by binding actuator endpoints to an
> internal management port or requiring an operator role.

### 2. Zero-Lock Architecture — High Performance Reads

The registry is engineered for the asymmetry of real PII-scanning workloads:
**very rare writes** (a few operational swaps per day) against **very frequent
reads** (every request). The active snapshot is held in an `AtomicReference`,
so:

- `snapshot()` performs a **single volatile read** with no locks and no
  allocation.
- `replace(...)` publishes a defensive `List.copyOf(...)` via a single atomic
  store — readers see either the old list or the new list, never a partial
  update.
- Concurrent scans that capture the snapshot once at the start of a `tokenize`
  call are isolated from any swap that lands mid-operation: the in-flight scan
  iterates the list it captured; the next scan picks up the new list.

A `RegistryConcurrencyStressTest` exercises this contract under sustained
multi-threaded write/read pressure and is wired into the build under the
`@Tag("stress")` JUnit tag.

### 3. Fail-Safe Resilience — Pre-flight Validation & Poison-Pill Isolation

A misconfigured or buggy rule pack can no longer corrupt the active scanner.
Two complementary mechanisms enforce the guarantee:

- **Pre-flight validation.** `PrismRulePackRegistry.replace(...)` runs a
  pluggable `PrismRulePackValidator` against every candidate list *before* the
  atomic publication. The default `PrismRulePackValidator.STRUCTURAL`
  implementation rejects null packs, blank names, packs whose `getDetectors()`
  itself throws (the canonical "poison pill"), null detector elements, and
  detectors with a blank entity type. A rejected swap leaves the previous
  snapshot fully intact and reports the failure through
  `PrismRegistryMetricsSink.onRegistryError(...)`.
- **Per-detector fail-safe execution.** Both `PrismTextScanner` variants
  (Spring AI and LangChain4j) wrap each `detector.detect(...)` call in a
  try-catch. A single detector failing at runtime fires
  `onDetectionError(...)` and the scan continues with the remaining
  detectors/packs. In `FAIL_CLOSED` mode the request is short-circuited with a
  structured protection exception, as before.

Heavyweight policy (regex compilation, naming conventions, etc.) is delegated
to host-supplied validator implementations — the registry itself contains no
detector-specific logic, preserving `prism-core`'s clean boundary.

### 4. Observability — Actuator & Micrometer Integration

When a Micrometer `MeterRegistry` is present in the application context, the
starter automatically registers three new meters:

| Meter | Type | Description |
| ----- | ---- | ----------- |
| `prism.registry.swap.count` | Counter | Successful `PrismRulePackRegistry.replace(...)` calls. |
| `prism.registry.swap.error.count` | Counter | Failed `PrismRulePackRegistry.replace(...)` calls (validator rejections, malformed input). |
| `prism.scanner.tokenize.duration` | Timer | `PrismTextScanner.tokenize()` latency, tagged by integration (`spring-ai`, `langchain4j`, `mcp-stdio`, `mcp-streamable-http`). |

The bridge is implemented through the `PrismRegistryMetricsSink` abstraction in
`prism-core` and is wired by `PrismRuntimeMetrics` in the starter — `prism-core`
itself does **not** depend on Micrometer.

Deployments without a `MeterRegistry` bean continue to operate exactly as
before: the in-memory dashboard endpoints (`/prism/metrics` and the
`prism-rules` actuator) remain fully functional and no meters are registered.

---

## New Public Types

| Module | Type | Purpose |
| ------ | ---- | ------- |
| `prism-core` | `PrismRulePackRegistry` | Thread-safe holder for the active rule packs. |
| `prism-core` | `PrismRegistryMetricsSink` | Decoupled observability hook for registry events. |
| `prism-core` | `PrismRulePackValidator` | Pre-flight check applied before every atomic swap. |
| `prism-core` | `PrismRulePackValidationException` | Distinguishes structural rejection from runtime failure. |
| `prism-spring-boot-starter` | `PrismRulePackRegistryEndpoint` | `/actuator/prism-rules` read/write endpoint. |

---

## Binary Compatibility

This release is **fully binary backward compatible** with 1.1.x. All new
functionality is additive:

- `PrismRulePackRegistry` exposes three constructors. The 1-arg
  `(List<PrismRulePack>)` and 2-arg `(List, PrismRegistryMetricsSink)`
  constructors are retained and chain to the new 3-arg variant with
  `PrismRulePackValidator.STRUCTURAL`. Existing call-sites recompile and
  link unchanged.
- `PrismRuntimeMetrics` retains its no-arg and `(int, int)` constructors
  alongside the new `(int, int, MeterRegistry)` variant.
- No existing method signatures, return types, or exception specifications
  have been altered.
- No existing bean names or qualifiers have been renamed.

A host upgrading from 1.1.x can drop in 1.2.0 with **no source or
configuration changes**; opt-in to the new features by:

- Defining a `PrismRulePack` bean and POSTing its name to
  `/actuator/prism-rules` to activate hot-reload.
- Adding a `MeterRegistry` bean to the context (Spring Boot Actuator does
  this by default when `micrometer-core` is on the classpath) to enable
  the new meters.
- Supplying a custom `PrismRulePackValidator` bean to override the default
  structural checks (e.g. with one that compiles regex patterns up front).

---

## Decoupling Guarantee

`prism-core` remains free of Micrometer and Spring imports. A grep across
`prism-core/src/main/java` for `io.micrometer` and `org.springframework`
returns zero matches. The Micrometer bridge lives entirely in the Spring Boot
starter and is wired through the `PrismRegistryMetricsSink` and
`PrismMetricsSink` abstractions, both of which are pure Java interfaces.

---

## Upgrade Notes

No migration is required. If you wish to enable the new actuator endpoint,
ensure the host application:

1. Has `spring-boot-starter-actuator` on the classpath.
2. Exposes the `prism-rules` actuator endpoint via
   `management.endpoints.web.exposure.include=prism-rules` (or `*`).
3. Restricts access to the endpoint through the host's security
   configuration — Spring Prism does not ship a security configuration.

---

## Acknowledgements

Thanks to everyone who contributed feedback on the 1.1.x release and helped
shape the dynamic rule-engine design.
