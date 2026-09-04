---
name: comp-intel-openrewrite-migration
description: What it does — runs OpenRewrite with caller-supplied, explicitly pinned and tested versions/recipe coordinates for automated Gradle/Java source-code migrations, dry-running before apply and validating every edit. It never discovers versions, recipes, or newer OpenRewrite releases at runtime. Why it matters — hand-migrating call sites by hand is slower and more error-prone than an existing, tested recipe, and applying a multi-major recipe in one jump (or skipping validation) can silently narrow behavior to force a green build. Use this whenever an API changed as part of a version bump, before hand-migrating call sites yourself. Invoked by comp-intel-resolve-dependency-upgrade, comp-intel-spring-boot-2-to-3-migration, comp-intel-spring-boot-3-to-4-migration, and comp-intel-platform-connector-to-runtime-connector-migration — this skill only covers the mechanics of running and validating a recipe, not which recipe applies to which domain migration (that's the caller's job).
---

# OpenRewrite Migration — Mechanics

Default mechanism for post-bump source migration. Never hand-edit a changed API's call sites
before checking whether a recipe covers it. Hand-edit only as the disclosed fallback below.

**Luna/Terra split**: steps 2–4 (add the recipe, dry-run, apply) are mechanical — Luna-appropriate.
Step 5 (validating each hunk, especially the recipe-overreach checks below) is exactly the kind
of ambiguous judgment call that stays with Terra — a hunk that compiles isn't proof it's correct,
and telling a legitimate rewrite apart from overreach into a third-party library's API is not
mechanical work.

**Recipe-first means recipe-first against the still-compiling old-major codebase** — run the
recipe against the dependency state supplied by the caller. **Renovate is authoritative for the version bump**: if Renovate already changed the dependency version, keep that exact version and migrate compatibility code around it. Never revert or replace a Renovate-selected version just to let a recipe choose another version.

## Version and recipe contract — hard allow-list, no runtime exploration

The migration may use **only an explicitly approved, previously tested tuple**. The tuple is the
combination of **migration hop + recipe ID + OpenRewrite Gradle/Maven plugin version + recipe
module version**. These values are configuration, not discovery targets. Never search the
OpenRewrite catalog, Maven Central, Plugin Portal, release notes, or the web to find a newer,
older, “latest”, compatible, or alternate version during a migration.

### Approved tested tuples in this skill package

| Migration hop | Recipe | Maven plugin | Gradle plugin | Recipe module | Status |
|---|---|---:|---:|---:|---|
| Java 17 → 21 | `org.openrewrite.java.migrate.UpgradeToJava21` | `rewrite-maven-plugin:7.39.0` | `org.openrewrite.rewrite:7.39.0` | `org.openrewrite.recipe:rewrite-migrate-java:3.42.1` | **corrected 2026-09-04 — see note below** |
| Spring Boot 2.x → 3.5.x | `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5` | `rewrite-maven-plugin:7.39.0` | `org.openrewrite.rewrite:7.39.0` | `org.openrewrite.recipe:rewrite-spring:6.37.1` | **corrected 2026-09-04 — see note below** |
| Spring Boot 3.x → 4.0 | `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` | `rewrite-maven-plugin:7.39.0` | `org.openrewrite.rewrite:7.39.0` | `org.openrewrite.recipe:rewrite-spring:6.37.1` | **corrected 2026-09-04 — see note below** |

**Correction note (2026-09-04):** the previously pinned tuple (`org.openrewrite.rewrite:6.47.0`,
`rewrite-spring:6.38.0`, `rewrite-migrate-java:3.43.0`) does not exist on Maven Central or the
Gradle Plugin Portal — verified directly against each artifact's live `maven-metadata.xml`
(published `org.openrewrite.rewrite` plugin versions run ...6.29.4 → 7.0.0 → ... → 7.41.0;
published `rewrite-spring` versions run ...6.36.1 → 6.37.0 → 6.37.1, with no 6.38.0; published
`rewrite-migrate-java` versions run ...3.41.0 → 3.42.0 → 3.42.1, with no 3.43.0).

**This same fabricated tuple was also found hardcoded, independently, inside
[spring-boot-2-to-3-migration](../spring-boot-2-to-3-migration/SKILL.md),
[spring-boot-3-to-4-migration](../spring-boot-3-to-4-migration/SKILL.md), and
[java-17-to-21-migration](../java-17-to-21-migration/SKILL.md)** — four copies of the same wrong
numbers, none of which caught the others going stale. Those three files have been changed to
read the tuple from this table at run time instead of hardcoding their own copy — **don't
reintroduce a hardcoded tuple in any migration-hop skill file; this table is the single source of
truth.** The recipe module/recipe names were verified by
downloading `rewrite-spring-6.37.1.jar` from Maven Central directly and confirming it contains
`META-INF/rewrite/spring-boot-35.yml` (recipe `org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_5`)
and `META-INF/rewrite/spring-boot-40.yml` (recipe `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0`)
— both fully public, no authenticated/private repository required for either hop.

**Plugin-version pitfall found 2026-09-04: the plugin's own published version number and the
`rewrite-bom` version it depends on are two independently-incrementing numbers — a plugin version
existing on the Gradle Plugin Portal does NOT mean its declared `rewrite-bom` dependency has
actually been published yet.** `org.openrewrite.rewrite:7.41.0`'s own POM (fetch with `curl -L`,
Gradle Plugin Portal issues a 303 redirect that a plain `curl -s` silently drops) declares
`org.openrewrite:rewrite-bom:8.91.0`, which does not exist on Maven Central (latest published at
correction time: `8.90.4`) — so 7.41.0 fails at plugin-resolution time with a POM-parse error, not
a recipe-execution error. **7.39.0 is the corrected pin**: its POM declares `rewrite-bom:8.89.0`,
and all three artifacts (marker plugin POM, `org.openrewrite:plugin:7.39.0` jar, and
`rewrite-bom:8.89.0`'s own POM) were confirmed to return HTTP 200. Before trusting any plugin
version pin on a future run: fetch that version's real POM with `curl -sL` (not `-s` alone) from
`https://plugins.gradle.org/m2/org/openrewrite/plugin/<version>/plugin-<version>.pom`, extract its
declared `rewrite-bom` version, and confirm that exact BOM version exists in
`https://repo.maven.apache.org/maven2/org/openrewrite/rewrite-bom/maven-metadata.xml` before
picking it — don't assume the newest plugin version is safe just because its own artifact
resolves. Update this note's date when re-confirmed.

A caller-supplied tuple is acceptable only when it is explicitly identified
as a previously tested tuple by the calling migration matrix; “latest”, “current”, “confirmed”,
or “available” is not sufficient evidence.

For Renovate PRs, the Renovate diff/branch supplies the **dependency destination**, not the
OpenRewrite tooling version. Renovate may update Spring Boot/Gradle/Java directly; the migration
skill must keep those exact Renovate targets while selecting OpenRewrite only from this fixed
allow-list. If the required tuple is absent, stop and report it.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.


## Steps

1. **Validate the supplied migration tuple against the hard allow-list**
   - What: exact hop, recipe ID, plugin version, and recipe module version must match an approved tuple above
   - Why: compatibility is established only by a previously tested combination, never by runtime discovery
   - STOP if the tuple is absent or does not exactly match the allow-list. Do not search for a replacement.
2. **Add only the one recipe, temporarily**
   - What: add the plugin + the single recipe from step 1 — not the whole catalog
   - Why: extra active recipes make a bad hunk in step 5 harder to attribute
   ```gradle
   // before — no OpenRewrite plugin present
   ```
   ```gradle
   // after
   plugins {
       id("org.openrewrite.rewrite") version "<pinned-plugin-version>"
   }
   rewrite {
       activeRecipe("<fully-qualified-recipe-name>")
   }
   dependencies {
       rewrite("<pinned-recipe-module-coordinate>:<pinned-module-version>")
   }
   ```
   - If an authenticated repository is required (see step 1), add it scoped to this temporary
     block only, using property-referenced credentials — never a literal username/password in
     any tracked build file — and remove it in step 6 along with everything else added here.
   - **The plugin's own transitive `rewrite-bom` POM often isn't resolvable from the Gradle
     Plugin Portal alone** — only from Maven Central. A repo whose `settings.gradle` has no
     `pluginManagement` block (Gradle's default `plugins {}` resolution is Plugin-Portal-only)
     will fail with `Could not find org.openrewrite:rewrite-bom:<version>` the moment the
     plugin block is added, before the recipe ever gets a chance to run. Fix: add a
     `pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }` block to
     `settings.gradle` (a real, recurring bootstrap need, not a one-off — this has hit
     independently on unrelated repos). Since it only adds a repository for plugin resolution
     and changes no dependency versions, it's safe to land permanently rather than re-adding
     it as throwaway scaffolding on every single migration run in the same repo.
   - Use the exact plugin version from the caller's tested matrix. Do not inspect newer plugin releases or step backward/forward to another version at runtime. If the pinned plugin cannot resolve in the configured repositories, stop and report the resolution failure.

3. **Dry run first**
   - What: `./gradlew rewriteDryRun`, read every hunk of the patch — not just the file list
   - Why: a recipe can silently drop a call/block it doesn't recognize; the patch is the only
     place to catch that before it lands
   ```bash
   ./gradlew rewriteDryRun
   cat build/reports/rewrite/rewrite.patch
   ```
   - **Environment note:** OpenRewrite's type-table cache resolves via `user.home`, not just the
     `HOME` env var. If the sandbox/CI user's real home directory isn't writable, setting `HOME`
     alone often isn't enough — with `--no-daemon`, pass the override directly on the command
     line: `./gradlew rewriteDryRun --no-daemon -Duser.home=<writable-path>`. An env-var-only
     override can silently fail to take effect and the recipe will keep hitting the original
     (inaccessible) path.

4. **Apply**
   - What: `./gradlew rewriteRun` once the dry-run output looks correct
   - Why: applying before reading the patch defeats the point of step 3

5. **Validate against the Non-Removal Invariant**
   - What: fix or revert only the invalid hunks; keep the valid ones
   - Why: blind trust that the recipe worked is how a narrowing edit reaches production
   ```java
   // before (recipe's invalid hunk)
   - @Bean
   - LegacyService legacyService() { ... }
   ```
   ```java
   // after (corrected)
   @Bean
   LegacyService legacyService() { ... }   // reverted; disclosed in report as hand-fixed
   ```

   - **Recipe overreach — verify before trusting an import/type rewrite that isn't Spring's (or
     the target library's) own package.** A recipe pattern-matches known import paths (e.g. any
     `com.fasterxml.jackson.*` or `org.apache.http.*` usage) and rewrites them wherever it finds
     them — it has no way to know whether an *unrelated* third-party library that happens to
     consume those same classes has actually moved to the new major in lockstep with the
     dependency being bumped. Concrete examples hit migrating to Spring Boot 4 (Jackson 3 /
     Apache HttpClient 5 baseline): a low-level Elasticsearch client config still required
     Jackson 2 / HttpClient 4 signatures, but the recipe rewrote both to the new major anyway;
     separately, a Testcontainers container class hadn't moved to its new package at the pinned
     Testcontainers version even though the recipe assumed it had. Both failures look identical
     to a legitimate "recipe needs the version bump too" failure (compile errors: "incompatible
     types" / "package does not exist").
     **Disambiguate by checking who actually owns the API on each side of the call**: if the
     rewritten class is passed into a *non-Spring, non-Boot-managed* library's method or
     constructor, decompile that library's actual resolved jar (`javap` on the extracted
     `.class`, or `unzip -l ... | grep ClassName` first to confirm the package) rather than
     trusting the import path alone. Fix is either (a) revert that one usage's import/API to the
     old major, since the third-party library hasn't moved yet, or (b) bump that third-party
     library to a version with compatible support for the new major — prefer the smallest bump
     that stays compatible with any pinned server/runtime it talks to (e.g. a database or search
     server version) over jumping that library's own major purely to silence a compile error.
   - A recipe-touched line can also change without a package rename: don't assume a moved
     class's static members/constants carried over unchanged. If a recipe-touched line
     references a static field or method on a relocated class, verify it still exists under the
     same name via `javap` on the resolved new-major jar before accepting the hunk as-is.
   - Compile success alone doesn't clear a hunk. Some genuinely-required companion changes are
     invisible to `compileJava` and only surface as a `NoClassDefFoundError`/missing-bean failure
     at Spring context startup, or a wrong-signature call that resolves to a different overload
     with different behavior. Don't treat a green compile as validation complete — run the full
     verification gate (build+test, and where relevant an actual runtime/context-load check)
     before disclosing a hunk as accepted.

6. **Remove the temporary tooling**
   - What: delete the plugin block and `rewrite(...)` dependency once the gate passes
   - Why: OpenRewrite is a migration tool, not a permanent build dependency
   ```gradle
   // after — the block added in step 2 is gone; only the source-code changes remain
   ```
   - Remove any temporary authenticated-repository block added in step 2 at the same time —
     nothing from step 2 should remain once the migration is verified.

## Multi-major jumps

One major's recipe at a time, confirming compilation after each — never skip a major. See
[comp-intel-spring-boot-2-to-3-migration](../spring-boot-2-to-3-migration/SKILL.md) and
[comp-intel-spring-boot-3-to-4-migration](../spring-boot-3-to-4-migration/SKILL.md).

## Fallback: hand-edit only for what's flagged or genuinely out of scope

- Hand-editing is the exception, not a parallel option — OpenRewrite's recipe is the default
  path for every migration in this family, every run.
- A hand-edit is only valid for two cases: (1) an item the recipe's own dry-run output
  **explicitly flags** as unhandled/unmigrated, or (2) an item that's **genuinely out of
  OpenRewrite's scope** — it isn't source/build-file content a recipe could ever touch (CI
  pipeline config, Dockerfiles, infra-only changes, non-code files). Both cases also cover a
  recipe that fails or violates the Non-Removal Invariant after one retry.
- Never hand-edit something the recipe could plausibly have covered just because a manual fix
  looks faster or simpler — that's exactly the ad hoc, undisclosed migration this skill exists
  to prevent (see the recipe-first note at the top of this file).
- Disclose exactly why (no recipe found / invalid edit / run failure / out-of-scope-for-recipe)
  for every hand-edit — never substitute silently, and never fold a hand-edit into "the recipe's
  own diff."

## Bootstrap edge case

If the code doesn't compile well enough for OpenRewrite to even run: make the smallest possible
direct bootstrap edit to reach compilable state, disclose it, then run OpenRewrite for the rest.
Don't let the bootstrap edit grow into the whole migration.

## Non-Removal Invariant (reminder)

No deleting, disabling, or narrowing production code, config, beans, pipeline config, or tests
to reach green. If a recipe or hand-edit can only get there by removing something, that's a stop
condition — revert this dependency and report why.
