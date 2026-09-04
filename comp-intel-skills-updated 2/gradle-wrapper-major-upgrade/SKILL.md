---
name: comp-intel-gradle-wrapper-major-upgrade
description: What it does — bumps a Gradle wrapper across a major version line (e.g. 7.x/8.x -> 9.x) for a repo whose build-tool bump isn't already owned by another migration's own wrapper-floor step, covering distributionUrl/wrapper JAR, plugin-portal vs Maven Central resolution changes for any active custom plugins, DSL/API breaking changes, and CI runner Gradle assumptions. Why it matters — a Gradle major can change plugin-resolution behavior and deprecate/remove DSL that a repo's build scripts actually use; treating it as a routine wrapper-properties edit risks a build that only breaks in CI, or a silent no-op if the wrapper JAR itself wasn't regenerated. Use whenever comp-intel-resolve-dependency-upgrade hands off a Gradle wrapper major bump with no other owning case (see its hand-off-detection.md), or whenever a user explicitly asks to move a repo's Gradle wrapper across a major line outside that flow. Do not use for a same-major minor/patch wrapper bump (e.g. 8.5 -> 8.11) — that stays on the generic dependency-upgrade path. Do not use when the wrapper bump is only a floor requirement of another migration already in progress (e.g. Spring Boot 3's wrapper-version floor) — that migration's own step owns it; this skill is for an otherwise-unowned, standalone wrapper major bump only.
---

# Gradle Wrapper Major-Version Upgrade

One build-tool major hop at a time using the exact Renovate/caller target version — never a version selected by this skill. Built on the same template as
[comp-intel-java-17-to-21-migration](../java-17-to-21-migration/SKILL.md); an earlier or later
hop needs its own instance of this template with the target line changed, not this skill stretched
to fit two hops at once.

**Luna/Terra split** (see
[comp-intel-sca-vulnerability-remediation](../sca-vulnerability-remediation/SKILL.md#model-selection-and-subagent-delegation-luna-and-terra)
for the full model): steps 1, 2, 5, and 6 (version detection, wrapper regeneration, the
build+test gate, re-detection) are mechanical — Luna-appropriate. Step 3 (DSL/plugin-resolution
breaking changes) and step 4 (custom-plugin compatibility) need Terra's judgment — a build that
merely evaluates without error isn't proof a removed/changed DSL feature wasn't silently
no-op'd.

## Hard rules

- Read the current wrapper version from `gradle/wrapper/gradle-wrapper.properties`'s
  `distributionUrl` itself — never from a claimed version in a PR title/branch name.
- Target precedence: Renovate-applied `distributionUrl` version > explicit caller `targetVersion`. Never search for latest Gradle or select another target.
- If Renovate already changed the wrapper to the target, do not bump it again; validate/regenerate only if required.
- Never stage, commit, push, or open a PR. Leave the diff unstaged, per the same contract as
  [comp-intel-resolve-dependency-upgrade](../resolve-dependency-upgrade/SKILL.md).
- Non-Removal Invariant applies here too: don't delete or disable a build script block, a
  custom task, or a test to route around a DSL-breaking change — fix it or stop and report.

## Host JDK vs. target — the same trap as the JDK migration skill, applied to Gradle itself

Running the *new* Gradle version at all can itself require a newer host JDK than what's
currently installed — separate from whatever JDK the target repo's own code compiles against.
Gradle majors periodically raise their own minimum JDK-to-run requirement independent of any
project-level toolchain setting. Follow the same procedure as
[comp-intel-java-17-to-21-migration](../java-17-to-21-migration/SKILL.md#the-jdk-version-trap-read-this-before-running-anything)'s
JDK-version trap section before step 2 below: enumerate installed JDKs, don't trust the host
default, acquire a portable one to a user-writable location if needed, and scope `JAVA_HOME` to
this skill's own commands only — never edit the host's global default to make the wrapper task
run.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.


## Steps

1. **Detect current wrapper version, and pin the exact target — once**
   - What: read `distributionUrl` directly; confirm the target is a real, published GA
     distribution (`https://services.gradle.org/distributions/`), not a milestone/RC — and pin
     that **exact** version string here, for use verbatim in step 2. Don't re-derive or restate
     it loosely later; the value fixed in this step is the one step 2 must run against.
   - Why: a stale or hand-edited `distributionUrl` can disagree with what `./gradlew -v` actually
     resolves if the JAR itself wasn't regenerated to match — and a target that drifts between
     detection and execution (e.g. one version discussed here, a different one actually run in
     step 2) is exactly the kind of silent mismatch this skill exists to prevent
   ```properties
   # before
   distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
   ```
   - Source the target from real evidence, not a placeholder — e.g. the actual Renovate branch's
     proposed `distributionUrl`, or a caller-supplied `targetVersion`. Confirmed real example: a
     Renovate branch proposing `gradle-9.7.1-bin.zip` as its exact `distributionUrl` — that exact
     string (`9.7.1`, not a rounded "9.x") is what step 2 must run with.
   - Same major as target → stop, not applicable (belongs on the generic path instead)
   - Target isn't a real published GA → stop, report

2. **Regenerate the wrapper itself, with the exact pinned version — run once**
   - What: use the *currently installed* Gradle (with `JAVA_HOME` scoped per the host-JDK note
     above) to run the wrapper task for the **exact version pinned in step 1** — hand-editing
     `distributionUrl` alone leaves the wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) itself
     un-regenerated, which can produce a wrapper that fetches the new distribution but still runs
     old bootstrap logic
   - Why: this is exactly the kind of edit that looks complete in a diff (one line changed) but
     silently doesn't do what it claims — and running the command with anything other than step
     1's exact pinned string (a rounded number, a different patch, a value typed from memory
     instead of copied from step 1) reintroduces the same drift step 1 exists to prevent
   ```bash
   # exact version from step 1 — not a placeholder, not a different patch chosen here
   ./gradlew wrapper --gradle-version <step-1's-pinned-version> --distribution-type bin
   git status   # confirm gradle-wrapper.jar itself shows as changed, not just .properties
   ./gradlew -v # confirm the wrapper now actually reports step 1's exact pinned version
   ```
   - Run this once per migration. If the confirmation in the last line doesn't match step 1's
     pinned version exactly, that's a stop-and-report condition, not a reason to retry with a
     nearby version guessed on the spot.

3. **Check DSL / plugin-resolution breaking changes for this major**
   - What: read the target major's own upgrade guide
     (`https://docs.gradle.org/current/userguide/upgrading_version_<N>.html` for the *previous*
     major, which documents what changed moving into the new one) — don't rely on memory, Gradle
     DSL changes are frequent and version-specific
   - Why: a removed/deprecated-then-removed DSL feature can compile-evaluate as a no-op rather
     than failing loudly, which looks identical to "nothing needed changing"
   - Specifically check, every run (real, version-specific list — verify against the actual guide
     rather than assuming this list is exhaustive or still current):
     - `buildscript {}` classpath resolution behavior changes
     - any custom/community plugin's own declared minimum-Gradle-version compatibility — a
       repo-local convention plugin or a third-party plugin can simply not support the new major
       yet; check its own release notes, don't assume it "probably still works"
     - task configuration-avoidance API changes (a plugin or build script using an
       eagerly-configuring API that's been removed, not just deprecated)
     - `pluginManagement {}` repository resolution — see
       [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s own note on this
       (a repo with no `pluginManagement` block defaults to Plugin-Portal-only resolution) if this
       same run is also adding OpenRewrite or another plugin that needs Maven Central

4. **Check CI runner assumptions**
   - What: confirm the CI pipeline doesn't pin or cache an incompatible Gradle version
     independent of the wrapper (e.g. a CI image pre-bundling an older Gradle that some pipeline
     step invokes directly instead of via `./gradlew`)
   - Why: the wrapper bump can be fully correct locally while CI silently keeps building with the
     old version through a direct `gradle` invocation that bypasses the wrapper entirely

5. **Full build + test gate**
   - What: full suite must pass, no test-count shrinkage, same gate as any dependency bump
   - Why: a wrapper bump alone shouldn't need test changes — if it does, something's unsound

6. **Re-detect and report resulting version**
   - What: re-run step 1's check (`distributionUrl`, and confirm `./gradlew -v` actually reports
     the new version — not just that the properties file says so)
   - Why: confirms the regeneration in step 2 actually took effect, not just that the properties
     file was edited

## Related skills

Handed off from
[comp-intel-resolve-dependency-upgrade](../resolve-dependency-upgrade/SKILL.md) when its
hand-off detection finds a Gradle wrapper major bump with no other owning case. A wrapper bump
that's a *floor requirement* of another migration (e.g. Spring Boot 3's minimum-wrapper-version
step) stays owned by that migration — don't invoke this skill for that case.
