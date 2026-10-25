import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import signal
import subprocess
import sys
import time
import uuid

from mobile_audit import TASKS, load_run
from mobile_audit_inventory import resolve_git
from mobile_audit_git import checkout_roots, git_environment, safe_path


CONFIG_KEYS = {"format", "branch", "scopes", "task_timeout_seconds", "run_timeout_seconds"}
SCRATCH = Path(".tmp/mobile-audit-auto")


def main():
    parser = argparse.ArgumentParser(description="Audit a pinned local branch with installed Codex.")
    parser.add_argument("--config", default=str(Path(__file__).with_name("mobile-audit.config.json")))
    parser.add_argument("--codex", default="codex.exe" if os.name == "nt" else "codex")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check-only", action="store_true")
    modes.add_argument("--prepare-only", action="store_true")
    arguments = parser.parse_args()
    try:
        return autorun(arguments)
    except (OSError, ValueError, subprocess.SubprocessError, json.JSONDecodeError) as error:
        emit({"status": "failed", "error": str(error)})
        return 1
    except KeyboardInterrupt:
        emit({"status": "interrupted"})
        return 130


def autorun(arguments):
    config_path = safe_path(Path(arguments.config).absolute())
    config = read_config(config_path)
    root = next((parent for parent in (config_path.parent, *config_path.parents)
                 if (parent / ".git").exists()), None)
    if root is None:
        raise ValueError("Keep the audit configuration inside its Git checkout.")
    root = safe_path(root)
    git_executable = str(resolve_git(root))
    environment = git_environment()
    discovered = git(git_executable, root, environment, "rev-parse", "--show-toplevel",
                     worktree=False).decode().strip()
    if Path(discovered) != root:
        raise ValueError("The audit configuration does not belong to the selected checkout.")
    if not config_path.is_relative_to(root):
        raise ValueError("Keep the audit configuration inside its Git checkout.")
    environment = git_environment(root, git_executable)
    branch = config["branch"]
    git(git_executable, root, environment, "check-ref-format", "refs/heads/" + branch)
    sha = git(git_executable, root, environment, "rev-parse", "--verify",
              "refs/heads/" + branch + "^{commit}").decode().strip()
    engine = resolve_engine(arguments.codex, root)
    base = safe_path(root / SCRATCH, missing=True)
    plan = {"status": "checked", "source_branch": branch, "source_sha": sha,
            "repo": str(root), "scopes": config["scopes"], "config": str(config_path),
            "codex": str(engine), "reports": str(base),
            "task_timeout_seconds": config["task_timeout_seconds"],
            "run_timeout_seconds": config["run_timeout_seconds"]}
    if arguments.check_only:
        selected_files(git_executable, root, environment, sha, config["scopes"])
        emit(plan)
        return 0
    base.mkdir(parents=True, exist_ok=True)
    with owner_lock(base / "autorun.lock") as owned:
        if not owned:
            emit({**plan, "status": "already_running"})
            return 0
        previous = previous_success(base, sha, config["scopes"], environment)
        if previous and not arguments.prepare_only:
            emit({**plan, "status": "already_audited", "report": str(previous / "report.md")})
            return 0
        return audit_snapshot(arguments, plan, config, root, base, engine,
                              git_executable, environment)


def audit_snapshot(arguments, plan, config, root, base, engine, git_executable, environment):
    deadline = time.monotonic() + config["run_timeout_seconds"]
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ-") + uuid.uuid4().hex[:12]
    run = safe_path(base / "runs" / run_id, missing=True)
    run.mkdir(parents=True, exist_ok=False)
    snapshot = run / "snapshot"
    report_dir = snapshot / ".tmp/mobile-audit"
    provenance = {**plan, "format": 1, "status": "preparing", "run_id": run_id,
                  "started_at": datetime.now(timezone.utc).isoformat(),
                  "snapshot": str(snapshot), "report": str(report_dir / "report.md"),
                  "python": sys.executable, "audit_tool": str(Path(__file__).with_name(
                      "mobile_audit.py").resolve()), "stages": []}
    write_json(run / "provenance.json", provenance)
    try:
        entries = selected_files(git_executable, root, environment, plan["source_sha"],
                                 config["scopes"])
        git(git_executable, root, environment, "worktree", "add", "--detach", "--no-checkout",
            str(snapshot), plan["source_sha"])
        safe_path(snapshot)
        environment = git_environment(snapshot, git_executable)
        git(git_executable, snapshot, environment, "read-tree", plan["source_sha"])
        files = materialize(git_executable, root, snapshot, environment, entries, deadline)
        write_json(run / "snapshot-files.json", files)
        if git(git_executable, snapshot, environment, "rev-parse", "HEAD").decode().strip() != \
                plan["source_sha"]:
            raise ValueError("The detached snapshot does not match the pinned commit.")
        audit_tool = Path(__file__).with_name("mobile_audit.py").resolve()
        command = [sys.executable, "-E", "-s", "-B", str(audit_tool)]
        prepare = [*command, "prepare", "--repo", str(snapshot), "--output", ".tmp/mobile-audit"]
        for scope in config["scopes"]:
            prepare.extend(["--scope", scope])
        stage("prepare", prepare, root, environment, deadline, run, provenance)
        if arguments.prepare_only:
            verify_snapshot(snapshot, files)
            provenance["status"] = "prepared"
        else:
            stage("audit", [*command, "run", "--report-dir", str(report_dir),
                            "--codex", str(engine), "--max-tasks", str(len(TASKS)),
                            "--timeout", str(config["task_timeout_seconds"])],
                  root, environment, deadline, run, provenance)
            stage("report", [*command, "report", "--report-dir", str(report_dir)],
                  root, environment, deadline, run, provenance)
            verify_snapshot(snapshot, files)
            verify_complete(report_dir, plan["source_sha"], config["scopes"], environment)
            provenance["status"] = "ai_audit_complete"
            write_json(base / "success.json", {"format": 1, "source_sha": plan["source_sha"],
                       "source_branch": plan["source_branch"], "scopes": config["scopes"],
                       "run_id": run_id})
        provenance["finished_at"] = datetime.now(timezone.utc).isoformat()
        write_json(run / "provenance.json", provenance)
        emit(provenance)
        return 0
    except (OSError, ValueError, subprocess.SubprocessError, KeyboardInterrupt) as error:
        provenance.update(status="interrupted" if isinstance(error, KeyboardInterrupt) else "failed",
                          error=str(error), finished_at=datetime.now(timezone.utc).isoformat())
        write_json(run / "provenance.json", provenance)
        emit(provenance)
        return 130 if isinstance(error, KeyboardInterrupt) else 1


def read_config(path):
    if not path.is_file() or path.stat().st_size > 16384:
        raise ValueError("Select an existing audit configuration smaller than 16 KiB.")
    config = json.loads(path.read_text(encoding="utf-8-sig"))
    if not isinstance(config, dict) or set(config) != CONFIG_KEYS or \
            type(config["format"]) is not int or config["format"] != 1:
        raise ValueError("The audit configuration has unknown or missing fields.")
    if not isinstance(config["branch"], str) or not re.fullmatch(
            r"[A-Za-z0-9][A-Za-z0-9._/-]{0,199}", config["branch"]):
        raise ValueError("Select an exact local branch name.")
    scopes = config["scopes"]
    if not isinstance(scopes, list) or not 1 <= len(scopes) <= 16:
        raise ValueError("Configure between one and sixteen source scopes.")
    for scope in scopes:
        if not isinstance(scope, str) or (scope != "." and not valid_name(scope)):
            raise ValueError("Scopes must be literal relative checkout paths.")
    if len(set(scopes)) != len(scopes):
        raise ValueError("Configure each source scope once.")
    config["scopes"] = sorted(scopes)
    task_timeout = config["task_timeout_seconds"]
    run_timeout = config["run_timeout_seconds"]
    if type(task_timeout) is not int or not 1 <= task_timeout <= 7200:
        raise ValueError("Set task_timeout_seconds between 1 and 7200.")
    if type(run_timeout) is not int or not 120 <= run_timeout <= 86400 or \
            run_timeout < len(TASKS) * task_timeout + 120:
        raise ValueError("Allow all six task limits plus 120 seconds within a one-day run limit.")
    return config


def valid_name(name):
    parts = name.split("/")
    reserved = {"con", "prn", "aux", "nul", *["com" + str(n) for n in range(1, 10)],
                *["lpt" + str(n) for n in range(1, 10)]}
    return bool(name) and len(name) <= 1024 and all(
        part not in ("", ".", "..") and part.lower() not in (".git", ".tmp") and
        not part.endswith((".", " ")) and part.split(".")[0].lower() not in reserved and
        not any(ord(char) < 32 or char in '\\:*?"<>|\x7f' for char in part) for part in parts)


def resolve_engine(value, root):
    root = safe_path(root)
    requested = Path(value)
    if requested.is_absolute():
        path = safe_path(requested)
        if path.is_file() and path.suffix.lower() == ".py" and path.is_relative_to(root / ".tmp"):
            return path
        if installed_engine(path, root):
            return path
    elif requested.name == value and value not in ("", ".", ".."):
        if os.name == "nt":
            names = [value] if requested.suffix.lower() == ".exe" else [value + ".exe"] \
                if not requested.suffix else []
        else:
            names = [value]
        for entry in os.environ.get("PATH", "").split(os.pathsep):
            directory = Path(entry.strip('"'))
            if not entry or not directory.is_absolute():
                continue
            for name in names:
                try:
                    path = safe_path(directory / name)
                except (OSError, ValueError):
                    continue
                if installed_engine(path, root):
                    return path
    raise ValueError("Select an installed Codex executable outside the checkout. "
                     "Fixture scripts require an explicit absolute .tmp/*.py path.")


def installed_engine(path, root):
    return path.is_absolute() and path.is_file() and not any(
        path.is_relative_to(checkout) for checkout in checkout_roots(root)) and (
        path.suffix.lower() == ".exe" if os.name == "nt" else
        path.suffix.lower() not in (".py", ".cmd", ".bat") and os.access(path, os.X_OK))


def git(git_executable, root, environment, *arguments, worktree=True):
    command = [git_executable, "--literal-pathspecs", "-c",
               "safe.directory=" + str(root), "-C", str(root)]
    if worktree:
        command.append("--work-tree=" + str(root))
    result = subprocess.run([*command, *arguments],
                            env=environment, capture_output=True, timeout=60)
    if result.returncode:
        raise ValueError("Git stopped during " + arguments[0] + " with status " +
                         str(result.returncode) + ".")
    return result.stdout


def selected_files(git_executable, root, environment, sha, scopes):
    records = git(git_executable, root, environment, "ls-tree", "-r", "-z", "--full-tree",
                  sha, "--", *scopes)
    entries = []
    for record in filter(None, records.split(b"\0")):
        metadata, path = record.split(b"\t", 1)
        mode, kind, oid = metadata.decode("ascii").split()
        name = path.decode("utf-8")
        if not valid_name(name) or mode not in ("100644", "100755") or kind != "blob":
            raise ValueError("The selected snapshot contains an unsafe path, symlink or submodule.")
        entries.append((name, oid, mode))
    if not entries:
        raise ValueError("The pinned commit has no files in the configured scopes.")
    if any(not any(scope == "." or name == scope or name.startswith(scope + "/")
                    for name, _, _ in entries) for scope in scopes):
        raise ValueError("Every configured scope must contain tracked files in the pinned commit.")
    return entries


def materialize(git_executable, root, snapshot, environment, entries, deadline):
    options = process_options()
    process = subprocess.Popen([git_executable, "-C", str(root), "cat-file", "--batch"],
                               env=environment, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, **options)
    files = []
    try:
        for name, oid, mode in entries:
            if time.monotonic() >= deadline:
                raise ValueError("The snapshot exceeded the configured run limit.")
            process.stdin.write((oid + "\n").encode("ascii"))
            process.stdin.flush()
            header = process.stdout.readline().decode("ascii").strip().split()
            if len(header) != 3 or header[:2] != [oid, "blob"]:
                raise ValueError("Git did not return the pinned source blob.")
            size = int(header[2])
            if not 0 <= size <= 512 * 1024 * 1024:
                raise ValueError("The snapshot contains a blob larger than 512 MiB.")
            destination = safe_path(snapshot.joinpath(*PurePosixPath(name).parts), missing=True)
            destination.parent.mkdir(parents=True, exist_ok=True)
            digest = hashlib.sha256()
            remaining = size
            with destination.open("xb") as stream:
                while remaining:
                    data = process.stdout.read(min(1024 * 1024, remaining))
                    if not data:
                        raise ValueError("Git returned an incomplete source blob.")
                    stream.write(data)
                    digest.update(data)
                    remaining -= len(data)
            if process.stdout.read(1) != b"\n":
                raise ValueError("Git returned an invalid source blob boundary.")
            destination.chmod(0o555 if mode == "100755" else 0o444)
            files.append({"path": name, "sha256": digest.hexdigest(), "bytes": size, "mode": mode})
        process.stdin.close()
        process.wait(timeout=15)
        if process.returncode:
            raise ValueError("Git stopped before completing the source snapshot.")
        return files
    finally:
        if process.poll() is None:
            stop_process_tree(process)
        process.stdout.close()


def verify_snapshot(snapshot, files):
    if not isinstance(files, list) or not files:
        raise ValueError("The pinned snapshot manifest is missing its source files.")
    for item in files:
        if not isinstance(item, dict) or not isinstance(item.get("path"), str) or \
                not valid_name(item["path"]) or item.get("mode") not in ("100644", "100755"):
            raise ValueError("The pinned snapshot manifest contains an unsafe source path.")
        path = safe_path(snapshot / item["path"])
        if not path.is_file() or path.stat().st_size != item["bytes"]:
            raise ValueError("The pinned source snapshot changed during the audit.")
        if os.name != "nt" and path.stat().st_mode & 0o111 != (
                0o111 if item["mode"] == "100755" else 0):
            raise ValueError("The pinned source snapshot executable mode changed during the audit.")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for data in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(data)
        if digest.hexdigest() != item["sha256"]:
            raise ValueError("The pinned source snapshot changed during the audit.")


def stage(name, command, root, environment, deadline, run, provenance):
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise ValueError("The audit exceeded the configured run limit.")
    process = subprocess.Popen(command, cwd=root, env=environment, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, **process_options())
    try:
        output, _ = process.communicate(timeout=remaining)
    except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        stop_process_tree(process)
        output, _ = process.communicate()
        (run / (name + ".log")).write_bytes(output)
        if isinstance(error, KeyboardInterrupt):
            raise
        raise ValueError("The audit stage was interrupted or exceeded the configured run limit.")
    (run / (name + ".log")).write_bytes(output)
    provenance["stages"].append({"stage": name, "exit_code": process.returncode})
    write_json(run / "provenance.json", provenance)
    if process.returncode:
        raise ValueError("The " + name + " stage stopped with status " + str(process.returncode) +
                         ". Inspect its retained log and report.")


def process_options():
    return {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.CREATE_NO_WINDOW} \
        if os.name == "nt" else {"start_new_session": True}


def stop_process_tree(process):
    if os.name == "nt":
        taskkill = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32/taskkill.exe"
        subprocess.run([str(taskkill), "/PID", str(process.pid), "/T", "/F"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15,
                       creationflags=subprocess.CREATE_NO_WINDOW)
    else:
        # Let the Python runner reap its separately grouped Codex before killing the runner.
        # SIGTERM is handled explicitly by invoke_engine, even if SIGINT was inherited ignored.
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    if process.poll() is None:
        process.kill()
    process.wait(timeout=15)


def verify_complete(directory, sha, scopes, environment):
    safe_path(directory)
    directory, inventory, ledger = load_run(directory)
    if inventory.get("head") != sha or inventory.get("scopes") != scopes or \
            inventory.get("include_untracked") is not False or inventory.get("warnings") or \
            inventory.get("tracked_status") != [""] or \
            ledger.get("stale") is not False or any(
                entry.get("state") != "complete" or entry.get("result", {}).get("completed") is not True
                for entry in ledger["tasks"].values()):
        raise ValueError("All five surfaces and triage must complete against the pinned snapshot.")
    repo = safe_path(Path(inventory["repo"]))
    git_executable = str(resolve_git(repo))
    environment = git_environment(repo, git_executable)
    current_head = git(git_executable, repo, environment, "rev-parse", "HEAD").decode().strip()
    status = git(git_executable, repo, environment, "status", "--porcelain=v1", "-z",
                 "--untracked-files=no", "--ignore-submodules=all", "--", *scopes)
    if current_head != sha or status.decode("utf-8").split("\0") != inventory["tracked_status"]:
        raise ValueError("The retained snapshot no longer matches its pinned Git state.")
    report = read_json(directory / "report.json")
    tasks = report.get("tasks", [])
    if report.get("state") != "ai_audit_complete" or report.get("head") != sha or \
            report.get("fingerprint") != inventory["fingerprint"] or len(tasks) != len(TASKS) or \
            {item.get("id") for item in tasks} != set(TASKS) or any(
                item.get("state") != "complete" for item in tasks):
        raise ValueError("The retained report does not prove a complete audit.")
    for name, entry in ledger["tasks"].items():
        if read_json(directory / "responses" / (name + ".json")) != entry["result"]:
            raise ValueError("The retained response does not match its completed task.")


def previous_success(base, sha, scopes, environment):
    try:
        success = read_json(base / "success.json")
        run_id = success.get("run_id", "")
        if success.get("format") != 1 or success.get("source_sha") != sha or \
                success.get("scopes") != scopes or not isinstance(run_id, str) or not re.fullmatch(
                    r"\d{8}T\d{12}Z-[a-f0-9]{12}", run_id):
            return None
        run = safe_path(base / "runs" / run_id)
        provenance = read_json(run / "provenance.json")
        if provenance.get("status") != "ai_audit_complete" or provenance.get("source_sha") != sha:
            return None
        directory = run / "snapshot/.tmp/mobile-audit"
        verify_snapshot(run / "snapshot", read_json(run / "snapshot-files.json"))
        verify_complete(directory, sha, scopes, environment)
        return directory
    except (OSError, ValueError, KeyError, TypeError, AttributeError, subprocess.SubprocessError):
        return None


@contextmanager
def owner_lock(path):
    safe_path(path, missing=True)
    with path.open("a+b") as stream:
        if not os.fstat(stream.fileno()).st_size:
            stream.write(b" ")
            stream.flush()
        stream.seek(0)
        owned = False
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            owned = True
        except OSError:
            pass
        try:
            yield owned
        finally:
            if owned:
                stream.seek(0)
                if os.name == "nt":
                    msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def read_json(path):
    safe_path(path)
    if not path.is_file() or path.stat().st_size > 64 * 1024 * 1024:
        raise ValueError("The retained audit metadata is missing or exceeds 64 MiB.")
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path, value):
    safe_path(path, missing=True)
    pending = safe_path(path.with_name(path.name + ".pending"), missing=True)
    with pending.open("w", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, ensure_ascii=True)
        stream.write("\n")
    pending.replace(path)


def emit(value):
    print(json.dumps(value, ensure_ascii=True))


if __name__ == "__main__":
    sys.exit(main())
