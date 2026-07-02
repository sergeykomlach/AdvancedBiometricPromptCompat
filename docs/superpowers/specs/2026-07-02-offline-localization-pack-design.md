# Offline Localization Pack For All Android Modules

## Goal

Add committed offline translations for all existing user-facing string resources across the Android modules in this repo, so runtime localization does not depend on network translation, while also hardening the English fallback by introducing explicit `values-en` copies for every module that currently relies on default `values`.

## Problem

The repo currently keeps almost all string resources only in default `values/strings.xml` files:

- `app`
- `common`
- `biometric`
- `biometric-custom-behavior`
- `biometric-custom-face-tf`
- `biometric-custom-voice`
- `biometric-zkfinger`

The project already has runtime localization support through `LocalizationHelper`, but that path can require internet access and therefore is not a reliable production fallback for offline or privacy-sensitive biometric flows.

There is also a known device-specific risk where `values` and `values-en` do not behave identically. Even if English text equals the default text, the English locale needs an explicit `values-en` resource set instead of implicitly falling back to `values`.

Finally, some user-facing copy can still remain hardcoded in Kotlin/XML call sites after previous localization passes. That creates drift between modules and weakens translation coverage.

## Approved Locale Scope

The committed offline localization pack should cover this expanded production set:

- English: `values-en`
- German: `values-de`
- French: `values-fr`
- Spanish (Spain): `values-es`
- Spanish (Latin America): `values-b+es+419`
- Italian: `values-it`
- Dutch: `values-nl`
- Russian: `values-ru`
- Ukrainian: `values-uk`
- Chinese Simplified: `values-zh-rCN`
- Chinese Traditional: `values-zh-rTW`
- Japanese: `values-ja`
- Korean: `values-ko`
- Indonesian: `values-id`
- Vietnamese: `values-vi`
- Portuguese (Brazil): `values-pt-rBR`
- Hindi: `values-hi`
- Arabic: `values-ar`
- Turkish: `values-tr`

These names are part of the design because Android resource qualifiers are easy to get wrong, especially for Latin America Spanish and Chinese variants.

## Constraints

- Do not depend on runtime translation APIs for the shipped offline experience.
- Do not change resource keys unless a hardcoded string extraction requires a new key.
- Preserve existing module ownership: each module keeps its own translated `strings.xml`.
- Add explicit `values-en` as a separate mandatory step, even when English text is identical to default `values`.
- Do not localize test-only strings, preference keys, logging-only messages, manifest metadata, license headers, or protocol constants.
- Do not introduce a cross-module “shared translations” abstraction that changes current resource ownership.
- Keep translation work safe for Android builds: every new locale file must preserve formatting placeholders, escaping, and key parity.

## Recommended Approach

Build one committed offline localization layer per module, using the current default `values/strings.xml` as the source of truth.

The implementation should proceed in this shape:

1. Audit every module `values/strings.xml` file and extract the canonical key set.
2. Create `values-en/strings.xml` for each module as an exact structural copy of the default file.
3. Create the approved locale directories for each module and add translated `strings.xml` files with the same keys and ordering.
4. Sweep `src/main` code and XML for remaining user-facing hardcoded strings and move them into resources before finalizing locale files.
5. Verify parity between `values`, `values-en`, and every added locale file so no locale silently drops keys.

This keeps the final product simple: normal Android string resources are the offline source of truth, while `LocalizationHelper` remains a runtime enhancement instead of a dependency.

## Alternatives Considered

### 1. Keep relying on runtime translation and cache

Pros:
- almost no committed resource churn;
- minimal diff size.

Cons:
- still network-dependent in the worst case;
- weak offline behavior;
- does not solve the known `values` vs `values-en` drift risk;
- makes production UX dependent on runtime translation infrastructure.

Not acceptable for the requested offline-ready scope.

### 2. Localize only biometric modules first

Pros:
- smaller initial diff;
- easier review.

Cons:
- leaves `app` and `common` inconsistent;
- does not satisfy the requirement to cover all project modules;
- creates a partial offline experience.

Not recommended because the user explicitly asked for all resources in project modules.

### 3. Full offline localization pack for every module

Pros:
- complete offline coverage;
- consistent Android locale behavior;
- explicit `values-en` hardening;
- one-time structure that later work can extend.

Cons:
- largest diff;
- requires disciplined verification to avoid placeholder and key mismatches.

Recommended.

## Target Architecture

### Resource Ownership

Each module continues to own its own strings:

- [app/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/app/src/main/res/values/strings.xml)
- [common/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/common/src/main/res/values/strings.xml)
- [biometric/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/biometric/src/main/res/values/strings.xml)
- [biometric-custom-behavior/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/biometric-custom-behavior/src/main/res/values/strings.xml)
- [biometric-custom-face-tf/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/biometric-custom-face-tf/src/main/res/values/strings.xml)
- [biometric-custom-voice/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/biometric-custom-voice/src/main/res/values/strings.xml)
- [biometric-zkfinger/src/main/res/values/strings.xml](C:/Users/skoml/StudioProjects_5/AdvancedBiometricPromptCompat/biometric-zkfinger/src/main/res/values/strings.xml)

No centralized translation file should be introduced.

### Locale Directory Rules

For every module above, the implementation should create:

- `values-en`
- `values-de`
- `values-fr`
- `values-es`
- `values-b+es+419`
- `values-it`
- `values-nl`
- `values-ru`
- `values-uk`
- `values-zh-rCN`
- `values-zh-rTW`
- `values-ja`
- `values-ko`
- `values-id`
- `values-vi`
- `values-pt-rBR`
- `values-hi`
- `values-ar`
- `values-tr`

With the current tree, every listed module already has a `strings.xml`, so the implementation should create the full approved locale set for all seven modules.

### English Fallback Hardening

`values-en/strings.xml` is not a “translation.” It is a compatibility safeguard.

Rules:

- copy every default string into `values-en` with the same key set;
- keep the same placeholder order and escaping;
- do not rewrite wording during the copy step;
- treat `values-en` parity as a hard verification gate.

### Hardcoded String Sweep

The sweep should target only `src/main` production sources and layouts in the scoped modules.

In scope:

- dialog labels;
- help/error/status text;
- content descriptions;
- button text;
- prompt hints;
- other user-visible copy in Kotlin, Java, or XML.

Out of scope:

- tests;
- log tags and debug text;
- non-user-visible constants;
- serialized field names / preference keys;
- intent actions, authorities, protocol identifiers;
- license text.

If a remaining hardcoded user-facing string is found, it should be extracted into the owning module `values/strings.xml` first, then mirrored into every locale file.

## Translation Quality Rules

The work should optimize for safe shipped resources, not “creative” rephrasing.

Rules:

- preserve formatting placeholders exactly: `%1$s`, `%1$d`, newline usage, HTML tags, and escaped characters;
- preserve semantic strength of biometric warnings, lockout messages, and privacy prompts;
- keep short UI labels short enough for mobile layouts;
- keep content descriptions explicit and accessibility-safe;
- when a default string is intentionally technical, do not over-localize into vague wording.

## Verification Strategy

Verification should combine structural checks and compile proof.

### Structural Checks

- key parity between `values/strings.xml` and every generated `values-*/strings.xml`;
- exact parity between `values` and `values-en`;
- placeholder parity for every locale file;
- no new user-facing hardcoded strings remain in the scoped modules after extraction, other than explicitly accepted exceptions.

### Build Checks

Use compile/resource proof gates that are already reliable in this repo:

- `.\gradlew.bat :common:compileDebugKotlin`
- `.\gradlew.bat :biometric:compileDebugKotlin`
- `.\gradlew.bat :biometric-custom-behavior:compileDebugKotlin`
- `.\gradlew.bat :biometric-custom-face-tf:compileDebugKotlin`
- `.\gradlew.bat :biometric-custom-voice:compileDebugKotlin`
- `.\gradlew.bat :biometric-zkfinger:compileDebugKotlin`
- `.\gradlew.bat :app:compileDebugKotlin`

Resource-focused checks are also important because this task mostly changes XML:

- `.\gradlew.bat :app:mergeDebugResources`
- `.\gradlew.bat :common:packageDebugResources :biometric:packageDebugResources :biometric-custom-behavior:packageDebugResources :biometric-custom-face-tf:packageDebugResources :biometric-custom-voice:packageDebugResources :biometric-zkfinger:packageDebugResources`

The environment-sensitive `testDebugUnitTest` runner remains a non-blocking proof source here; compile/resource proof is the primary gate.

## Rollout Strategy

The implementation should land as one coherent localization feature, but the work itself should be staged to keep risk manageable:

1. inventory keys and create `values-en` copies;
2. extract any remaining hardcoded user-visible strings;
3. add the expanded locale directories and translated files module by module;
4. run structural parity checks after each module batch;
5. finish with combined compile/resource proof.

This sequencing minimizes the chance that translations are built on top of an unstable key set.

## Scope Boundaries

In scope:

- offline translation resources for every existing module `strings.xml`;
- explicit `values-en` copies;
- extraction of remaining hardcoded user-facing strings in scoped modules;
- verification for key and placeholder parity;
- compile/resource proof.

Out of scope:

- changing runtime translation infrastructure behavior;
- adding new feature UI unrelated to localization;
- translating tests or developer-only logs;
- restructuring module boundaries or prompt architecture;
- adding locales outside the approved expanded set.

## Success Criteria

This design is successful if:

1. every scoped module has committed offline `strings.xml` files for the approved locale set;
2. every scoped module has an explicit `values-en/strings.xml`;
3. user-facing hardcoded production strings in the scoped modules are moved into resources;
4. `values` and `values-en` have exact key parity;
5. locale files preserve placeholder correctness and compile cleanly;
6. the repo builds through the agreed compile/resource verification gates without localization regressions.
