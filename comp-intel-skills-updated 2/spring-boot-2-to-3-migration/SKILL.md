---
name: comp-intel-spring-boot-2-to-3-migration
description: What it does — migrates a Gradle/Spring Boot repo or module from Spring Boot 2.x to a pinned target of 3.5.x, covering the javax->jakarta namespace change, the Spring Data Cassandra property rename, JUnit 4->5, Gradle wrapper/plugin compatibility, and actuator/security config changes, running OpenRewrite's pinned recipe first and hand-editing only what it flags as unhandled or what's genuinely out of OpenRewrite's scope (CI/Dockerfile/non-code config). Why it matters — 2.x->3.x and 3.x->4.x are each their own breaking-change surface, so running both at once makes a failure impossible to attribute to the right hop; this skill refuses to run against a repo not currently on the 2.x line for that reason. Use whenever a repo/module is confirmed on Spring Boot 2.x and needs to move to 3.x, either as its own target or as the first hop of a 2.x->4.1.x request — a 2.x->4.1.x request goes through this skill first (landing at the pinned 3.5.x), then comp-intel-spring-boot-3-to-4-migration (landing at the pinned 4.1.x), re-detecting the resolved version between the two.
---

# Spring Boot 2.x -> 3.x Migration

One major hop only, recipe-first, **pinned target and recipe — not discovered per run.** Run
OpenRewrite's pinned 2->3 recipe before hand-editing anything; hand-edit only what the recipe
itself flags as unhandled, or what's genuinely out of OpenRewrite's scope entirely (CI config,
Dockerfiles, non-code files) — never something the recipe could plausibly have covered.

## Pinned target and tooling — do not search for alternatives at run time

**Renovate contract:** if the Renovate PR already sets the target version, that exact version is authoritative. Do not replace it with another patch or minor. Use the pinned default only when no Renovate/caller target exists.

- **Default target: Spring Boot `3.5.16`** — fixed landing point when no Renovate/caller target
  exists. This is intentionally an intermediate
  stop, not a long-term destination — this hop always continues to
  [comp-intel-spring-boot-3-to-4-migration](../spring-boot-3-to-4-migration/SKILL.md)'s own
  pinned 4.1.x target next (step 6), so landing briefly on an EOL 3.x line is expected and fine.
- **Recipe:** `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5`
- **Plugin/module versions: do not hardcode them here.** Read the exact current values from
  [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s approved-tuple table
  (the "Spring Boot 2.x → 3.5.x" row) at run time — that table is the single source of truth,
  kept current with its own live-verified correction notes. **This file used to duplicate the
  tuple inline and it silently went stale** (a fabricated `6.47.0`/`6.38.0` pair sat here for
  some time — neither version was ever published; verified 2026-09-04 against live Maven
  Central/Gradle Plugin Portal metadata). Copying the tuple into a second location is exactly
  what let it drift undetected; don't reintroduce that by hardcoding a version here again.
- If the caller passes an explicit `targetVersion` that differs from 3.5.16, treat that as an
  override to confirm with the caller before proceeding (real GA, not this skill's own job to go
  verify online) — but the *default*, no-input behavior is always this pinned target, never a
  "latest GA" lookup.

## Prerequisite: confirm the host JDK before running anything

Running OpenRewrite (step 3) needs a **host** JDK 17+ to execute at all — a separate question
from the repo's own currently-resolved JDK, which can still be on an earlier version at this
point in the migration. Don't assume the host's default JDK satisfies this. Follow
[comp-intel-java-17-to-21-migration](../java-17-to-21-migration/SKILL.md#the-jdk-version-trap-read-this-before-running-anything)'s
JDK-version trap procedure: enumerate installed JDKs, acquire a portable JDK 17+ to a
user-writable location if none exists (no sudo needed), and pin `JAVA_HOME` inline scoped to
this skill's own commands only — never edit the host's global default.

**Luna/Terra split**: steps 1, 5, and 6 (version detection, the build+test gate, re-detection)
are mechanical — Luna-appropriate. Step 2 is a fixed lookup of the pinned values above, also
Luna-appropriate — there is nothing to decide there anymore. Step 4's breaking-change checklist
stays with Terra — each item needs a real judgment call about whether the recipe actually
covered it correctly for this repo, not just whether the build went green.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.


## Steps

1. **Detect current Spring Boot version**
    - What: check fresh, don't trust the caller's claim
    - Why: a claimed version and resolved reality can diverge (e.g. a BOM override)
   ```bash
   ./gradlew dependencies | grep spring-boot
   # 2.7.18 → on 2.x, proceed
   # 3.2.1  → past this hop, stop
   ```
    - 3.x+ → stop, not applicable
    - <2.x (1.x) → stop, not documented yet
    - =2.x → proceed

2. **Set target version to the pinned value**
    - What: `3.5.16`, per the pinned section above — a fixed lookup, not a search
    - Why: the target and recipe are fixed once by the migration matrix and are not rediscovered
      or reselected during the run

3. **Run OpenRewrite with the pinned recipe before any hand-edit**
    - What: invoke [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md) with
      the pinned recipe id and plugin/module versions above — not a "confirmed" id looked up
      fresh; follow its dry-run → apply → validate sequence in full
    - Why: the recipe covers most of the mechanical changes below more reliably than a hand
      edit — hand-editing anything the recipe could plausibly have covered defeats the point of
      pinning a known-working recipe in the first place

4. **Check each breaking change below** against what the recipe already handled — hand-edit
   **only** an item the recipe's own dry-run output explicitly flagged as unhandled, or an item
   that's genuinely out of OpenRewrite's scope (it doesn't touch source/build files at all —
   e.g. a CI-only or infra-only change). Never hand-edit something the recipe covers just
   because a manual fix seems faster; disclose which item applied and why it was in scope for a
   hand-edit specifically.


5. **Full build + test run**
    - What: same gate as any dependency bump; no test-count shrinkage, no new
      `@Disabled`/`@Ignore`
    - Why: a compile can succeed while behavior still narrowed silently

6. **Re-detect and report resulting version**
    - What: re-run step 1's check — confirms the recipe's own pinned run actually landed at
      3.5.16, not just that it was targeted
    - This hop's target (3.5.16) is always an intentional intermediate stop, per the pinned
      section above — report that
      [comp-intel-spring-boot-3-to-4-migration](../spring-boot-3-to-4-migration/SKILL.md) (its
      own pinned 4.1.x target) runs next, but don't auto-chain into it directly; the caller
      re-invokes it as its own step so each hop's own verification gate runs in full
    - **STOP HERE — this is a hard checkpoint, not a suggestion.** Confirmed real failure mode:
      a single subagent run given both hops at once made premature Boot-4-specific edits
      (renamed starters, a companion-version bump, and a hardcoded transitive-version override)
      while still supposedly working on the 2→3 hop, because nothing forced a stop between them.
      If you are an orchestrator or subagent about to continue straight into the 3→4 skill in the
      same turn/session without a fresh verification-gate pass and an explicit new invocation —
      don't. Report this hop's result and end your turn here.

## Breaking changes (step 4 checklist)

- **`javax.*` -> `jakarta.*`** — handled by OpenRewrite's jakarta recipe.
  ```java
  // before
  import javax.persistence.Entity;
  ```
  ```java
  // after
  import jakarta.persistence.Entity;
  ```
  Verify none remain: `grep -r "import javax\." --include="*.java" src/`

- **Cassandra property rename**
  ```yaml
  # before
  spring.data.cassandra.contact-points: cass-host-1,cass-host-2
  ```
  ```yaml
  # after
  spring.cassandra.contact-points: cass-host-1,cass-host-2
  ```
  Check every environment: `grep -rln "spring.data.cassandra" src/main/resources/
  tap-config/*/configs/application.yml`

- **JUnit 4 -> JUnit 5**
  ```gradle
  // before — no platform launcher; tests ran via JUnit 4 vintage
  testImplementation("junit:junit:4.13.2")
  ```
  ```gradle
  // after
  tasks.withType(Test) { useJUnitPlatform() }
  dependencies { testRuntimeOnly("org.junit.platform:junit-platform-launcher") }
  ```

- **Gradle wrapper version**
  ```
  # before — wrapper pinned 7.4, Boot 3 plugin requires 7.5+
  ```
  ```bash
  # after
  ./gradlew wrapper --gradle-version 8.5
  ```
  Treat this as a floor set by the actual resolved combination, not a fixed target — the
  minimum wrapper version depends on the specific Boot 3.x line and JDK together, and can rise
  again later in the same run (e.g. a subsequent JDK bump, or a build-tooling item processed
  after this one) requiring a further wrapper bump on top of what this step alone needed.
  Coordinate with [comp-intel-java-17-to-21-migration](../java-17-to-21-migration/SKILL.md) if a
  JDK bump is also in scope — sequence by what compiles.

- **Actuator + Security config**
  ```java
  // before
  class SecurityConfig extends WebSecurityConfigurerAdapter {
      protected void configure(HttpSecurity http) { http.authorizeRequests()... }
  }
  ```
  ```java
  // after
  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.authorizeHttpRequests(auth -> auth...).build();
  }
  ```
  Find all occurrences: `grep -rn "WebSecurityConfigurerAdapter" src/`

- **Spring Cloud release train, if the repo uses Spring Cloud — pinned, not looked up per run.**
  Spring Cloud isn't managed by Spring Boot's own BOM, so bumping Boot alone doesn't move it, and
  an incompatible pairing fails at startup via Spring Cloud's own compatibility verifier, not at
  compile time. Since this hop's target is pinned at Boot `3.5.16`, the matching train is
  **Spring Cloud `2025.0.3`** (Northfields, the train's own final OSS release, based on Boot
  3.5.15 — the closest matching patch to this hop's pinned 3.5.16 target). Pin
  `springCloudVersion` (or the equivalent BOM coordinate) to `2025.0.3` directly — don't look up
  a different pairing, and don't select a Spring Cloud release train independently of the fixed
  target/version matrix for this migration.
  ```bash
  # confirm resolution only — not a search for which train to use
  ./gradlew dependencies | grep spring-cloud
  ```

- **Dependency coordinate relocations**
  ```bash
  # verify — don't assume the old coordinate still resolves
  ./gradlew dependencies | grep driver-core
  ```
  Use only the specific relocated coordinate the resolution shows — not the whole BOM.

- **Recipe overreach into third-party code applies here too, not just at the 3->4 hop.** The
  `javax->jakarta` recipe rewrites known package paths wherever it finds them, including inside
  calls into a library that hasn't itself moved to Jakarta yet. See
  [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md)'s validation step for the
  disambiguation procedure (decompile the third-party library's actually-resolved jar before
  accepting a rewritten hunk that touches its API) — don't assume it's a 3->4-only concern.
