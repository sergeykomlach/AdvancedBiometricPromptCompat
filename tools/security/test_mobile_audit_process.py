import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import mobile_audit
import mobile_audit_auto


SCRATCH = next(parent for parent in Path(__file__).resolve().parents
               if (parent / ".git").exists()) / ".tmp"


class MobileAuditProcessTest(unittest.TestCase):
    def setUp(self):
        SCRATCH.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="mobile-audit-process-test-", dir=SCRATCH)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)

    def test_stage_cancellation_allows_runner_to_clean_its_child(self):
        marker = self.root / "child-cleaned"
        script = ("import pathlib, sys\n"
                  "if sys.stdin.readline().strip() == 'cancel':\n"
                  "    pathlib.Path(sys.argv[1]).write_text('cleaned')\n")
        process = subprocess.Popen([sys.executable, "-B", "-c", script, str(marker)],
                                   stdin=subprocess.PIPE)

        def kill_group(pid, signum):
            self.assertEqual(process.pid, pid)
            if signum == signal.SIGTERM:
                process.stdin.write(b"cancel\n")
                process.stdin.flush()
            else:
                process.kill()

        # Windows has no killpg. Replace only that OS boundary; keep a real runner and wait.
        operating_system = SimpleNamespace(name="posix", killpg=kill_group)
        signals = SimpleNamespace(SIGTERM=signal.SIGTERM, SIGKILL=9)
        try:
            with patch.object(mobile_audit_auto, "os", operating_system), \
                    patch.object(mobile_audit_auto, "signal", signals):
                mobile_audit_auto.stop_process_tree(process)
            self.assertTrue(marker.is_file(), "The runner was killed before child cleanup")
            self.assertEqual(0, process.returncode)
        finally:
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
            process.stdin.close()

    def test_interrupt_during_engine_spawn_still_reaps_engine(self):
        children = []
        spawn = subprocess.Popen
        previous_handler = signal.signal(signal.SIGTERM, signal.default_int_handler)

        def spawn_then_interrupt(*arguments, **keywords):
            process = spawn(*arguments, **keywords)
            children.append(process)
            signal.getsignal(signal.SIGTERM)(signal.SIGTERM, None)
            return process

        def kill_group(pid, _signum):
            next(process for process in children if process.pid == pid).kill()

        operating_system = SimpleNamespace(name="posix", killpg=kill_group)
        signals = SimpleNamespace(SIGTERM=signal.SIGTERM, SIGINT=signal.SIGINT, SIGKILL=9,
                                  signal=signal.signal)
        error = None
        try:
            with patch.object(mobile_audit, "os", operating_system), \
                    patch.object(mobile_audit, "signal", signals), \
                    patch.object(mobile_audit.subprocess, "Popen", spawn_then_interrupt):
                try:
                    mobile_audit.invoke_engine(
                        [sys.executable, "-B", "-c", "import time; time.sleep(30)"],
                        "fixture", 10, self.root)
                except BaseException as caught:
                    error = caught
            self.assertIsInstance(error, ValueError)
            self.assertEqual(1, len(children))
            self.assertIsNotNone(children[0].poll(), "The interrupted spawn leaked its engine")
            self.assertIs(signal.default_int_handler, signal.getsignal(signal.SIGTERM))
        finally:
            signal.signal(signal.SIGTERM, previous_handler)
            for process in children:
                if process.poll() is None:
                    process.kill()
                process.communicate(timeout=5)

    @unittest.skipIf(os.name == "nt", "POSIX process groups and signals")
    def test_outer_deadline_reaps_nested_engine_and_its_child(self):
        self.check_nested_cancellation("timeout")

    @unittest.skipIf(os.name == "nt", "POSIX process groups and signals")
    def test_outer_interrupt_reaps_nested_engine_with_inherited_sigint_ignore(self):
        self.check_nested_cancellation("interrupt")

    def check_nested_cancellation(self, mode):
        engine = self.root / "engine.py"
        pids = self.root / "pids.json"
        engine.write_text(
            "import json, os, pathlib, subprocess, sys, time\n"
            "child = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])\n"
            "target = pathlib.Path(sys.argv[1])\n"
            "pending = target.with_suffix('.pending')\n"
            "pending.write_text(json.dumps([os.getpid(), child.pid]))\n"
            "pending.replace(target)\n"
            "time.sleep(60)\n")
        runner = self.root / "runner.py"
        runner.write_text(
            "import pathlib, signal, sys\n"
            "sys.path.insert(0, sys.argv[1])\n"
            "import mobile_audit\n"
            "signal.signal(signal.SIGINT, signal.SIG_IGN)\n"
            "mobile_audit.invoke_engine([sys.executable, '-B', sys.argv[2], sys.argv[3]], '', 60, "
            "pathlib.Path.cwd())\n")
        wrapper = self.root / "stage.py"
        wrapper.write_text(
            "import pathlib, sys, time\n"
            "sys.path.insert(0, sys.argv[1])\n"
            "import mobile_audit_auto as audit\n"
            "root = pathlib.Path.cwd()\n"
            "audit.stage('fixture', [sys.executable, '-B', sys.argv[2], *sys.argv[1:2], "
            "*sys.argv[3:5]], root, dict(audit.os.environ), time.monotonic() + "
            "(10 if sys.argv[5] == 'timeout' else 60), root, {'stages': []})\n")
        process = subprocess.Popen(
            [sys.executable, "-B", str(wrapper), str(Path(mobile_audit.__file__).parent),
             str(runner), str(engine), str(pids), mode], cwd=self.root,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        engine_pids = []
        try:
            deadline = time.monotonic() + 8
            while not pids.exists() and process.poll() is None and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(pids.exists(), "Nested engine did not become ready")
            engine_pids = json.loads(pids.read_text())
            if mode == "interrupt":
                os.kill(process.pid, signal.SIGINT)
            process.wait(timeout=20)
            self.assertNotEqual(0, process.returncode)
            for pid in engine_pids:
                deadline = time.monotonic() + 5
                while process_running(pid) and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertFalse(process_running(pid), "Cancellation leaked PID " + str(pid))
        finally:
            for pid in engine_pids:
                try:
                    os.kill(pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=5)


def process_running(pid):
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    # A killed grandchild may remain a zombie briefly until the host's init reaps it.
    if Path("/proc").is_dir():
        try:
            state = (Path("/proc") / str(pid) / "stat").read_text()
        except FileNotFoundError:
            return False
        if state.split(") ", 1)[1].startswith("Z "):
            return False
    return True


if __name__ == "__main__":
    unittest.main()
