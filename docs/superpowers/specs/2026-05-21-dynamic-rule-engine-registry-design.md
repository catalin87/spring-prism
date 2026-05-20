# Dynamic Rule Engine — `PrismRulePackRegistry` (Turn 1)

- **Branch:** `feature/42-dynamic-rule-engine-mod-a`
- **Status:** Draft for review
- **Author:** Catalin (with Claude Code)
- **Date:** 2026-05-21
- **Scope:** Turn 1 of the dynamic rule-engine work — registry + scanner refactor only. Actuator endpoint, auto-configuration wiring, and `PrismChatClientAdvisor` constructor changes are explicitly out of scope.

## 1. Goal

Allow the active set of `PrismRulePack`s used by `PrismTextScanner` to be replaced at runtime (~2–3 swaps per day) without:

- Restarting the application.
- Acquiring any lock on the PII-detection hot path (`tokenize` runs on every request).
- Exposing in-flight requests to a partially-updated ruleset.

## 2. Non-goals

- Persisting rule packs to durable storage.
- Versioning, audit logging, or rollback of swaps (turn 3 may add these on top of the registry).
- Hot-reloading individual detectors (granularity is pack-level — confirmed during brainstorming).
- Changing the `PiiDetector` interface (it returns `List<PiiCandidate>` and stays that way).
- Changing any public constructor on `PrismChatClientAdvisor`.

## 3. Architectural decisions

### 3.1 Granularity: pack-level

The registry holds `List<PrismRulePack>`, not `List<PiiDetector>`. Reasons:

- Existing metrics are tagged with the pack name (`metricsSink.onDetected(pack.getName(), ...)`).
- `PrismRulePack` carries activation aliases and an auto-discovery flag that we want to preserve.
- Detector-level granularity would force us to synthesise a pack name on every metric emission.

### 3.2 Atomicity: snapshot-per-call

`PrismTextScanner.tokenize()` reads `registry.snapshot()` **once** at the top of the method and uses that snapshot for the rest of the call. A `replace()` mid-call has no effect on the in-flight request.

Rejected alternatives:

- *Snapshot-per-detector-iteration.* A single request could mix an old EU pack with a new EU pack — surprising and hard to test.
- *Copy-on-read.* Defeats the point — allocates on the hot path.

### 3.3 Data structure: `AtomicReference<List<PrismRulePack>>` over an immutable snapshot

- `AtomicReference.get()` is a single volatile read — wait-free and lock-free.
- `List.copyOf(...)` produces an immutable list, so readers can iterate without external synchronisation.
- `AtomicReference.set(...)` provides a happens-before edge from the writer to subsequent readers, so the contents of the new list are fully visible.

Rejected alternatives:

- `CopyOnWriteArrayList`. Adds per-element write semantics we don't need; iteration is still cheap, but `set(int, E)` and friends would imply mutability we want to forbid.
- `synchronized` blocks / `ReentrantReadWriteLock`. Violates the "zero-lock reads" requirement.
- `volatile List<PrismRulePack>` field on the scanner. Conflates lifecycle and scanning concerns; scanner is package-private, so we'd need a public facade anyway.

### 3.4 Location: `prism-core`

The new class lives next to `PrismRulePack` and `PiiDetector`:

```
prism-core/src/main/java/io/github/catalin87/prism/core/PrismRulePackRegistry.java
```

No Spring dependency. Both the spring-ai and langchain4j scanners depend on it.

## 4. Public API

### 4.1 `PrismRulePackRegistry`

```java
package io.github.catalin87.prism.core;

public final class PrismRulePackRegistry {

  /** Creates a registry seeded with the given packs. Defensively copies the input. */
  public PrismRulePackRegistry(@NonNull List<@NonNull PrismRulePack> initial);

  /** Returns the current immutable snapshot. Wait-free, lock-free. */
  public @NonNull List<@NonNull PrismRulePack> snapshot();

  /** Atomically replaces the current snapshot with a defensive copy of the input. */
  public void replace(@NonNull List<@NonNull PrismRulePack> packs);
}
```

**Thread-safety contract (to appear in Javadoc):**

- `snapshot()` is wait-free and lock-free; it is safe to call from any thread.
- `replace(...)` is atomic; concurrent readers see either the old list or the new list in full, never a partial state.
- The list returned by `snapshot()` is immutable; iterating it requires no external synchronisation.

**Error handling:**

- `new PrismRulePackRegistry(null)` → `NullPointerException` from `List.copyOf`.
- `replace(null)` → `NullPointerException` from `List.copyOf`.
- Either constructor or `replace` with a list containing nulls → `NullPointerException` from `List.copyOf`.
- `snapshot()` cannot throw.

These NPE behaviours are intentional fail-fast — a misconfigured swap must not silently succeed.

## 5. Refactor: `PrismTextScanner`

Applies identically to the two scanners:

- `prism-spring-ai/.../advisor/PrismTextScanner.java` (package-private)
- `prism-langchain4j/.../PrismTextScanner.java` (package-private, `final`)

### 5.1 Field change

```diff
- private final List<PrismRulePack> rulePacks;
+ private final PrismRulePackRegistry registry;
```

Constructor stores the registry reference directly. The old `List.copyOf(rulePacks)` is moved into the new list-taking constructor's delegation step (see 5.2).

### 5.2 Constructor strategy (backward compatible)

For each existing list-taking constructor, add a parallel registry-taking constructor with the same other parameters. The list-taking constructor delegates by wrapping:

```java
PrismTextScanner(List<PrismRulePack> rulePacks, ...) {
  this(new PrismRulePackRegistry(rulePacks), ...);
}

PrismTextScanner(PrismRulePackRegistry registry, ...) {
  this.registry = registry;
  // ... rest unchanged
}
```

All existing call sites continue to compile and behave identically (the registry created internally is never exposed, so it cannot be swapped — same effective behaviour as the static list today).

For the spring-ai scanner this means **three new constructors** mirroring the three existing ones (the `boolean strictMode` and `PrismFailureMode failureMode` overloads, plus the metrics-sink-only variant).

For the langchain4j scanner this means **two new constructors** mirroring the two existing ones.

### 5.3 Hot-path change

Top of `tokenize(String text)`:

```java
@Nullable String tokenize(@Nullable String text) {
  if (text == null || text.isBlank()) {
    return text;
  }
  runPreflightIfRequired();

  List<PrismRulePack> packs = registry.snapshot();   // <-- single volatile read
  // ... existing logic, but iterate `packs` instead of the old field
  for (PrismRulePack pack : packs) {
    for (PiiDetector detector : pack.getDetectors()) {
      // ... unchanged
    }
  }
  // ... rest unchanged
}
```

No other behavioural change. Sort, dedup, metrics, failure modes, preflight, segmentation — all unchanged. `detokenize` does not iterate packs and is untouched.

## 6. Data flow

```
[ Outside caller ]  ──replace(newPacks)──▶  PrismRulePackRegistry  (AtomicReference swap)
                                                    ▲
                                                    │ snapshot()  (one volatile read)
                                                    │
[ HTTP request ]  ─▶  Advisor / Integration  ─▶  PrismTextScanner.tokenize()
                                                    │
                                                    ▼
                                          iterate snapshot → detectors → vault
```

## 7. Testing plan

### 7.1 `PrismRulePackRegistryTest` (new, in `prism-core`)

| Case | Assertion |
|---|---|
| Construct with empty list | `snapshot()` is empty and immutable |
| Construct with single pack | `snapshot()` contains that pack |
| Construct with multiple packs | `snapshot()` preserves order |
| Defensive copy on construction | Mutating the input list after construction does not change the registry's snapshot |
| `replace()` swaps the snapshot | A subsequent `snapshot()` returns the new list |
| Defensive copy on replace | Mutating the replacement list after `replace()` does not change the registry |
| Immutability | `snapshot().add(...)` throws `UnsupportedOperationException` |
| Null inputs | Constructor and `replace(null)` throw `NullPointerException` |
| Atomicity smoke test | N reader threads iterating `snapshot()` while one writer thread calls `replace()` — every observed snapshot equals one of the two known input lists exactly; no partial blends, no exceptions |

The atomicity test runs for a bounded number of iterations (e.g. 10 000) and asserts on the set of observed snapshots rather than wall-clock timing. JUnit 5, no Awaitility needed.

### 7.2 `PrismTextScanner` (spring-ai and langchain4j) — additions to existing tests

| Case | Assertion |
|---|---|
| Construct via registry-taking constructor | Behaviour identical to list-taking constructor for the same packs |
| Swap registry between two `tokenize()` calls | Second call uses the new packs |
| Existing tests via list-taking constructor | Continue to pass unchanged |

In-flight atomicity is **not** asserted at the scanner layer — it would require timing-sensitive multi-threaded scaffolding that is already covered by the registry-level atomicity test. The scanner inherits the property by construction (it reads the snapshot once per call).

## 8. Files touched

| File | Change |
|---|---|
| `prism-core/src/main/java/io/github/catalin87/prism/core/PrismRulePackRegistry.java` | **new** |
| `prism-core/src/test/java/io/github/catalin87/prism/core/PrismRulePackRegistryTest.java` | **new** |
| `prism-spring-ai/src/main/java/io/github/catalin87/prism/spring/ai/advisor/PrismTextScanner.java` | refactor field; add 3 registry-taking constructors |
| `prism-spring-ai/src/test/java/io/github/catalin87/prism/spring/ai/advisor/PrismTextScannerTest.java` (if present) or equivalent | add registry-constructor and swap cases |
| `prism-langchain4j/src/main/java/io/github/catalin87/prism/langchain4j/PrismTextScanner.java` | refactor field; add 2 registry-taking constructors |
| `prism-langchain4j/src/test/.../PrismTextScannerTest.java` (if present) or equivalent | add registry-constructor and swap cases |

License headers: EUPL 1.2, matching the existing header block exactly.
JSpecify: `@NonNull` / `@Nullable` on parameters, returns, and type arguments. The `prism-core` package is not `@NullMarked`, so annotations are applied explicitly.

## 9. Risks and mitigations

| Risk | Mitigation |
|---|---|
| A swap with an empty list silently disables all PII detection | Out of scope for turn 1; turn 3's Actuator endpoint will validate. The registry itself accepts an empty list intentionally (test environments). |
| A pack registered in the new list shares mutable detector instances with the old list | Detectors are stateless by convention in this codebase. No change to that contract. |
| `replace()` called before the scanner is constructed | Impossible — the scanner takes the registry by reference; the registry must exist first. |
| Memory visibility of pack/detector internal fields | Detectors are constructed before being placed in the list and are not mutated afterwards. The happens-before edge from `AtomicReference.set` covers the list reference; final-field semantics (or effective immutability) cover the detectors' internals. |

## 10. Acceptance criteria

- `PrismRulePackRegistry` compiles in `prism-core` with EUPL header and JSpecify annotations.
- All existing tests in `prism-core`, `prism-spring-ai`, and `prism-langchain4j` pass without modification.
- New `PrismRulePackRegistryTest` passes including the atomicity smoke test.
- New scanner constructor tests pass.
- `PrismChatClientAdvisor` constructors are unchanged.
- `git diff` shows no changes outside the files listed in section 8.
