# Hand-off Detection

How to confirm each special-cased dependency before falling back to the generic workflow, and
how to fold the hand-off skill's report back into this skill's own.

## Spring Boot

- Detect via `dependencyInsight` on `org.springframework.boot:spring-boot-dependencies` (or
  whichever artifact actually governs it — direct BOM, platform plugin, or parent).
- Confirm the **currently resolved** major/minor — don't infer it from the target version.

```
resolved 2.x → hand off to spring-boot-2-to-3-migration
              (regardless of whether the ultimate target is 3.x or 4.x —
              this hop always lands at its own pinned 3.5.x, never a
              different 3.x minor, and is never skipped or shortened)
resolved 3.x → hand off to spring-boot-3-to-4-migration
resolved 4.x+ → not a hand-off case; if target is a minor/patch within 4.x,
              treat as generic (step 2 onward)
```

- **2.x → 4.x always goes through exactly two hops, in order: 2.x → 3.5.x, then 3.5.x → 4.x.**
  There is no direct 2.x → 4.x path and no skipping the 3.5.x landing, regardless of what the
  caller's ultimate target is.
- If the 2->3 skill's resulting version is still short of the caller's ultimate target (e.g.
  target 4.x, repo now on 3.x) → re-run this same detection table for the next hop. Don't chain
  directly into the 3->4 skill without re-detecting, and don't run both hops inside the same
  subagent turn without the first hop's own verification gate completing first — confirmed real
  failure mode: a single run given both hops at once made premature Boot-4-specific edits while
  still supposedly working the 2→3 hop, because nothing forced a stop in between.

## JDK / toolchain

- Detect via `./gradlew -v` (the JVM line) and the Gradle toolchain block if present — not a
  manifest; the JDK is rarely declared as a normal Gradle dependency.

```
currently Java 17 → hand off to java-17-to-21-migration
any other version → no dedicated skill yet; stop and report — don't attempt
                     the bump generically (toolchain/Docker/CI implications
                     the generic workflow doesn't cover)
```

## Gradle wrapper / build-tool major version

- Detect via `gradle/wrapper/gradle-wrapper.properties`'s `distributionUrl` — not a manifest
  entry, and not something `dependencyInsight` will ever show, since it isn't a JVM dependency.
- Confirmed real-world gap: a Renovate branch proposing a bare wrapper major bump (e.g.
  `gradle-7.x`/`8.x` line -> `9.x`) with no accompanying Spring Boot/JDK/Platform Connector
  change attached showed up with no owning hand-off case here — it would otherwise silently fall
  through to the generic path (step 2 onward), which has no concept of wrapper-distribution
  compatibility, plugin-portal resolution changes, or the DSL-breaking changes a Gradle major can
  carry.

```
wrapper distributionUrl crosses a major line (e.g. 7.x/8.x -> 9.x)
  and no other detected case (Spring Boot/JDK/Platform Connector) owns this bump
    → hand off to comp-intel-gradle-wrapper-major-upgrade
same major line (a minor/patch wrapper bump, e.g. 8.5 -> 8.11)
    → generic path (step 2 onward) — routine, no dedicated skill needed
```

- A wrapper major bump surfacing *as a requirement* of another hand-off (e.g.
  spring-boot-2-to-3-migration's wrapper-floor step, or java-17-to-21-migration's step 2) is
  owned by that migration's own step, not this case — this entry is only for a wrapper major bump
  that arrives as its own, otherwise-unowned Renovate proposal or user request.

## Platform Connector

- Detect via the same evidence Phase 0 of
  [comp-intel-platform-connector-to-runtime-connector-migration](../../platform-connector-to-runtime-connector-migration/SKILL.md)
  uses: direct `com.target.platform:platform-connector-*` coordinates, or a wrapper/convention
  plugin pulling it in transitively (check any unfamiliar `<team>-gradle-plugin`-shaped plugin
  before assuming it's unrelated).

```
found     → hand off directly, same `module` scope
not found → continue with Spring Boot / JDK / generic
```

## Folding a hand-off skill's report back in

- Pass the hand-off skill's own report through as this skill's entry for that dependency — don't
  summarize or re-derive it.
- Label it clearly as a hand-off result (name the skill invoked), so
  [comp-intel-sca-vulnerability-remediation](../../sca-vulnerability-remediation/SKILL.md)'s
  aggregated report can distinguish a generic bump from a special-cased migration.
