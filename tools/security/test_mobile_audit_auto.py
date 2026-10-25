import json
from datetime import datetime
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import unittest

from mobile_audit_auto import process_options, stop_process_tree
from mobile_audit_inventory import resolve_git


SCRIPT = Path(__file__).with_name("mobile_audit_auto.py").resolve()
INSTALLER = SCRIPT.with_name("configure_mobile_audit_task.ps1")
SCRATCH = next(parent for parent in SCRIPT.parents if (parent / ".git").exists()) / ".tmp"
ENTRY = "src/main/Entry.kt"
BRANCH = "audit-fixture"
ENGINE = r'''
import json, os, pathlib, stat, sys, time
args = sys.argv[1:]
root = pathlib.Path(args[args.index("--cd") + 1])
prompt = sys.stdin.read()
mode = os.environ.get("MOBILE_AUDIT_FIXTURE_MODE", "ok")
log = pathlib.Path(os.environ["MOBILE_AUDIT_FIXTURE_LOG"])
task = prompt.split("Task: ", 1)[1].split("\n", 1)[0]
with log.open("a", encoding="utf-8") as stream:
    stream.write(json.dumps({"args": args, "task": task,
                             "source": (root / "src/main/Entry.kt").read_text()}) + "\n")
if mode == "wait":
    until = time.monotonic() + 15
    while not log.with_suffix(".release").exists() and time.monotonic() < until:
        time.sleep(0.05)
if mode == "timeout":
    time.sleep(20)
if mode == "failure":
    sys.exit(9)
if mode == "stale":
    path = root / "src/main/Entry.kt"
    path.chmod(stat.S_IWRITE | stat.S_IREAD)
    path.write_text("mutated source\n")
result = {"completed": mode != "incomplete", "summary": "Fixture inspection",
          "inspected_files": ["src/main/Entry.kt"], "findings": [], "reviews": [],
          "limitations": ["Synthetic fixture"]}
output = pathlib.Path(args[args.index("--output-last-message") + 1])
output.write_text(json.dumps(result), encoding="utf-8")
print(json.dumps({"type": "turn.completed", "usage": {"input_tokens": 1, "output_tokens": 1}}))
'''


class MobileAuditAutoTest(unittest.TestCase):
    def setUp(self):
        SCRATCH.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="mobile-audit-auto-test-", dir=SCRATCH)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.git_executable = str(resolve_git(self.root))
        self.config = self.root / "tools/security/mobile-audit.config.json"
        self.base = self.root / ".tmp/mobile-audit-auto"
        self.engine = self.root / ".tmp/fake_codex.py"
        self.log = self.root / ".tmp/engine.jsonl"
        self.write(ENTRY, "fun onNewIntent() = Unit\n")
        self.write("src/main/AndroidManifest.xml",
                   '<manifest package="com.example"><application/></manifest>')
        self.write(".gitattributes", "*.kt filter=fixture\n")
        self.write(".tmp/fake_codex.py", ENGINE)
        self.write_config()
        self.git("init", "--quiet")
        self.git("symbolic-ref", "HEAD", "refs/heads/" + BRANCH)
        self.git("add", "--", "src", ".gitattributes")
        self.commit()
        self.sha = self.git("rev-parse", "HEAD").decode().strip()
        self.environment = dict(os.environ)
        self.environment["MOBILE_AUDIT_FIXTURE_LOG"] = str(self.log)

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def write_config(self, **updates):
        config = {"format": 1, "branch": BRANCH, "scopes": ["src"],
                  "task_timeout_seconds": 5, "run_timeout_seconds": 150}
        config.update(updates)
        self.config.parent.mkdir(parents=True, exist_ok=True)
        self.config.write_text(json.dumps(config), encoding="utf-8")

    def git(self, *arguments):
        environment = {key: value for key, value in os.environ.items()
                       if not key.upper().startswith("GIT_")}
        result = subprocess.run([self.git_executable, "-C", str(self.root),
                                 "-c", "safe.directory=" + str(self.root),
                                 "-c", "core.hooksPath=" + os.devnull,
                                 "-c", "core.autocrlf=false", *arguments],
                                env=environment, capture_output=True, timeout=15)
        self.assertEqual(0, result.returncode, result.stderr.decode("utf-8", errors="replace"))
        return result.stdout

    def commit(self):
        self.git("-c", "user.name=Audit Fixture", "-c", "user.email=audit@example.invalid",
                 "-c", "commit.gpgsign=false", "commit", "--quiet", "-m", "Fixture")

    def command(self, *arguments):
        return [sys.executable, "-B", str(SCRIPT), "--config", str(self.config),
                "--codex", str(self.engine), *arguments]

    def cli(self, *arguments, mode="ok"):
        environment = dict(self.environment)
        environment["MOBILE_AUDIT_FIXTURE_MODE"] = mode
        command = self.command(*arguments)
        process = subprocess.Popen(command, cwd=self.root, env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True, encoding="utf-8", **process_options())
        try:
            stdout, stderr = process.communicate(timeout=90)
        except subprocess.TimeoutExpired:
            stop_process_tree(process)
            process.communicate()
            raise
        return subprocess.CompletedProcess(command, process.returncode, stdout, stderr)

    def output(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        return json.loads(result.stdout)

    def invocations(self):
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text(encoding="utf-8").splitlines()]

    def test_check_only_validates_without_snapshot_or_model(self):
        result = self.output(self.cli("--check-only"))
        self.assertEqual("checked", result["status"])
        self.assertEqual(self.sha, result["source_sha"])
        self.assertEqual(BRANCH, result["source_branch"])
        self.assertFalse(self.base.exists())
        self.assertEqual([], self.invocations())

    @unittest.skipUnless(os.name == "nt", "Windows executable search")
    def test_git_ignores_checkout_and_relative_path_executables(self):
        for name in ("git.exe", "git.cmd", "git.bat", "relative-tools/git.exe",
                     "relative-tools/git.cmd", "relative-tools/git.bat"):
            self.write(name, "INVALID SENTINEL FILE. NEVER EXECUTE.\n")
        self.environment["PATH"] = os.pathsep.join(
            ["relative-tools", ".", str(self.root), str(self.root / "relative-tools"),
             self.environment["PATH"]])
        self.assertEqual("prepared", self.output(self.cli("--prepare-only"))["status"])
        self.assertFalse((self.base / "success.json").exists())
        self.assertEqual([], self.invocations())

    @unittest.skipUnless(os.name == "nt", "Windows executable search")
    def test_codex_uses_only_absolute_installed_path_entries(self):
        installed = Path(sys.executable).resolve()
        name = installed.name
        for name in (name, installed.stem + ".cmd", installed.stem + ".bat",
                     "relative-tools/" + name):
            self.write(name, "INVALID SENTINEL FILE. NEVER EXECUTE.\n")
        self.environment["PATH"] = os.pathsep.join(
            ["relative-tools", ".", str(self.root), str(self.root / "relative-tools"),
             str(installed.parent), self.environment["PATH"]])
        result = self.output(self.cli("--check-only", "--codex", installed.name))
        self.assertEqual(str(installed), result["codex"])
        self.assertFalse(self.base.exists())
        self.assertEqual([], self.invocations())

    def test_codex_rejects_checkout_binaries_shell_launchers_and_relative_fixture_paths(self):
        for name in ("codex.exe", ".tmp/codex.exe", ".tmp/codex.cmd", ".tmp/codex.bat"):
            path = self.write(name, "INVALID SENTINEL FILE. NEVER EXECUTE.\n")
            with self.subTest(path=name):
                self.assertNotEqual(0, self.cli("--check-only", "--codex", str(path)).returncode)
        self.assertNotEqual(0, self.cli("--check-only", "--codex", ".tmp/fake_codex.py").returncode)
        self.assertEqual("checked", self.output(self.cli("--check-only"))["status"])
        self.assertFalse(self.base.exists())
        self.assertEqual([], self.invocations())

    def test_replacement_objects_do_not_change_the_pinned_commit_sources(self):
        original = (self.root / ENTRY).read_bytes()
        original_blob = self.git("rev-parse", self.sha + ":" + ENTRY).decode().strip()
        self.write(ENTRY, "fun substitutedSource() = Unit\n")
        self.git("add", "--", ENTRY)
        self.commit()
        replacement_commit = self.git("rev-parse", "HEAD").decode().strip()
        replacement_blob = self.git("rev-parse", "HEAD:" + ENTRY).decode().strip()
        self.git("update-ref", "refs/heads/" + BRANCH, self.sha)
        self.environment["GIT_NO_REPLACE_OBJECTS"] = "0"
        before_index = (self.root / ".git/index").read_bytes()
        for old, replacement in ((self.sha, replacement_commit), (original_blob, replacement_blob)):
            with self.subTest(object=old):
                self.git("update-ref", "refs/replace/" + old, replacement)
                self.assertNotEqual(original, self.git("show", self.sha + ":" + ENTRY))
                result = self.output(self.cli("--prepare-only"))
                self.assertEqual(self.sha, result["source_sha"])
                self.assertEqual(original, (Path(result["snapshot"]) / ENTRY).read_bytes())
                inventory = json.loads(Path(result["report"]).with_name("inventory.json").read_text())
                self.assertEqual(self.sha, inventory["head"])
                self.assertEqual([""], inventory["tracked_status"])
                self.assertEqual(replacement,
                                 self.git("rev-parse", "refs/replace/" + old).decode().strip())
                self.git("update-ref", "-d", "refs/replace/" + old)
        self.assertEqual(before_index, (self.root / ".git/index").read_bytes())
        self.assertEqual([], self.invocations())

    def test_prepare_only_keeps_pinned_sources_and_live_index_unchanged(self):
        self.write(ENTRY, "staged source\n")
        self.git("add", "--", ENTRY)
        self.write(ENTRY, "working source\n")
        self.write("src/main/Untracked.kt", "private untracked source\n")
        before_index = (self.root / ".git/index").read_bytes()
        before_head = (self.root / ".git/HEAD").read_bytes()
        result = self.output(self.cli("--prepare-only"))
        snapshot = Path(result["snapshot"])
        self.assertEqual("prepared", result["status"])
        self.assertEqual(self.sha, result["source_sha"])
        self.assertEqual("fun onNewIntent() = Unit\n", (snapshot / ENTRY).read_text())
        self.assertFalse((snapshot / "src/main/Untracked.kt").exists())
        self.assertEqual("working source\n", (self.root / ENTRY).read_text())
        self.assertEqual(before_index, (self.root / ".git/index").read_bytes())
        self.assertEqual(before_head, (self.root / ".git/HEAD").read_bytes())
        self.assertFalse((self.base / "success.json").exists())
        self.assertEqual([], self.invocations())

    def test_snapshot_preserves_sources_beyond_the_windows_path_limit(self):
        relative = "src/" + "/".join(["nested-directory-" * 2] * 4) + "/Deep.kt"
        self.write(relative, "fun deep() = Unit\n")
        self.git("add", "--", relative)
        self.commit()
        result = self.output(self.cli("--prepare-only"))
        source = Path(result["snapshot"]) / relative
        self.assertGreater(len(str(source)), 260)
        self.assertEqual("fun deep() = Unit\n", source.read_text())
        inventory = json.loads(Path(result["report"]).with_name("inventory.json").read_text())
        self.assertEqual([""], inventory["tracked_status"])
        self.assertIn(relative, [item["path"] for item in inventory["files"]])

    def test_snapshot_records_git_executable_modes(self):
        self.write("src/build.gradle", "// executable fixture\n")
        self.git("add", "--", "src/build.gradle")
        blob = self.git("rev-parse", ":src/build.gradle").decode().strip()
        self.git("update-index", "--cacheinfo", "100755," + blob + ",src/build.gradle")
        self.commit()
        result = self.output(self.cli("--prepare-only"))
        files = json.loads((Path(result["snapshot"]).parent / "snapshot-files.json").read_text())
        modes = {item["path"]: item.get("mode") for item in files}
        self.assertEqual("100755", modes["src/build.gradle"])
        self.assertEqual("100644", modes[ENTRY])

    @unittest.skipIf(os.name == "nt", "POSIX executable bits")
    def test_executable_snapshot_finishes_with_clean_git_status(self):
        executable = self.write("src/build.gradle", "// executable fixture\n")
        executable.chmod(0o755)
        self.git("config", "core.filemode", "true")
        self.git("add", "--", "src/build.gradle")
        self.commit()
        result = self.output(self.cli())
        snapshot = Path(result["snapshot"])
        self.assertEqual("ai_audit_complete", result["status"])
        self.assertEqual(0o555, stat.S_IMODE((snapshot / "src/build.gradle").stat().st_mode))
        self.assertEqual(0o444, stat.S_IMODE((snapshot / ENTRY).stat().st_mode))
        self.assertEqual(b"", self.git("-C", str(snapshot), "status", "--porcelain=v1",
                                       "--untracked-files=no", "--", "src"))

    def test_all_six_stages_complete_before_deduplication(self):
        first = self.output(self.cli())
        self.assertEqual("ai_audit_complete", first["status"])
        calls = self.invocations()
        self.assertEqual(["entry-points", "webview", "credentials", "files-native",
                          "storage-network", "triage"], [call["task"] for call in calls])
        for call in calls:
            self.assertEqual("read-only", call["args"][call["args"].index("--sandbox") + 1])
            self.assertNotIn("--model", call["args"])
        second = self.output(self.cli())
        self.assertEqual("already_audited", second["status"])
        self.assertEqual(6, len(self.invocations()))
        self.assertEqual(1, len(list((self.base / "runs").iterdir())))
        self.write(ENTRY, "fun changed() = Unit\n")
        self.git("add", "--", ENTRY)
        self.commit()
        third = self.output(self.cli())
        self.assertEqual("ai_audit_complete", third["status"])
        self.assertNotEqual(first["source_sha"], third["source_sha"])
        self.assertEqual(12, len(self.invocations()))

    def test_failed_incomplete_and_stale_results_never_deduplicate(self):
        for mode in ("failure", "incomplete", "stale"):
            with self.subTest(mode=mode):
                result = self.cli(mode=mode)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertEqual("failed", json.loads(result.stdout)["status"])
                self.assertFalse((self.base / "success.json").exists())
        result = self.output(self.cli())
        self.assertEqual("ai_audit_complete", result["status"])
        self.assertEqual(9, len(self.invocations()))

    def test_tampered_retained_report_is_not_reused(self):
        first = self.output(self.cli())
        report = Path(first["report"]).with_name("state.json")
        state = json.loads(report.read_text())
        state["tasks"]["triage"]["state"] = "pending"
        report.write_text(json.dumps(state))
        self.assertEqual("ai_audit_complete", self.output(self.cli())["status"])
        self.assertEqual(12, len(self.invocations()))

    def test_retained_snapshot_manifest_cannot_escape_its_snapshot(self):
        first = self.output(self.cli())
        run = Path(first["snapshot"]).parent
        manifest = json.loads((run / "snapshot-files.json").read_text())
        outside = self.root / ".tmp/manifest-outside.kt"
        outside.write_bytes((Path(first["snapshot"]) / manifest[0]["path"]).read_bytes())
        manifest[0]["path"] = Path(os.path.relpath(outside, first["snapshot"])).as_posix()
        (run / "snapshot-files.json").write_text(json.dumps(manifest))
        self.assertEqual("ai_audit_complete", self.output(self.cli())["status"])
        self.assertEqual(12, len(self.invocations()))

    def test_model_timeout_cancels_and_releases_mutex(self):
        self.write_config(task_timeout_seconds=1)
        result = self.cli(mode="timeout")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.base / "success.json").exists())
        self.assertEqual("ai_audit_complete", self.output(self.cli())["status"])

    def test_live_owner_blocks_an_overlapping_run(self):
        self.write_config(task_timeout_seconds=20, run_timeout_seconds=240)
        environment = dict(self.environment, MOBILE_AUDIT_FIXTURE_MODE="wait")
        process = subprocess.Popen(self.command(), cwd=self.root, env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE, **process_options())
        try:
            until = time.monotonic() + 15
            while not self.log.exists() and process.poll() is None and time.monotonic() < until:
                time.sleep(0.05)
            self.assertTrue(self.log.exists())
            second = self.output(self.cli("--prepare-only"))
            self.assertEqual("already_running", second["status"])
            self.log.with_suffix(".release").write_text("release")
            stdout, stderr = process.communicate(timeout=20)
            self.assertEqual(0, process.returncode, stdout.decode() + stderr.decode())
            self.assertEqual(6, len(self.invocations()))
        finally:
            if process.poll() is None:
                stop_process_tree(process)
                process.communicate()

    def test_snapshot_does_not_execute_hooks_or_filters(self):
        marker = self.root / ".tmp/executed.txt"
        self.write("src/.gitattributes", "*.kt filter=conditional\n")
        self.git("add", "--", "src/.gitattributes")
        self.commit()
        hook = self.root / ".git/hooks/post-checkout"
        hook.write_text('#!/bin/sh\nprintf unsafe > "' + marker.as_posix() + '"\n')
        hook.chmod(0o755)
        filter_script = self.write(".tmp/unsafe_filter.py",
                                   "import pathlib, sys\npathlib.Path(" + repr(str(marker)) +
                                   ").write_text('unsafe')\n" +
                                   "sys.stdout.buffer.write(sys.stdin.buffer.read())\n")
        command = '"' + sys.executable + '" "' + str(filter_script) + '"'
        self.git("config", "filter.fixture.clean", command)
        self.git("config", "filter.fixture.smudge", command)
        self.git("config", "filter.fixture.required", "true")
        conditional = self.root / ".tmp/worktree.config"
        for name in ("clean", "smudge"):
            self.git("config", "--file", str(conditional), "filter.conditional." + name, command)
        self.git("config", "--file", str(conditional), "filter.conditional.required", "true")
        self.git("config", "--add", "includeIf.gitdir:" +
                 (self.root / ".git/worktrees").as_posix() + "/.path", str(conditional))
        result = self.output(self.cli())
        self.assertEqual("ai_audit_complete", result["status"])
        self.assertFalse(marker.exists())
        conditional_value = subprocess.run([self.git_executable, "-C", result["snapshot"],
                                             "config", "--get",
                                             "filter.conditional.required"],
                                            capture_output=True, text=True, check=True).stdout
        self.assertEqual("true", conditional_value.strip())

    def test_tracked_symlink_is_rejected_before_materialization(self):
        blob = subprocess.run([self.git_executable, "-C", str(self.root),
                               "hash-object", "-w", "--stdin"],
                              input=b"../../.tmp/private.kt", capture_output=True, check=True).stdout
        self.git("update-index", "--add", "--cacheinfo", "120000," + blob.decode().strip() +
                 ",src/main/Link.kt")
        self.commit()
        result = self.cli("--prepare-only")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.invocations())

    def test_invalid_configuration_and_extra_cli_arguments_fail_closed(self):
        for update in ({"scopes": ["../escape"]}, {"scopes": [":(glob)**"]},
                       {"scopes": ["src", "src"]}, {"branch": "--other"},
                       {"task_timeout_seconds": True}, {"task_timeout_seconds": 7201},
                       {"run_timeout_seconds": 86401}, {"run_timeout_seconds": 120},
                       {"model": "unexpected"}):
            with self.subTest(update=update):
                self.write_config(**update)
                self.assertNotEqual(0, self.cli("--check-only").returncode)
        self.write_config()
        self.assertNotEqual(0, self.cli("--check-only", "--model", "unexpected").returncode)
        self.assertFalse(self.base.exists())

    @unittest.skipUnless(os.name == "nt" and shutil.which("powershell.exe"), "Windows installer")
    def test_scheduler_whatif_and_read_existing_do_not_register(self):
        for argument in ("-WhatIf", "-ShowExisting"):
            with self.subTest(argument=argument):
                result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive",
                                         "-ExecutionPolicy", "Bypass", "-File",
                                         str(INSTALLER), "-Config", str(self.config), "-Python",
                                         sys.executable, "-Codex", sys.executable, argument],
                                        capture_output=True, text=True, timeout=30)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertIn("Codex-MobileAudit-", result.stdout)
                if argument == "-WhatIf":
                    first_run = re.search(r"FirstRunLocal\s+:\s+(\S+)", result.stdout)
                    self.assertIsNotNone(first_run, result.stdout)
                    self.assertGreater(datetime.fromisoformat(first_run.group(1)),
                                       datetime.now().astimezone())
        self.assertFalse(self.base.exists())
        self.assertEqual([], self.invocations())


if __name__ == "__main__":
    unittest.main()
