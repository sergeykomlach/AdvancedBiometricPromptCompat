# Audit the current Android checkout with Codex

Run the Android audit method from GitHub Security Lab against local source with the installed
Codex CLI.
Use Python 3.10 or newer, Git and an authenticated Codex executable.
The scripts use the Python standard library and install nothing.

This implementation adapts the entry-point, investigation and independent-triage stages.
It does not implement the upstream YAML engine, Copilot authentication or CodeQL integration.
Read the [upstream taskflows](https://github.com/GitHubSecurityLab/seclab-taskflows) and
[agent](https://github.com/GitHubSecurityLab/seclab-taskflow-agent) for those interfaces.
The upstream sources reviewed on 19 October 2026 were taskflows revision
`1d0ec1ba188de1e1fc549922cb62f788efe13e3a` and agent revision
`ab02b1fe8ac3d94352332b2c739c579f0d5ae810`.
The current upstream `run_audit.sh` includes mobile collection.
The article's separate `run_mobile.sh` is absent from that revision.
No upstream program or prompt file is downloaded, installed or executed by this port.

## Prepare a run

Run these commands from the checkout root in PowerShell:

```powershell
python -B .\tools\security\mobile_audit.py doctor
python -B .\tools\security\mobile_audit.py prepare --repo . --output .tmp/mobile-audit/my-run
```

The second command works offline and makes no model requests.
It reads tracked production source from the current working tree.
It records HEAD, branch, scoped tracked status and a SHA-256 digest for every included file.
It includes staged and unstaged source bytes rather than silently substituting committed HEAD.
It excludes untracked files unless `--include-untracked` is supplied.
It excludes tests, device-test harnesses, build output, binaries and symlink sources.
It records files that could not be decoded, read or parsed as warnings.
Each included source file has a 4 MiB limit.
It neither fetches refs nor traverses Git submodules.

Repeat `--scope` with explicit checkout-relative paths to include direct shared dependencies.
For another Android repository, pass its root with `--repo` and select its source with `--scope`.
The default scope is the whole repository when `roboform/android` is absent.
Every report directory must be new and remain inside that checkout's `.tmp` directory.

The inventory preserves separate manifest declarations, exported attributes, aliases, inherited
permissions, declared protection levels, intent filters and source lines.
It labels unresolved exported defaults and records resource expressions without evaluating them.
It does not merge manifests or resolve Gradle placeholders, library manifests and resource booleans.
Debug, main, Wear and other source sets retain their original paths.
An agent must establish the applicable variant before treating a declaration as release exposure.

## Run a bounded source review

Start with one task:

```powershell
python -B .\tools\security\mobile_audit.py run `
    --report-dir .tmp/mobile-audit/my-run --task entry-points --timeout 900
```

Resume the remaining tasks and independent triage:

```powershell
python -B .\tools\security\mobile_audit.py run `
    --report-dir .tmp/mobile-audit/my-run --max-tasks 6 --timeout 1800
```

The default limit is one task per invocation.
The default timeout is 900 seconds per task.
Each successful task is saved before the next task starts.
Completed tasks are skipped on resume.
The runner stops after a failed or incomplete task and returns a nonzero exit code.
Rejected model responses up to 2 MiB remain unchanged in `responses/<task>.rejected-<id>.json`.
The state and reports identify that file and the exact invalid source path or evidence line.
Retries keep earlier rejected files and previously accepted incomplete results.
If archiving fails, the pending file remains available and the report records the retention error.
An incomplete task retains its accepted candidates and coverage map across failed continuations.
Resume the same directory to inspect the remaining paths.
It checks source freshness before and after each model call.
A source change invalidates that run and requires another preparation directory.
Timeout handling terminates the spawned process tree.
An interrupted process may leave `run.lock` behind.
Check the PID in that file before removing a stale lock.

The task order is:

1. inspect exported components, deep links, intents and IPC authorization;
2. inspect WebView navigation, frames, bridges, cookies and local content;
3. inspect credential providers, biometric results, crypto binding and session ownership;
4. inspect file imports, temporary files, parser limits and JNI authorization;
5. inspect storage, session lifetime, diagnostics and network boundaries;
6. independently triage every candidate against the actual callers and mitigations.

Pass `--task webview`, `--task credentials`, `--task files-native` or `--task storage-network`
to select another surface.
Triage requires all five surface tasks to complete.
The preparation step also writes inspectable prompts for use in an existing agent session.
The runner regenerates each prompt from the inventory before invoking Codex.
Prompts require a command-scoped `safe.directory` override for that exact checkout when running
Git inside the sandbox. They forbid persistent Git configuration changes and wildcard trust.
Triage must inspect actual inventory source paths even when no candidates were supplied; an empty
`inspected_files` list cannot complete a stage.

The runner selects the installed `codex` executable from PATH.
Pass `--codex C:\path\to\codex.exe` to select another existing executable.
Windows shell launchers are rejected.
An explicit local Python CLI adapter is supported for integration fixtures.
Pass `--model MODEL_ID` only when an explicit model selection is intended.
The runner supplies no model override by default.

Each model request consumes the authenticated Codex account's allowance.
The model reads proprietary source from the selected scopes.
The runner uses `read-only`, `approval_policy="never"`, `--ephemeral`, disabled web search,
disabled multi-agent support and an empty MCP configuration override.
It ignores the user's home CLI configuration and keeps the existing authentication.
On Windows it explicitly selects the existing `elevated` sandbox implementation.
The operator must already have provisioned that implementation.
The runner does not provision or install it.
This selects the Windows isolation backend and retains the `read-only` permission mode.
See the [official Windows sandbox documentation](https://learn.chatgpt.com/docs/windows/windows-sandbox).
Project configuration and the installed CLI's sandbox implementation still apply.
The prompt forbids builds, tests, devices, source script execution, connectors and networking.
These controls do not establish a separate security boundary for a compromised host or CLI.
The runner retains structured results and token usage, and discards raw tool events and stderr.
It does not bypass sandbox or hook trust.

## Interpret the report

Open `report.md`, `report.json` or `audit.sqlite3` inside the run directory.
The SQLite tables are `audit_run`, `tasks`, `components` and `audit_results`.
The inventory and state remain in `inventory.json` and `state.json`.

The report states are:

- `prepared`, with no completed model audit;
- `partial`, with pending, running, failed or incomplete tasks;
- `stale`, with source bytes or Git state changed since preparation;
- `ai_audit_complete`, with all assigned stages completed by the model.

`ai_audit_complete` describes workflow coverage.
It does not prove the absence of vulnerabilities.
Every finding retains `human_verified=false` and `has_vulnerability=null`.
An independent AI verdict may be `supported`, `rejected` or `unverified`.
It does not substitute for human verification or an installed-package reproduction.
The runner validates the result schema, source paths, evidence line bounds and triage coverage.
It cannot establish the correctness of a model's causal trace.
Unfinished storage/network traces must identify the concrete source path, sensitive input and
unresolved security decision or sink. Exhaustive absence proof remains outside source-review claims.
The agent must state missing shared-source, merged-manifest, runtime and external-auth evidence.
Pattern matches are investigation seeds and never populate the vulnerability table on their own.
The inventory retains up to three seeds per rule and file.
The initial prompt shows up to 120 seeds and points the agent to the full inventory.

Refresh local output and recheck freshness without model requests:

```powershell
python -B .\tools\security\mobile_audit.py report --report-dir .tmp/mobile-audit/my-run
```

Run the focused tool tests from the checkout root:

```powershell
python -B -m unittest discover -s tools/security -p test_mobile_audit.py -v
```

The tests create synthetic Git repositories under the checkout's `.tmp` directory.
They exercise the real CLI and report/database writes with a local fake Codex process.
They do not authenticate, contact a model, build Android, install an APK or access a vault.

## Configure the daily audit

Use `mobile-audit.config.json` to select an exact local branch and tracked source scopes.
The RoboForm configuration selects `android/developers/skomlach/enhanced_security`.
The AdvancedBiometricPromptCompat configuration selects `main`, its Android app/library modules and root Gradle metadata.
Neither configuration follows the branch currently displayed in an editor.
The runner pins the configured branch SHA before collecting files.
It does not fetch remote refs.

Preview the configuration without creating a snapshot or contacting a model:

```powershell
python -B .\tools\security\mobile_audit_auto.py --check-only
```

Create an isolated snapshot and inventory without a model request:

```powershell
python -B .\tools\security\mobile_audit_auto.py --prepare-only
```

Run all six audit stages against a detached snapshot of the configured committed SHA:

```powershell
python -B .\tools\security\mobile_audit_auto.py
```

The automatic runner materializes raw tracked Git blobs without checkout hooks or filters.
It disables replacement objects and checks source hashes before accepting a result.
It excludes untracked source, staged changes and unstaged changes in the live checkout.
Commit intended source changes before expecting the daily audit to include them.
The runner uses installed executable paths outside repository directories.
It does not execute audited build scripts or apply fixes.

The per-repository lock prevents overlapping runs.
A successful run for the same SHA and scopes is skipped after its retained evidence is checked.
A failed, incomplete or interrupted run never qualifies for that skip.
The default limits are 30 minutes per stage and 3 hours 30 minutes for the complete run.
A timeout terminates the spawned process tree and retains the partial report.

Inspect `.tmp/mobile-audit-auto/runs/<run-id>/provenance.json` for the source branch and SHA.
The same directory contains stage logs and `snapshot/.tmp/mobile-audit/report.md`.
Snapshots and reports remain available for review.
The runner does not remove them automatically.
Their Git worktree registrations also remain until the operator removes them.

Install or update the current user's Windows Task Scheduler entry:

```powershell
.\tools\security\configure_mobile_audit_task.ps1 -At 05:00 -WhatIf
.\tools\security\configure_mobile_audit_task.ps1 -At 05:00
.\tools\security\configure_mobile_audit_task.ps1 -ShowExisting
```

Use the `tools/security` directory in this checkout.
Its daily audit is configured for 03:00.
Times follow the computer's Kyiv timezone.
The first scheduled occurrence is in the future.
The task runs hidden with the current user's limited privileges while that user is logged in.
It starts when the machine becomes available and does not wake the machine.
Windows prevents a second instance of the same task.

The installer records absolute paths for the installed Python and Codex executables.
Pass `-Python` and `-Codex` to select existing installations.
Run the installer again if those installation paths change.
The installer does not change PowerShell execution policy or install dependencies.

Authorize the repository's source transfer to OpenAI before enabling scheduled model calls.
Scheduled runs use the existing Codex authentication and consume that account's allowance.
They retain the model's limitations and require review of each proposed finding.
They do not send notifications or change source code, commits, branches or remote repositories.

Run the automatic-runner integration tests without model requests:

```powershell
python -B -m unittest discover -s tools/security -p test_mobile_audit_auto.py -v
```
