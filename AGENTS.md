# Android repository workflow

## General behavior

- Before editing, identify the smallest affected Gradle module,
  build variant, and direct dependants.
- During exploration and planning, do not run Gradle commands.
- Finish one logical batch of edits before running verification.
- Prefer reading existing source code and build files over querying
  Gradle for project structure.
- Ignore build/, .gradle/, generated/, .idea/, and other generated
  directories unless the task explicitly concerns them.

## Build restrictions

- Never run a build merely to understand the code.
- Never build after every edited file.
- Never run `clean`.
- Never delete Gradle, Kotlin, KSP, or Android build caches.
- Never use `--no-daemon`.
- Never use `--refresh-dependencies` unless dependencies have actually
  changed or the user explicitly requests it.
- Never run root-level `build`, `check`, `assemble`, `lint`, `test`,
  `connectedCheck`, or all-variant tasks unless explicitly requested.
- Do not run release, minified, R8, baseline-profile, or instrumented
  builds during normal local iteration.
- Run no more than one Gradle process at a time.
- Only the main agent may run Gradle. Subagents must remain read-only.
- After a failed Gradle command, inspect the failure before retrying.
  Never repeat an unchanged failed command.

## Verification ladder

1. Documentation, comments, or formatting only:
   no build.

2. Kotlin or Java change isolated to one module:
   run only that module's compile task.

   Example:
   `.\gradlew.bat :feature:auth:compileDevDebugKotlin --console=plain`

3. Business logic covered by unit tests:
   run only the affected test class or method.

   Example:
   `.\gradlew.bat :feature:auth:testDevDebugUnitTest
   --tests "com.company.auth.AuthViewModelTest" --console=plain`

4. Resources, manifest, dependency injection, navigation,
   build scripts, or cross-module integration:
   run one targeted app assemble task.

   Example:
   `.\gradlew.bat :app:assembleDevDebug --console=plain`

5. Full lint, all tests, release builds, and connected tests:
   run only when explicitly requested, preferably in CI or Codex Cloud.

## Command output

- Use `--console=plain`.
- Do not use `--info`, `--debug`, or full stack traces on successful runs.
- On failure, first inspect the normal error.
- Add `--stacktrace` only when the normal output is insufficient.

## Completion report

At the end, report:

- changed files;
- affected modules;
- exact verification commands executed;
- their results;
- checks intentionally not executed;
- any remaining integration risks.