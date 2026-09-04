---
name: comp-intel-resolve-dependency-upgrade
description: What it does — upgrades a single Gradle/Java dependency to a real, published GA target version: discovers the real resolution path (direct, parent, BOM, or plugin), detects special-cased dependencies (Spring Boot, the JDK/toolchain, Platform Connector) and hands off to the matching dedicated migration skill instead of a generic bump, migrates call sites (defaulting to OpenRewrite), and gates on a full build+test run with no removal/narrowing of existing code, config, or tests. Why it matters — trusting a caller's claimed version or path instead of confirming it can bump the wrong governing artifact or silently no-op a Renovate-invisible dependency, and treating every dependency as generic would run the wrong steps against Spring Boot, JDK, or Platform Connector cases that each have their own breaking-change surface and dedicated skill. Use whenever comp-intel-sca-vulnerability-remediation invokes it for one dependency in a Renovate branch, or standalone when a user asks to bump one specific Gradle/Java dependency to an explicit target version outside a full SCA remediation run.
---

# Resolve Dependency Upgrade

One dependency at a time. Sequencing a bundled branch (ordering, failure classification) is
[comp-intel-sca-vulnerability-remediation](../sca-vulnerability-remediation/SKILL.md)'s job —
this skill assumes it's been told which dependency and what baseline to verify against.

**Luna/Terra split** (see the orchestrator's [Model selection and subagent
delegation](../sca-vulnerability-remediation/SKILL.md#model-selection-and-subagent-delegation-luna-and-terra)
section for the full model): step 1's hand-off detection and step 3's STOP-condition calls are
judgment work — keep those with Terra. Step 2's discovery and step 4's verification gate are
mechanical once the target is known — those can run on Luna.

## Hard rules

- Discover repo facts fresh each run. Treat an existing Renovate version change as the fixed destination, not as proof the repository is compatible. Never select a different destination version.
- Always determine the baseline version from the branch/base diff and the target version from the Renovate change, then migrate the code/build/configuration across the full version gap required to make that exact target work.
- Never stage, commit, push, or open a PR. Leave the diff unstaged.
- Never force a transitive version.
- Never delete, disable, or narrow production code, config, beans, pipeline config, or test
  scaffolding to pass (**Non-Removal Invariant**). If green needs removal, revert and report why.
  **This covers capability, not just lines of code** — confirmed real near-miss: when a
  dependency's old coordinate stopped resolving (e.g. `spring-cloud-starter-sleuth` dropped from
  a newer Spring Cloud train), a subagent's first instinct was to delete the `implementation`
  line outright to reach a compiling state, which drops the tracing capability entirely rather
  than replacing it. A dependency swap must land on a real equivalent (a maintained successor
  library providing the same capability), not a bare removal that happens to compile.
- **Before finalizing any companion fix, verify it's actually load-bearing — don't trust
  pattern-matched reasoning alone.** Confirmed real incident: a subagent added
  `runtimeOnly "javax.xml.bind:jaxb-api:2.3.1"` to fix a Jakarta-migration issue, with reasoning
  that didn't hold up (`javax.xml.bind` is the legacy javax coordinate, not a Jakarta one) — the
  orchestrator later ran the full verification gate with that line removed and the build stayed
  green, meaning it was never actually necessary. Cheap to check: temporarily remove a
  just-added companion dependency/edit and re-run the gate; if nothing regresses, it wasn't
  justified — drop it rather than leaving speculative fixes in the final diff.
- No rationale comments in code — rationale goes in the report.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.



## OpenRewrite version policy

When this skill delegates source migration to `comp-intel-openrewrite-migration`, it must pass an
exact tuple from that skill's approved matrix. The resolver must not choose a recipe version, plugin
version, recipe-module version, “latest” release, or alternate pairing. If no approved tuple exists
for the required migration hop, stop and report the missing tested tuple.

Renovate determines the dependency destination; it does **not** determine the OpenRewrite tooling
version. Keep the Renovate target fixed and use only the previously tested OpenRewrite tuple for the
compatibility work.

## Steps

1. **Detect a special-cased dependency**

   | Detected as... | Hand off to |
   |---|---|
   | `org.springframework.boot:*`, resolved on 2.x | [spring-boot-2-to-3-migration](../spring-boot-2-to-3-migration/SKILL.md) — lands at its pinned `3.5.x` target, never an earlier 3.x minor, and never chains straight into the next hop |
   | Same, resolved on 3.x | [spring-boot-3-to-4-migration](../spring-boot-3-to-4-migration/SKILL.md) — a separate, re-invoked step after the 2→3 hop's own verification gate passes, not a continuation of the same run |
   | JDK/toolchain, currently Java 17 | [java-17-to-21-migration](../java-17-to-21-migration/SKILL.md) |
   | `com.target.platform:platform-connector-*` | [platform-connector-to-runtime-connector-migration](../platform-connector-to-runtime-connector-migration/SKILL.md) |
   | Gradle wrapper `distributionUrl` crossing a major line, otherwise unowned | [gradle-wrapper-major-upgrade](../gradle-wrapper-major-upgrade/SKILL.md) |

   - Full detection logic: [references/hand-off-detection.md](references/hand-off-detection.md)
   - Why: each case has its own breaking-change surface the generic steps don't cover
   ```
   # example
   dependencyInsight → spring-boot:2.7.18
   → determine the full baseline→target gap first; if target is 4.x, execute 2→3 transformations followed by 3→4 transformations while keeping 4.x as the final fixed target
   ```
   No match → continue to step 2.

2. **Resolve the fixed target, baseline, and real path** (generic only)
   - `dependencyInsight`/tree on the correct classpath (`testCompileClasspath` for `testImplementation`, not `compileClasspath`)
   - trace through direct declarations, catalogs, BOMs, parents, and plugins
   - target precedence: **Renovate-applied version in the current branch/PR > explicit caller `targetVersion`**
   - baseline precedence: **base branch/pre-Renovate declaration/resolution**; do not infer compatibility from the current target alone
   - compute the migration gap `baseline → fixed target`; Renovate changing a version is never evidence that source, configuration, plugins, Java, or Gradle are already compatible
   - never search for latest GA, newest patch, alternate major, or a replacement target
   - if the fixed target is already resolved, do not edit the version again; perform all compatibility migrations needed to bridge the baseline→target gap
   - read migration documentation only for API changes required by that exact target
   - Why: a dependency can be Renovate-invisible — versioned only via an internal plugin/BOM,
     invisible to the Dashboard
   ```
   # example
   PR claims target 4.13.0
   dependencyInsight shows: governed by internal platform BOM pinning 4.11.2
   → bump the BOM/plugin, not a direct version edit (BOM would override it anyway)
   ```

3. **Apply and migrate**
   - stop (no code change) when: only a transitive force reaches target / target isn't GA /
     valid migration needs removal
   - otherwise apply the bump, including any required companion major — a companion bump must
     be justified by an observed compile/runtime failure, not guessed
   - always check [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md) before
     any hand-edit — for every generic dependency, not just special-cased hand-offs
   - hand-edit **only** an item that skill's own dry-run output explicitly flagged as unhandled,
     or an item genuinely out of OpenRewrite's scope entirely (not source/build-file content a
     recipe could ever touch) — including after a recipe fails or violates the Non-Removal
     Invariant on one retry. Never hand-edit something the recipe could plausibly have covered.
   - Why: a hand-edit written before checking for a recipe risks diverging from the recipe's
     own approach elsewhere in the codebase
   ```
   # example — bundled build-tool drift
   wrapper bump surfaces new PMD violations unrelated to the in-scope dependency
   → fix with minimal behavior-preserving edits, report `fixed-with-companion-fix`
   (don't revert the in-scope bump to avoid them)
   ```

4. **Run the mandatory verification gate**
   - Full gate: [references/verification-gate.md](references/verification-gate.md)
     (dependency-insight re-check, full `clean check`, non-zero test count, Testcontainers
     flakiness handling, final diff inspection)
   - Why: a resolving build file edit isn't the same as a verified change

5. **Report**
   - Per dependency: `fixed` / `fixed-with-companion-major` / `fixed-with-companion-fix` / exact
     `stopped-*` / `waiting-upstream` / hand-off result from step 1
   - Include old/new resolved versions, governing parent/BOM/plugin, Renovate-invisible flag,
     migration method used
   - Why: this is what the SCA orchestrator folds into its own aggregated report

## Available References

- [Hand-off Detection](references/hand-off-detection.md) — CRITICAL. Only needed when step 1's
  table flags a candidate match.
- [Verification Gate](references/verification-gate.md) — CRITICAL. Needed every run.
