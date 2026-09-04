# Mandatory Verification Gate

Run for the proposed state of one dependency before accepting it as done.

1. **Re-run dependency insight** — confirm the target actually resolves, not just that the build
   file was edited.

2. **Run the full probed `./gradlew clean check`**, integration tests included, against the
   pre-change baseline.

3. **During interaction diagnosis/bisection** (part of a bundled branch orchestrated by
   [comp-intel-sca-vulnerability-remediation](../../sca-vulnerability-remediation/SKILL.md)) —
   narrowly scoped compile/lint/test commands are OK instead of repeating the full suite per
   combination. Any state ultimately accepted as fixed must still receive the full gate below.

4. **Require a non-zero test count.**
   - `NO-SOURCE` proves nothing.
   - Test names/counts must not shrink from baseline.
   - No new `@Disabled`/`@Ignore` — either is a Non-Removal Invariant violation, not a side
     effect.
   - **A custom-registered Gradle `Test` task reporting `NO-SOURCE` can mean it was never wired
     up at all, not that no matching tests exist.** Confirmed real case: a hand-registered
     `tasks.register('integrationTest', Test) { ... }` block with no `testClassesDirs`/`classpath`
     assignment reports `NO-SOURCE` on every run, including the pre-change baseline — it never
     actually executed any test, in any state. Check this once at the start of a run (compare
     against the baseline branch, not just the change): if the baseline already reported
     `NO-SOURCE` for the same structural reason, that's a pre-existing repo gap to name in the
     report, not a regression this change caused, and not something this workflow should try to
     fix (out of scope) or use to quietly claim a class of tests is "covered" when it never ran.

5. **A regression caused by this change is a stop.** Don't force versions or remove tests to get
   green — revert this dependency, report why no valid migration exists.

6. **Testcontainers / environment flakiness**
   - missing Docker/network/tools, or a failure matching a known flakiness signature:
   ```
   ExceptionInInitializerError -> ContainerLaunchException
   -> RetryCountExceededException -> LogMessageWaitStrategy
   # or: "exited with code 134" / SIGILL on an emulated-Docker host
   ```
   - confirm by retrying the single failing test class in isolation — passes alone → environmental
   - report as a verification limit, not a code bug; report pre-existing/unrelated failures
     without calling the change successful

7. **After the change passes, inspect the diff line by line:**
   - only the intended change remains
   - any temporary OpenRewrite tooling
     ([comp-intel-openrewrite-migration](../../openrewrite-migration/SKILL.md)'s plugin/recipe
     block) is gone
   - protected behavior/tests are intact
   - no rationale comments were added
