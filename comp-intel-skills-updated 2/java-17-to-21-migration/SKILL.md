---
name: comp-intel-java-17-to-21-migration
description: What it does — bumps a Gradle/Spring Boot repo or module's JDK toolchain from Java 17 to a pinned target of Java 21 using OpenRewrite's pinned recipe for the source/build-metadata changes, plus the Gradle wrapper compatibility check, the Dockerfile/Runtime Connector base image JDK tag, and CI runner JDK — none of which OpenRewrite's recipe touches, so those stay hand-edited by design, not as a fallback. Why it matters — a naive version bump breaks builds if the Gradle wrapper predates JDK 21 support, and leaves the Dockerfile/CI JDK silently mismatched with the toolchain if only one is updated; this skill sequences the change so nothing is missed. Use whenever a dependency (e.g. Spring Boot) requires a newer JDK baseline, whenever the user explicitly asks to move a repo from Java 17 to 21, or whenever another migration/upgrade skill hands off a module it detected as currently on Java 17. Scoped to exactly this one adjacent jump — neither an earlier jump (8->11, 11->17) nor a later one (21->25 and beyond) is covered; each needs its own skill built on this same template, not this one reused with a different target. Do not use for anything already on Java 21+ or still below 17.
---

# Java 17 -> 21 Migration

One adjacent hop (17 -> 21) only, **pinned target and recipe — not discovered per run.** Neither
an earlier hop (8/11) nor a later one (21 -> 25 and beyond) is covered — flag it, don't stretch
this skill to fit.

## Pinned target and tooling — do not search for alternatives at run time

**Renovate contract:** if the Renovate PR already sets the target version, that exact version is authoritative. Do not replace it with another patch, minor, or "latest" version. Use the pinned default only when no Renovate/caller target exists.

- **Target: Java `21`** — fixed by this skill's own scope, nothing to select.
- **Recipe:** `org.openrewrite.java.migrate.UpgradeToJava21` — rewrites source to idiomatic
  Java 21 where safe (pattern-matching `switch`, sequenced-collection APIs) **and** updates
  build metadata (`maven.compiler.release`, Gradle toolchain declarations) as part of its own
  patch. That build-metadata coverage is why step 3 below is recipe-first now, not a hand-edit —
  hand-editing `sourceCompatibility`/toolchain blocks ahead of the recipe risks diverging from
  what the recipe itself would have set.
- **OpenRewrite plugin/module versions: do not hardcode them here.** Read the exact current
  values from [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s
  approved-tuple table (the "Java 17 → 21" row) at run time — that table is the single source of
  truth. **This file used to duplicate the tuple inline and it silently went stale** (a
  fabricated `6.47.0`/`3.43.0` pair sat here for some time — neither version was ever published;
  verified 2026-09-04 against live Maven Central/Gradle Plugin Portal metadata, where the real
  latest `rewrite-migrate-java` release is `3.42.1`). Don't reintroduce the duplication.

**Luna/Terra split**: steps 1, 2, 4, 5, 6, and 7 (version detection, wrapper bump, Dockerfile/CI
edits, the build+test gate, re-detection) are mechanical — Luna-appropriate. Step 3's dry-run
review (per [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s own Luna/Terra
split) and step 4's Runtime Connector coordination check need Terra's judgment — the former
because a hunk that compiles isn't proof it's correct, the latter if a Platform Connector
migration is also in scope for the same module.

## The JDK-version trap — read this before running anything

The machine invoking this skill (sandbox, CI runner, whatever) can have **any** JDK as its
default, and that default is a separate concern from the repo's own target. Two distinct
questions, easy to conflate:

1. **What JDK does the *host* need to actually run the tooling in this skill?** OpenRewrite
   (step 3) and the Gradle wrapper task itself (step 2) both need a JDK **17 or newer** to
   execute at all — regardless of what the target repo's own toolchain currently says. A host
   stuck on an older default JDK will fail step 2/3 with an opaque Gradle/JVM-version error that
   looks like a project problem, not a host one.
2. **What JDK is the *repo's own code* being migrated to?** That's Java 21 (this skill's pinned
   target, from the section above) — a separate value from #1, even though they can coincide.

Don't assume the host's default JDK satisfies either need. Procedure:

1. Enumerate installed JDKs on the host — don't assume (platform-appropriate listing:
   `/usr/libexec/java_home -V` on macOS, `update-alternatives --list java` / `ls /usr/lib/jvm` on
   Linux, `sdk list java` if SDKMAN is present).
2. If none ≥17 exists for running the tooling itself, acquire a portable one to a
   user-writable location (no sudo, no system-wide install) rather than stopping outright — e.g.
   a Temurin tarball extracted locally — and pin `JAVA_HOME` to it only for the migration
   commands in steps 2 and 3.
3. Export `JAVA_HOME` inline, scoped to each migration command only — never edit the host's
   global default JDK to make this work.
4. After step 3's recipe run and step 6's build+test gate pass, an actual **JDK 21** install is
   needed for the repo's *ongoing* builds — separate from whatever JDK ran the migration tooling
   in steps 2–3. Confirm that install exists (or is provisioned by CI) before reporting the
   migration complete; a host that happened to have a portable JDK 17 available for running
   OpenRewrite is not the same thing as the repo now having a real JDK 21 to build against going
   forward.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.


## Steps

1. **Detect current JDK**
   - What: check the resolved JDK, not a claim
   - Why: a stale toolchain block can disagree with what CI actually pins
   ```bash
   ./gradlew -v          # JVM line
   # + Gradle toolchain block if present, + CI pipeline's JDK reference
   ```
   - 21+ → stop, not applicable
   - <17 → stop, earlier hop needed first
   - =17 → proceed

2. **Confirm Gradle wrapper supports JDK 21**
   - What: check installed JDKs (per the JDK-version trap above — this needs the *host's*
     capability, separate from the repo's own toolchain target), bump wrapper first if too old
   - Why: an old wrapper fails under JDK 21 with an opaque error that looks like a JDK bug
   ```properties
   # before — gradle/wrapper/gradle-wrapper.properties
   distributionUrl=...gradle-7.6-bin.zip
   ```
   - **Pinned minimum: Gradle `8.5`** — the floor version confirmed to run under and target
     JDK 21 for this migration. Run the bump exactly once, with this exact version pinned, not
     a version discovered or guessed at run time:
   ```bash
   # after — run once, with the exact pinned version, under JAVA_HOME scoped per the trap above
   ./gradlew wrapper --gradle-version 8.5 --distribution-type bin
   git status   # confirm gradle-wrapper.jar itself shows as changed, not just .properties
   ./gradlew -v   # confirm the wrapper now actually reports 8.5, not just that the file says so
   ```
   - If the wrapper is already ≥8.5, this step is a no-op — confirm and move on, don't re-run
     the bump command against an already-sufficient wrapper.
   - This is a floor bump owned by this step, not a hand-off, even if it crosses a Gradle major
     line. [comp-intel-gradle-wrapper-major-upgrade](../gradle-wrapper-major-upgrade/SKILL.md) is
     only for a standalone, otherwise-unowned wrapper major bump — but reuse its step 2 concern
     here too: run the wrapper task to regenerate `gradle-wrapper.jar` itself, don't just
     hand-edit `distributionUrl` in the properties file, and don't re-run the wrapper task
     speculatively — one exact, verified run per the JDK-version trap's scoped `JAVA_HOME`.
   - This is genuinely out of the recipe's scope (it isn't a source/build-metadata change the
     recipe covers) — hand-edited by design, not as a fallback from checking for a recipe first.

3. **Run OpenRewrite with the pinned recipe before any hand-edit**
   - What: invoke [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md) with the
     pinned recipe id and plugin/module versions above — not a discovered id, a fixed one; follow
     its dry-run → apply → validate sequence in full. This covers the toolchain/sourceCompatibility
     bump as part of its own patch (see the pinned section above) — don't hand-edit those ahead of
     the recipe.
   - Why: the recipe covers both the source-level Java 21 idioms and the build-metadata change
     more reliably than a hand edit — hand-editing either defeats the point of pinning a
     known-working recipe
   - Hand-edit **only** an item the recipe's own dry-run output explicitly flagged as unhandled —
     never something it could plausibly have covered, per
     [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s own fallback rule.

4. **Update Dockerfile / Runtime Connector base image tag**
   - What: bump the tag, confirm it's pullable — genuinely out of the recipe's scope (a
     Dockerfile isn't source/build-metadata content OpenRewrite's Java recipes touch), hand-edited
     by design, not because the recipe missed it
   - Why: a toolchain bump with no image bump silently still runs JDK 17 at container runtime
   ```dockerfile
   # before
   FROM docker.target.com/toolshed/base-runtime-connector-jre:17
   ```
   ```dockerfile
   # after
   FROM docker.target.com/toolshed/base-runtime-connector-jre:21
   ```
   - **Check whether a Platform Connector -> Runtime Connector migration is also in scope
     (planned or already queued) for this module before treating this bump as final.** A
     Runtime Connector base image bundles its own JDK tag; if that migration lands afterward, it
     supersedes whatever tag this step set — the base image line changes entirely (from a
     JDK-tagged image to the Runtime Connector image), not just the version number. A real run
     did this Dockerfile bump independently, only to have it become moot minutes later when the
     Platform Connector migration replaced the base image outright. Where both are in scope in
     the same orchestrated run, sequence Platform Connector's migration first (or confirm this
     step's tag choice with
     [comp-intel-platform-connector-to-runtime-connector-migration](../platform-connector-to-runtime-connector-migration/SKILL.md)'s
     `runtimeConnectorJdkTag` input) rather than doing this step in isolation.

5. **Update CI runner JDK**
   - What: bump the pipeline's pinned JDK — also genuinely out of the recipe's scope (CI YAML
     isn't a source/build-metadata file), hand-edited by design
   - Why: CI can keep building green on 17 while config claims 21, masking a real break
   ```yaml
   # before — .vela.yml
   image: openjdk:17-jdk-slim
   ```
   ```yaml
   # after
   image: openjdk:21-jdk-slim
   ```
   - **Check what the CI image line actually currently says before overwriting it — a separate,
     unrelated proposal can already have moved it past 21.** Confirmed on a real repo: a bundled
     Renovate branch bumped `.vela.yml`'s image straight from `17-jdk-jammy` to `25-jdk-jammy`
     with no accompanying Gradle toolchain change at all — Renovate proposing the CI image and
     the toolchain move independently of each other, not as one coordinated jump. If the CI line
     already targets something past 21 when this step runs, that's not this step's job to
     resolve by picking a number: setting CI to 21 would silently revert a separate proposal, and
     accepting the higher tag as-is would leave CI ahead of the toolchain this skill actually
     verified (21) — an adjacent-hop-only mismatch of its own, since a 17->25 jump isn't one
     adjacent hop and has no dedicated skill yet (this skill is capped at 21 by design; a later
     hop like 21->25 needs its own instance of this same template, not this one stretched to
     reach further). Flag the discrepancy and the two options plainly rather than silently
     picking one.

6. **Full build + test gate**
   - What: full suite must pass, no test-count shrinkage
   - Why: a JDK bump alone shouldn't need test changes — if it does, something's unsound

7. **Re-detect and report resulting version**
   - What: re-run step 1's check
   - Why: confirms the change actually took, not just that edits were made

## Optional: adopt JDK 21 features

Virtual threads, pattern matching for switch, etc. — the pinned recipe already applies the
in-scope idiom rewrites (see the pinned section above) where safe; anything beyond that is not
required by this migration. Apply only if separately requested.

## Related skills

Handed off from [comp-intel-resolve-dependency-upgrade](../resolve-dependency-upgrade/SKILL.md)
or [comp-intel-platform-connector-to-runtime-connector-migration](../platform-connector-to-runtime-connector-migration/SKILL.md)
when either detects a JDK-toolchain dependency currently on Java 17.
