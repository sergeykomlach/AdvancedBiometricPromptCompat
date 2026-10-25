"""Isolate read-only audit Git commands from caller and checkout execution settings."""

import os
from pathlib import Path
import stat
import subprocess


GIT_OPTIONS = {"core.hooksPath": os.devnull, "core.fsmonitor": "false",
               "core.attributesFile": os.devnull, "core.autocrlf": "false",
               "core.safecrlf": "false", "core.symlinks": "false", "core.longpaths": "true",
               "maintenance.auto": "false", "gc.auto": "0"}


def safe_path(path, missing=False):
    path = Path(os.path.abspath(path))
    for candidate in reversed([path, *path.parents]):
        try:
            information = candidate.lstat()
        except FileNotFoundError:
            if missing:
                continue
            raise
        if stat.S_ISLNK(information.st_mode) or getattr(information, "st_file_attributes", 0) & 0x400:
            raise ValueError("Audit paths must not traverse symlinks or reparse points.")
    return path


def checkout_roots(root):
    return [parent for parent in (root, *root.parents) if (parent / ".git").exists()] or [root]


def git_environment(root=None, git_executable=None):
    environment = {key: value for key, value in os.environ.items()
                   if not key.upper().startswith("GIT_")}
    environment.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                       GIT_TERMINAL_PROMPT="0", GIT_OPTIONAL_LOCKS="0", GIT_NO_REPLACE_OBJECTS="1",
                       PYTHONDONTWRITEBYTECODE="1", NoDefaultCurrentDirectoryInExePath="1")
    options = dict(GIT_OPTIONS)
    if root:
        checkouts = checkout_roots(root)
        directories = [str(Path(git_executable).parent)]
        for entry in os.environ.get("PATH", "").split(os.pathsep):
            directory = Path(entry.strip('"'))
            if not entry or not directory.is_absolute():
                continue
            try:
                directory = safe_path(directory)
            except (OSError, ValueError):
                continue
            if directory.is_dir() and not any(directory.is_relative_to(checkout)
                                              for checkout in checkouts):
                directories.append(str(directory))
        environment["PATH"] = os.pathsep.join(dict.fromkeys(directories))
        result = subprocess.run([git_executable, "-c", "safe.directory=" + str(root),
                                 "-C", str(root), "config", "--name-only", "-z",
                                 "--get-regexp", r"^filter\..*\.(clean|smudge|process|required)$"],
                                env=environment, capture_output=True, timeout=30)
        if result.returncode not in (0, 1):
            raise ValueError("Could not inspect local Git filter configuration.")
        for key in result.stdout.decode("utf-8").split("\0"):
            if key:
                options[key] = "false" if key.endswith(".required") else ""
    environment["GIT_CONFIG_COUNT"] = str(len(options))
    for index, (key, value) in enumerate(options.items()):
        environment["GIT_CONFIG_KEY_" + str(index)] = key
        environment["GIT_CONFIG_VALUE_" + str(index)] = value
    return environment
