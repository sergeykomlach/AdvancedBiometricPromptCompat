# TaskDescription compatibility design

## Goal

Keep `StatusBarTools.setNavBarAndStatusBarColors(...)` source- and binary-compatible while also updating the owning activity's `ActivityManager.TaskDescription` across the complete API range supported by `common` (API 23 and newer).

## Compatibility policy

- API 33 and newer: build `TaskDescription` through `ActivityManager.TaskDescription.Builder`. Use the resolved status-bar color for primary, background, and status-bar fields, and the resolved navigation-bar color for the navigation-bar field.
- API 28 through 32: use the public `TaskDescription` constructor that accepts an icon resource and primary color. Only primary color is publicly configurable on these API levels.
- API 23 through 27: load the application's icon through `ApplicationInfo.loadIcon(PackageManager)`, render that drawable to a valid bitmap, and pass it to the legacy public `TaskDescription` constructor together with the primary color. Only primary color is publicly configurable on these API levels.
- Do not use reflection or hidden Android APIs.

Primary and background task colors must be opaque. When the resolved status-bar color is not opaque, leave these fields unspecified instead of forcing an arbitrary fallback color or risking an exception. Status- and navigation-bar task colors retain their resolved values on API 33 and newer.

## Integration

The existing status- and navigation-bar helpers will expose their final resolved colors internally after theme, contrast, and legacy-icon adjustments. Once both colors have been applied to the window, `StatusBarTools` will update `TaskDescription` for the activity owning that window.

The public method signature remains unchanged. If no owning `Activity` can be resolved, task-description synchronization is a no-op. Existing exception logging behavior remains in place.

## Verification

- Add focused unit coverage for API-band selection and task-color mapping, including opaque and translucent primary colors.
- Demonstrate the new tests failing before production implementation and passing afterward.
- Run the focused `common` unit tests, Kotlin compilation, Android lint relevant to new-API guards, and `git diff --check`.
- Review the final diff for Android security concerns, especially hidden-API use, context unwrapping, and unintended exposure or persistence of sensitive data.

## Non-goals

- No public API changes.
- No reflection, OEM-specific task-description hooks, or new dependencies.
- No custom task label. Use the application's own icon only where a legacy constructor requires one. Do not change the system-bar icon policy or call sites outside `common`.
- No commit without explicit user approval.
