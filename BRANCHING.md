# 🌳 Branching & Contribution Strategy

Welcome to the **Spring Prism** engineering process. Because this framework acts as a definitive zero-dependency security firewall guarding enterprise PII from external Large Language Models, our Git workflow demands intense architectural discipline, cryptographic certainty, and immutable traceability.

However, we are highly contribution-friendly! This document outlines the straightforward operational expectations for flawless code integration.

---

## 🏗️ Trunk-Based Integration Strategy

Spring Prism operates on a strict **Trunk-Based Development** model configured for unbreakable releases.

- **`main`**: The immutable, production-hardened release artifact. Merges into this branch automatically construct cryptographic binaries and generate public Maven artifacts. 
- **`develop`**: The active integration corridor. All feature merges deploy here first. This branch represents the absolute cutting edge of the next Phase framework.

*(Do not commit directly to these protected trunks!)*

---

## 🏷️ Branch Naming Conventions

All active work must happen on dedicated, isolated topological branches originating from `develop`. We enforce a strict parsing nomenclature to map logic explicitly:

*   **`feature/<story-id>-<description>`** (e.g., `feature/PRISM-42-pesel-detector`)
*   **`fix/<issue-id>-<description>`** (e.g., `fix/PRISM-19-streaming-buffer-bounds`)
*   **`docs/<issue-id>-<description>`** (e.g., `docs/PRISM-8-update-vault-readme`)

---

## 🚦 The "Green-Only" Rule

To respect structural review cycles and maintain a pristine `develop` trunk natively, **Pull Requests will not be reviewed by Maintainers unless all Continuous Integration (CI) boundaries execute perfectly.**

Before requesting review, your branch must demonstrate:
1.  **Spotless Perfection**: `mvn spotless:check` yields zero format diffs.
2.  **License Sovereignty**: The EUPL 1.2 *Catalin Dordea* Copyright marker natively exists inside every single touched `.java` payload.
3.  **Maven Guarantee**: `mvn clean verify` naturally passes the strict Zero-Dependency `maven-enforcer-plugin` policies for `prism-core`.
4.  **Coverage Guardrails**: Subsystems strictly pass JaCoCo thresholds using the provided Jqwik boundary property templates.

*(If a GitHub Action pipeline fails on your PR, investigate and push corrections locally prior to engaging reviewers.)*

---

## ✒️ Conventional Commits & CHANGELOG.md

Spring Prism utilizes completely automated release drafting workflows generating the `CHANGELOG.md`. Because of this, every commit integrated must explicitly execute the **Conventional Commits** schema.

**Valid Formats:**
*   `feat: introduce HMAC-SHA256 signature generator`
*   `fix(core): correct unicode boundary offset in regex parser`
*   `docs: update quick start snippet in README`
*   `chore: bump jspecify from 1.0.0 to 1.1.0`

> **Note:** Typically, only `feat:` and `fix:` naturally bubble up into the published consumer Changelog!

---

## ⚖️ DCO Enforcement (Developer Certificate of Origin)

Because we programmatically enforce GDPR & EU AI Act compliance, the absolute provenance and legal ownership of computational logic changes are permanently binding.

Every single commit entering the Spring Prism organization **must** contain a verifiable Developer Certificate of Origin (DCO) trailer natively.

> [!IMPORTANT]
> **How to Sign Your Commits**
> Simply pass the `-s` or `--signoff` explicit flag natively during your git commit execution:
> ```bash
> git commit -s -m "feat(core): implement PL PESEL detector"
> ```
> 
> This automatically appends the strict, legally verifiable compliant trailer:
> `Signed-off-by: Jane Doe <jane.doe@example.com>`

*(If the GitHub Actions DCO Verification bot fails your pipeline because of missing signatures, immediately invoke an interactive rebase (`git rebase -i HEAD~N`) and amend each commit utilizing `--signoff`.)*
