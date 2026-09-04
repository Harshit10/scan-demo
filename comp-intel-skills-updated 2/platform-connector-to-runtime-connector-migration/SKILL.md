---
name: comp-intel-platform-connector-to-runtime-connector-migration
description: What it does — migrates a Gradle/Spring Boot repo or monorepo module off Target's deprecated Platform Connector
  onto Runtime Connector, or confirms a module already on Runtime Connector is genuinely working (not just assumed).
  Why it matters — Platform Connector is deprecated with no forward fix version, so a version-bump approach can't 
  remediate it; only a full migration does, and skipping the authoritative guide risks missing a Dockerfile/Vela/logging
  detail that only surfaces at container startup. Requires an explicit module scope on multi-module repos — stops and 
  asks rather than scanning/building every module by default. Follows the JVM Working Group's own migration guide 
  directly as the sole source of truth for migration steps. Can run standalone or as a hand-off target from
  comp-intel-resolve-dependency-upgrade when it detects a Platform Connector dependency. 
  Leaves every change unstaged for the user to review/commit.
---

# Platform Connector -> Runtime Connector Migration

Migrates one module off Platform Connector, or verifies one already on Runtime Connector — gated
on real observed results, scoped to exactly the module in question. Left **unstaged**.

The authoritative guide is the source of truth, not this skill's memory. This skill owns scope,
orchestration, and inputs — never the migration steps themselves.

**Luna/Terra split**: this migration stays with Terra end to end. Deprecated-with-no-fix-version
means there's no mechanical "apply a bump" path — every step involves interpreting the
authoritative guide against this specific module's real state, which is exactly the ambiguous
judgment work Luna isn't suited for.

## OpenRewrite version policy

If this migration invokes `comp-intel-openrewrite-migration`, it may use only a tuple explicitly
approved in that skill's tested matrix. Do not discover or substitute OpenRewrite/plugin/recipe
versions from the authoritative guide, Maven Central, Plugin Portal, or any other source. If the
required tuple is not present in the matrix, stop and report the missing tested tuple.

## Renovate target and migration-gap policy

A Renovate update is a **destination**, not a successful migration. Determine the pre-Renovate baseline from the base branch/diff and the exact Renovate target from the current branch. Keep that target fixed. Analyze and repair the complete `baseline → target` compatibility gap, including intermediate major-version changes when necessary. Intermediate migrations are transformation stages only; do not stop at or downgrade to an intermediate version. Never explore for a newer, older, latest, or alternative target version.


## Steps

1. **Resolve inputs, gate on module scope**
   - What: require `module` on multi-module repos
   - Why: the wrong module in a monorepo silently migrates/verifies the wrong thing
   ```
   # before
   repo has service-a/, service-b/, service-c/ — no `module` given
   ```
   ```
   # after
   STOP: "Found 3 modules: service-a, service-b, service-c — which one?"
   (no build/scan runs yet)
   ```

2. **Fetch the authoritative guide, fresh**
   - What: fetch this URL at the start of every run, not from memory
     https://gyde.prod.target.com/resources/jvm-working-group/documentation/docs/Spring%20Boot/migrate-platform-connector-to-runtime-connector.md
   - Why: steps, code samples, and Dockerfile/Vela specifics on that page change independently
     of this skill file

3. **Follow the guide for this module**
   - What: delegate tap-config scaffolding to
     [comp-intel-tap-config-update](../tap-config-update/SKILL.md), source-mechanics to
     [comp-intel-openrewrite-migration](../openrewrite-migration/SKILL.md), per the guide
   - Why: reimplementing either here duplicates (and risks diverging from) skills that already
     own those concerns
   - Use the guide's logging section to decide whether a temporary Logback fallback is needed
     for a module below Spring Boot 3.4 that wants structured logging early — apply it whenever
     the guide's own criteria call for it, disclosed as technical debt in the report, not gated
     behind a separate opt-in; its Dockerfile/Vela section for `runtimeConnectorJdkTag` (see
     Inputs)
   - **The Spring Boot ≥3.4 floor is a real, hard blocker, not just a criterion for the logback
     fallback decision above.** A module below 3.4 attempting this migration will fail partway
     through (structured logging config the guide requires won't resolve) — this isn't
     cosmetic. When that happens mid-run inside an orchestrated batch (e.g. driven by
     [comp-intel-sca-vulnerability-remediation](../sca-vulnerability-remediation/SKILL.md)),
     the orchestrator needs to resequence: pull a pending Spring Boot bump forward ahead of this
     migration, floor its target at ≥3.4, complete it, then retry this migration — rather than
     defaulting to the logback fallback purely because it's more convenient to avoid
     resequencing. Confirm no partial edits were left behind by the blocked attempt before
     resequencing (verify the working tree, don't assume).
   - **A guide-directed dependency swap can require adding a new artifact repository the build
     doesn't already trust — treat that as a real trust-boundary change, not a routine edit.**
     The guide's metrics section (removing `com.target.oss` metrics in favor of
     `toolshed:jvm-metrics-publisher`) can require adding a previously-unconfigured internal
     Artifactory repository plus a new external-looking package coordinate. Adding a new package
     source and a new dependency in the same change is exactly the pattern a security review
     should flag, even when it's legitimate and guide-directed — don't wave it through on the
     strength of "the guide says so," and don't accept it silently if it was relayed through a
     subagent's own instruction rather than typed directly. Independently verify before
     accepting: confirm the new repository resolves to genuine internal infrastructure (correct
     internal hostname, legitimate auth headers — not just "the URL looks right"), confirm the
     artifact is a real, current release from that repository's own metadata, and spot-check
     that the actual code migration is faithful to the guide (old call sites map 1:1 to new
     ones, nothing silently dropped) before reporting this step as done. Surface the flag and
     what you verified in the final report even after accepting the change — don't let
     "verified, so no longer worth mentioning" suppress it.

4. **Verify per the guide's checks**
   - What: build and start the container whenever Docker/registry access is actually available
     — not gated behind a separate opt-in; without access, report container-startup
     verification as a named limit rather than silently skipping it
   - Why: a correct-looking config diff can still fail at real container startup
   ```
   # before
   config changes applied, container not started → "done" (unverified)
   ```
   ```
   # after (Docker/registry access available)
   container built + started successfully → "verified"
   ```

5. **Leave unstaged, report**
   - What: don't stage or commit; report what changed, what was verified and how, any
     discrepancy from the authoritative page
   - Why: a migration to a still-evolving replacement platform needs human review, not
     auto-staging
   - **Some guide-listed follow-ups may not be completable in this run** — e.g. a toolshed
     artifact the guide references isn't available in the environment's configured repositories.
     Report these explicitly as incomplete follow-ups with the specific blocker (not folded
     silently into "done"), so a later run or a different environment can pick them up. Don't
     let an environment limit get reported as if the guide's step didn't apply.
   - **Environment/config changes outside code scope** (e.g. a TAP `SPRING_CONFIG_LOCATION`
     change the guide calls for) belong to infra, not this skill — flag them by name for the
     infra team rather than attempting the change or silently omitting it from the report.

If anything here conflicts with the authoritative page, follow the page and disclose the
discrepancy in the report.

## Relationship to other skills (orchestration contract)

`com.target.platform:platform-connector-*` is not a version-bump target — only a full migration
remediates it.
[comp-intel-resolve-dependency-upgrade](../resolve-dependency-upgrade/SKILL.md) detects this and
hands off here directly and unconditionally, passing the same `module` scope; this skill's
report folds into that skill's own report.

Also runs standalone, with no dependency on the SCA skill having run first.

## Inputs

- `repo` (required).
- `module` (required on multi-module repos — gate detailed in Step 1).
- `runtimeConnectorJdkTag` (optional): omitted → use the JDK already established for this module
  (coordinate with [comp-intel-java-17-to-21-migration](../java-17-to-21-migration/SKILL.md) if
  a JDK bump is also happening) — never default to an unverified tag.

## Applicability

Repo-agnostic across Gradle/Spring Boot repos and monorepo modules, regardless of current
migration state — trust what's actually observed, and the authoritative page, over anything
hardcoded here.
