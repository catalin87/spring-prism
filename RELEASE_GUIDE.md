# Spring Prism — Release Guide

> **This is a 3-step process. CI handles everything else.**

---

## Prerequisites (One-Time Setup)

Before you can publish to Maven Central, verify these are complete:

1. **Sonatype Namespace Verified** — Go to [central.sonatype.com](https://central.sonatype.com) → Namespaces → confirm `io.github.catalin87` shows ✅ Verified.
   - If not: create a **public** temporary GitHub repo named with the verification key they provide, click Verify, then delete the repo.

2. **GitHub Secrets Configured** — All four release secrets must exist in **Settings → Secrets → Actions**:
   - `OSSRH_USERNAME` — Sonatype Central Portal User Token username
   - `OSSRH_TOKEN` — Sonatype Central Portal User Token password
   - `GPG_PRIVATE_KEY` — Your armored GPG private key block
   - `GPG_PASSPHRASE` — Your GPG key passphrase

3. **GitHub Environment Exists** — `maven-central-release` environment must exist in **Settings → Environments**.

4. **Only release from `main`** — Merge all feature branches to `main` before tagging. Tags must point to `main` for the release workflow to produce a coherent artifact.

5. **Dry-Run Packaging Passes** — Before tagging, confirm:

```bash
mvn clean verify
mvn -Prelease -Dgpg.skip=true -DskipTests package
cd website && npm run build
```

Also create a GitHub **Environment** named `maven-central-release` (Settings → Environments). This acts as an optional manual approval gate before the publish job fires.

---

## Releasing a New Version (3 Steps)

### Step 1 — Update the version

Edit the `<version>` tag in the **root `pom.xml`** (and nowhere else — child modules inherit it):

```xml
<!-- Before -->
<version>1.0.0-SNAPSHOT</version>

<!-- After -->
<version>1.0.0</version>
```

### Step 2 — Commit

```bash
git add pom.xml
git commit -s -m "release: v1.0.0"
git push origin main
```

> The `-s` flag adds the required `Signed-off-by:` trailer for DCO compliance.

### Step 3 — Tag and push

```bash
git tag v1.0.0
git push origin v1.0.0
```

**That's it.** The `release.yml` workflow takes over automatically:

1. ✅ Runs the full test suite & JaCoCo 90% gate
2. ✅ Signs all artifacts with your GPG key
3. ✅ Generates Javadoc and sources JARs (required by Maven Central)
4. ✅ Deploys to Sonatype Staging and triggers the Close → Release sequence
5. ✅ Creates a GitHub Release with auto-generated release notes

---

## After the Release — Bump Back to SNAPSHOT

Immediately after tagging, prepare for the next development cycle:

```bash
# Update pom.xml version to the next SNAPSHOT
# e.g., 1.0.0 → 1.1.0-SNAPSHOT
git add pom.xml
git commit -s -m "chore: begin 1.1.0-SNAPSHOT development cycle"
git push origin main
```

---

## Verifying the Release

- **Sonatype Portal:** https://central.sonatype.com/artifact/io.github.catalin87.prism/prism-core
- **Maven Central Search:** https://search.maven.org/search?q=g:io.github.catalin87.prism
- Allow **15–30 minutes** for Central sync after the workflow completes.
