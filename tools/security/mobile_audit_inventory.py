import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import xml.etree.ElementTree as ET
from xml.parsers import expat

from mobile_audit_git import git_environment


ANDROID = "{http://schemas.android.com/apk/res/android}"
SOURCE_TYPES = {".kt", ".java", ".aidl", ".c", ".cc", ".cpp", ".h", ".hpp", ".js", ".ts",
                ".xml", ".gradle", ".kts", ".pro"}
EXCLUDED_PARTS = {".git", ".tmp", ".gradle", ".idea", "build", "node_modules", "test",
                  "tests", "androidTest", "testFixtures", "generated", "_ts-out"}
RULES = (
    ("entry-points", "intent-input", r"onNewIntent|Intent\.ACTION_(?:VIEW|SEND)|getIntent\("),
    ("entry-points", "receiver-registration", r"registerReceiver\(|onReceive\("),
    ("entry-points", "pending-intent", r"PendingIntent\.(?:get|FLAG_)"),
    ("webview", "javascript-bridge",
     r"addJavascriptInterface|@JavascriptInterface|addWebMessageListener"),
    ("webview", "webview-navigation",
     r"shouldOverrideUrlLoading|onReceivedSslError|shouldInterceptRequest"),
    ("webview", "origin-policy",
     r"allowUniversalAccessFromFileURLs|allowFileAccessFromFileURLs|allowedOriginRules"),
    ("credentials", "credential-entry",
     r"onFillRequest|onGetCredential|CallingAppInfo|onBeginGetCredential"),
    ("credentials", "credential-release",
     r"clientDataHash|DigitalAssetLinks|CredentialSecurityNative|sendResponse"),
    ("credentials", "biometric-authentication",
     r"BiometricPrompt|onAuthenticationSucceeded|CryptoObject|SoftwareBiometric"),
    ("files-native", "file-input",
     r"openFileDescriptor|ZipInputStream|ParcelFileDescriptor|ACTION_OPEN_DOCUMENT"),
    ("files-native", "native-boundary", r"external fun |RegisterNatives|JNI_OnLoad|JNIEXPORT"),
    ("storage-network", "secret-storage",
     r"Cipher\.getInstance|SecretKeySpec\(|AndroidKeyStore|SecureDataStore"),
    ("storage-network", "transport-policy",
     r"CertificatePinner|HostnameVerifier|checkServerTrusted|usesCleartextTraffic"),
    ("storage-network", "disclosure-policy", r"FLAG_SECURE|setPrimaryClip|allowBackup|redact|sanitize.*Log"),
)


def collect_inventory(repo, scopes=None, include_untracked=False):
    repo = Path(git(Path(repo).resolve(), "rev-parse", "--show-toplevel").strip()).resolve()
    scopes = scopes or (["roboform/android"] if (repo / "roboform/android").is_dir() else ["."])
    scopes = sorted({relative_scope(repo, scope) for scope in scopes})
    tracked = git(repo, "ls-files", "-z", "--", *scopes).split("\0")
    paths = set(filter(None, tracked))
    if include_untracked:
        paths.update(filter(None, git(repo, "ls-files", "--others", "--exclude-standard",
                                      "-z", "--", *scopes).split("\0")))
    files, indicators, components, warnings, texts = [], [], [], [], {}
    checked_directories = {repo}
    excluded = 0
    for name in sorted(paths):
        path = PurePosixPath(name)
        if path.suffix.lower() not in SOURCE_TYPES or any(
                part in EXCLUDED_PARTS or part.endswith("-device-tests") for part in path.parts):
            excluded += 1
            continue
        try:
            source = safe_source(repo, name, checked_directories)
            if source.stat().st_size > 4 * 1024 * 1024:
                raise ValueError("Source exceeds the 4 MiB inventory limit")
            data = source.read_bytes()
            text = data.decode("utf-8-sig")
        except (OSError, ValueError, UnicodeError) as error:
            warnings.append({"path": name, "reason": type(error).__name__ + ": " + str(error)})
            continue
        files.append({"path": name, "sha256": hashlib.sha256(data).hexdigest(),
                      "bytes": len(data), "lines": max(1, len(text.splitlines()))})
        texts[name] = text
        if path.suffix.lower() != ".xml" or path.name == "AndroidManifest.xml":
            for task, rule, pattern in RULES:
                for index, match in enumerate(re.finditer(pattern, text)):
                    if index == 3:
                        break
                    indicators.append({"task": task, "rule": rule, "path": name,
                                       "line": text.count("\n", 0, match.start()) + 1})
    for name, text in texts.items():
        if PurePosixPath(name).name == "AndroidManifest.xml":
            try:
                components.extend(manifest_components(name, text, texts))
            except (ET.ParseError, expat.ExpatError) as error:
                warnings.append({"path": name, "reason": "Manifest parse failed: " + str(error)})
    snapshot = {"format": 1, "repo": str(repo), "branch": git(repo, "branch", "--show-current").strip(),
                "head": git(repo, "rev-parse", "HEAD").strip(), "scopes": scopes,
                "include_untracked": include_untracked, "files": files,
                "tracked_status": git(repo, "status", "--porcelain=v1", "-z",
                                      "--untracked-files=no", "--ignore-submodules=all",
                                      "--", *scopes).split("\0"),
                "components": components, "indicators": indicators, "warnings": warnings,
                "excluded_file_count": excluded,
                "manifest_kind": "source declarations, not a merged APK manifest"}
    signature = {key: snapshot[key] for key in ("head", "branch", "scopes", "include_untracked",
                                               "files", "tracked_status", "warnings")}
    snapshot["fingerprint"] = hashlib.sha256(json.dumps(
        signature, sort_keys=True, ensure_ascii=True).encode("utf-8")).hexdigest()
    return snapshot


def assert_fresh(inventory):
    fresh = collect_inventory(inventory["repo"], inventory["scopes"], inventory["include_untracked"])
    if fresh["fingerprint"] != inventory["fingerprint"]:
        raise ValueError("Source snapshot changed. Prepare a new audit directory.")


def safe_source(repo, name, checked_directories=None):
    relative = PurePosixPath(name)
    if relative.is_absolute() or ".." in relative.parts or "\\" in name or ":" in name:
        raise ValueError("Source path must stay inside the checkout")
    path = repo.joinpath(*relative.parts)
    checked = checked_directories if checked_directories is not None else {repo}
    current = path
    verified = []
    while current not in checked:
        information = current.lstat()
        if stat.S_ISLNK(information.st_mode) or getattr(information, "st_file_attributes", 0) & 0x400:
            raise ValueError("Source symlinks and reparse points are outside the audit inventory")
        if current != path:
            verified.append(current)
        current = current.parent
    checked.update(verified)
    if not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("Source file is missing")
    return path


def relative_scope(repo, scope):
    path = Path(scope)
    if ".." in path.parts or path.is_absolute():
        raise ValueError("Scope must be a relative checkout path")
    resolved = (repo / path).resolve()
    if not resolved.is_relative_to(repo) or not resolved.exists():
        raise ValueError("Scope is missing or outside the checkout")
    return resolved.relative_to(repo).as_posix()


def manifest_components(name, text, texts):
    tree = ET.fromstring(text)
    application = tree.find("application")
    if application is None:
        return []
    namespace = tree.get("package", "")
    if not namespace:
        for parent in PurePosixPath(name).parents:
            build = next((texts.get((parent / filename).as_posix())
                          for filename in ("build.gradle", "build.gradle.kts")
                          if (parent / filename).as_posix() in texts), "")
            match = re.search(r"\bnamespace\s*(?:=\s*)?['\"]([^'\"]+)['\"]", build)
            if match:
                namespace = match.group(1)
                break
    protections = {item.get(ANDROID + "name"): item.get(ANDROID + "protectionLevel", "normal")
                   for item in tree.findall("permission")}
    positions = {}
    parser = expat.ParserCreate(namespace_separator="}")

    def locate(tag, attributes):
        key = (tag, attributes.get(ANDROID[1:] + "name", ""))
        positions.setdefault(key, []).append(parser.CurrentLineNumber)

    parser.StartElementHandler = locate
    parser.Parse(text, True)
    result = []
    for element in application:
        if element.tag not in {"activity", "activity-alias", "service", "receiver", "provider"}:
            continue
        short_name = element.get(ANDROID + "name", "")
        inherited_permission = ("" if element.tag == "activity-alias" else
                                application.get(ANDROID + "permission", ""))
        permission = element.get(ANDROID + "permission", inherited_permission)
        filters = [{"actions": [item.get(ANDROID + "name", "") for item in node.findall("action")],
                    "categories": [item.get(ANDROID + "name", "") for item in node.findall("category")],
                    "data": [{key.removeprefix(ANDROID): value for key, value in item.attrib.items()}
                             for item in node.findall("data")]}
                   for node in element.findall("intent-filter")]
        exported = element.get(ANDROID + "exported")
        if exported is None:
            exported = "inferred-filter" if filters and element.tag != "provider" else "unspecified"
        implementation = element.get(ANDROID + "targetActivity", short_name)
        if namespace and implementation and "${" not in implementation:
            implementation = (namespace + implementation if implementation.startswith(".") else
                              namespace + "." + implementation if "." not in implementation else
                              implementation)
        matches = positions.get((element.tag, short_name), [1])
        result.append({"manifest": name, "line": matches.pop(0), "kind": element.tag,
                       "name": short_name, "implementation": implementation,
                       "namespace": namespace, "exported": exported,
                       "enabled": element.get(ANDROID + "enabled",
                                              application.get(ANDROID + "enabled", "true")),
                       "application_enabled": application.get(ANDROID + "enabled", "true"),
                       "permission": permission,
                       "read_permission": element.get(ANDROID + "readPermission", permission),
                       "write_permission": element.get(ANDROID + "writePermission", permission),
                       "grant_uri_permissions": element.get(ANDROID + "grantUriPermissions", "false"),
                       "authorities": element.get(ANDROID + "authorities", ""),
                       "permission_protection": protections.get(
                           permission, "external-or-unknown" if permission else "none"),
                       "process": element.get(ANDROID + "process", application.get(ANDROID + "process", "")),
                       "tools_node": element.get("{http://schemas.android.com/tools}node", ""),
                       "filters": filters})
    return result


def git(repo, *arguments):
    executable = str(resolve_git(repo))
    result = subprocess.run([executable, "--no-replace-objects", "-c", "safe.directory=" + str(repo),
                             "--work-tree=" + str(repo), "-C", str(repo), *arguments], capture_output=True,
                            encoding="utf-8", errors="replace", timeout=60,
                            env=git_environment(repo, executable))
    if result.returncode:
        raise ValueError("Git failed for " + arguments[0] + ". Check checkout permissions.")
    return result.stdout


def checkout_root(path):
    path = Path(path).resolve()
    return next((parent for parent in (path, *path.parents) if (parent / ".git").exists()), path)


def resolve_installed_tool(name, repo):
    repo = Path(repo).resolve()
    roots = [parent for parent in (repo, *repo.parents) if (parent / ".git").exists()]
    roots = roots or [repo]
    for entry in os.get_exec_path():
        directory = Path(entry.strip('"'))
        if not directory.is_absolute():
            continue
        candidate = (directory / name).resolve()
        if any(candidate.is_relative_to(root) for root in roots):
            continue
        if candidate.is_file() and os.access(candidate, os.X_OK):
            return candidate
    raise ValueError("Select an installed " + name + " on an absolute PATH outside the checkout.")


def resolve_git(repo):
    return resolve_installed_tool("git.exe" if os.name == "nt" else "git", repo)
