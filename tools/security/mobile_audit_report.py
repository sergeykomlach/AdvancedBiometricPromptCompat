from contextlib import closing
import json
import re
import sqlite3


def result_schema():
    text = {"type": "string", "minLength": 1}
    strings = {"type": "array", "items": text}
    evidence = array_of(object_of({"path": text, "line": {"type": "integer", "minimum": 1},
                                   "reason": text}))
    finding = object_of({"title": text, "severity": {"type": "string", "enum": [
        "informational", "low", "medium", "high", "critical"]}, "entry_point": text,
        "attacker_control": text, "trace": text, "impact": text, "prerequisites": strings,
        "mitigations": strings, "evidence": {**evidence, "minItems": 1},
        "verification": text, "recommended_fix": text})
    review = object_of({"finding_id": text, "verdict": {"type": "string", "enum": [
        "supported", "rejected", "unverified"]}, "reason": text, "evidence": evidence})
    return object_of({"completed": {"type": "boolean"}, "summary": text,
                      "inspected_files": strings, "findings": array_of(finding),
                      "reviews": array_of(review), "limitations": strings})


class SourceReferenceError(ValueError):
    def __init__(self, message, path, line=None):
        self.path = path
        self.line = line
        reference = json.dumps(path, ensure_ascii=True)
        if line is not None:
            reference += ":" + str(line)
        super().__init__(message + ": " + reference)


def validate_result(result, inventory, task, candidate_ids=()):
    validate_schema(result, result_schema())
    files = {item["path"]: item for item in inventory["files"]}
    if result["completed"] and not result["inspected_files"]:
        raise ValueError("A completed task must identify inspected source files")
    for path in result["inspected_files"]:
        if path not in files:
            raise SourceReferenceError("Inspection cites a file outside the source snapshot", path)
    for item in result["findings"] + result["reviews"]:
        for evidence in item["evidence"]:
            file = files.get(evidence["path"])
            if file is None or evidence["line"] > file["lines"]:
                raise SourceReferenceError(
                    "Evidence cites a missing file or line outside the source snapshot",
                    evidence["path"], evidence["line"])
    if task == "triage":
        reviews = result["reviews"]
        ids = [item["finding_id"] for item in reviews]
        if result["findings"] or len(ids) != len(set(ids)) or set(ids) - set(candidate_ids):
            raise ValueError("Triage must review existing candidate IDs without adding findings")
        if result["completed"] and set(ids) != set(candidate_ids):
            raise ValueError("Triage left candidates unreviewed")
        if any(item["verdict"] == "supported" and not item["evidence"] for item in reviews):
            raise ValueError("Supported candidates need source evidence")
    elif result["reviews"]:
        raise ValueError("Surface tasks cannot issue triage verdicts")


def write_reports(directory, inventory, ledger, task_ids):
    findings = []
    tasks = []
    reviews = {}
    for task in task_ids:
        entry = ledger["tasks"][task]
        result = entry.get("result", {})
        tasks.append({"id": task, "state": entry["state"], "summary": result.get("summary", ""),
                      "error": entry.get("error", ""), "usage": entry.get("usage", {}),
                      "source_reference": entry.get("source_reference"),
                      "rejected_response": entry.get("rejected_response", ""),
                      "retention_error": entry.get("retention_error", ""),
                      "limitations": result.get("limitations", [])})
        for index, finding in enumerate(result.get("findings", []), 1):
            findings.append({**finding, "id": task + ":" + str(index), "task": task,
                             "human_verified": False, "has_vulnerability": None})
        reviews.update({item["finding_id"]: item for item in result.get("reviews", [])})
    for finding in findings:
        finding["ai_review"] = reviews.get(finding["id"], {"verdict": "pending"})
    states = [item["state"] for item in tasks]
    state = ("stale" if ledger.get("stale") else
             "ai_audit_complete" if all(item == "complete" for item in states) else
             "partial" if any(item != "pending" for item in states) else "prepared")
    report = {"format": 1, "state": state, "human_verified": False,
              "branch": inventory["branch"], "head": inventory["head"],
              "fingerprint": inventory["fingerprint"], "scopes": inventory["scopes"],
              "source_file_count": len(inventory["files"]),
              "components": inventory["components"], "warnings": inventory["warnings"],
              "tasks": tasks, "findings": findings,
              "limitations": ["Source review does not prove runtime behavior or absence of vulnerabilities.",
                              "Source manifests are not merged release manifests.",
                              "Pattern matches seed investigation and are not vulnerability findings.",
                              "Model candidates and triage verdicts require human review.",
                              "Only inventory paths are covered. Shared dependencies outside the scopes "
                              "need another run.",
                              "Read-only protects source writes. The CLI/model still reads scoped "
                              "proprietary source."]}
    write_json(directory / "report.json", report)
    write_text(directory / "report.md", render_markdown(report))
    database = directory / "audit.sqlite3.pending"
    if database.exists():
        database.unlink()
    with closing(sqlite3.connect(database)) as db:
        db.executescript("""
            CREATE TABLE audit_run (state TEXT, branch TEXT, head TEXT, fingerprint TEXT);
            CREATE TABLE tasks (id TEXT PRIMARY KEY, state TEXT, summary TEXT, error TEXT);
            CREATE TABLE components (manifest TEXT, kind TEXT, name TEXT, exported TEXT,
                                     permission TEXT, details_json TEXT);
            CREATE TABLE audit_results (id TEXT PRIMARY KEY, title TEXT, severity TEXT,
                has_vulnerability INTEGER, human_verified INTEGER, ai_verdict TEXT, details_json TEXT);
        """)
        db.execute("INSERT INTO audit_run VALUES (?, ?, ?, ?)",
                   (state, report["branch"], report["head"], report["fingerprint"]))
        db.executemany("INSERT INTO tasks VALUES (?, ?, ?, ?)",
                       [(item["id"], item["state"], item["summary"], item["error"]) for item in tasks])
        db.executemany("INSERT INTO components VALUES (?, ?, ?, ?, ?, ?)",
                       [(item["manifest"], item["kind"], item["name"], item["exported"],
                         item["permission"], json.dumps(item)) for item in report["components"]])
        db.executemany("INSERT INTO audit_results VALUES (?, ?, ?, ?, ?, ?, ?)",
                       [(item["id"], item["title"], item["severity"], None, 0,
                         item["ai_review"]["verdict"], json.dumps(item)) for item in findings])
        db.commit()
    database.replace(directory / "audit.sqlite3")
    return report


def render_markdown(report):
    lines = ["# Review Android mobile entry points", "", "State: `" + report["state"] + "`",
             "", "Branch: `" + escape(report["branch"]) + "`", "", "HEAD: `" + report["head"] + "`",
             "", "Inventory files: " + str(report["source_file_count"]),
             "", "Every model finding requires human review.", "", "## Task coverage", ""]
    for task in report["tasks"]:
        lines.append("- " + task["id"] + ": **" + task["state"] + "**")
        if task["summary"]:
            lines.append("  " + escape(task["summary"]))
        if task["error"]:
            lines.append("  " + escape(task["error"]))
        if task.get("rejected_response"):
            lines.append("  Rejected response: `" + escape(task["rejected_response"]) + "`")
        if task.get("retention_error"):
            lines.append("  Response retention error: " + escape(task["retention_error"]))
        for limitation in task["limitations"]:
            lines.append("  Limitation: " + escape(limitation))
    lines.extend(["", "## Candidate findings", ""])
    if not report["findings"]:
        lines.append("No candidates are recorded. Inspect task coverage before interpreting this result.")
    for finding in report["findings"]:
        lines.extend(["### " + finding["id"] + " " + escape(finding["title"]), "",
                      "Severity proposed by model: " + finding["severity"], "",
                      "AI triage: " + finding["ai_review"]["verdict"], ""])
        for label, key in (("Entry point", "entry_point"), ("Attacker control", "attacker_control"),
                           ("Trace", "trace"), ("Impact", "impact"),
                           ("Verification needed", "verification"), ("Suggested fix", "recommended_fix")):
            lines.extend([label + ": " + escape(finding[key]), ""])
        for key in ("prerequisites", "mitigations"):
            lines.extend([key.capitalize() + ": " + escape(", ".join(finding[key]) or "None recorded"), ""])
        for evidence in finding["evidence"]:
            lines.append("- `" + escape(evidence["path"]) + ":" + str(evidence["line"]) +
                         "` " + escape(evidence["reason"]))
        if "reason" in finding["ai_review"]:
            lines.extend(["", "Triage reason: " + escape(finding["ai_review"]["reason"])])
        lines.append("")
    lines.extend(["", "## Inventory warnings", ""])
    lines.extend("- `" + escape(item["path"]) + "` " + escape(item["reason"])
                 for item in report["warnings"])
    if not report["warnings"]:
        lines.append("No inventory read or parse warnings.")
    lines.extend(["", "## Limits", ""])
    lines.extend("- " + item for item in report["limitations"])
    return "\n".join(lines) + "\n"


def write_json(path, value):
    write_text(path, json.dumps(value, indent=2, ensure_ascii=True) + "\n")


def write_text(path, text):
    temporary = path.with_name(path.name + ".pending")
    with temporary.open("w", encoding="utf-8", newline="\r\n") as stream:
        stream.write(text)
    temporary.replace(path)


def object_of(properties):
    return {"type": "object", "properties": properties, "required": list(properties),
            "additionalProperties": False}


def array_of(items):
    return {"type": "array", "items": items}


def validate_schema(value, schema):
    kind = schema["type"]
    expected = {"object": dict, "array": list, "string": str, "boolean": bool, "integer": int}
    if type(value) is not expected[kind]:
        raise ValueError("Model output does not match the required JSON types")
    if "enum" in schema and value not in schema["enum"]:
        raise ValueError("Model output has an unknown enum value")
    if kind == "object":
        if set(value) != set(schema["properties"]):
            raise ValueError("Model output has missing or unexpected fields")
        for key, child in value.items():
            validate_schema(child, schema["properties"][key])
    elif kind == "array":
        if len(value) < schema.get("minItems", 0):
            raise ValueError("Model output is missing evidence")
        for child in value:
            validate_schema(child, schema["items"])
    elif kind == "string" and not value.strip():
        raise ValueError("Model output has an empty required string")
    elif kind == "integer" and value < schema.get("minimum", value):
        raise ValueError("Model output has an invalid source line")


def escape(text):
    return re.sub(r"([\\`*\[\]<>|])", r"\\\1", text.replace("\n", " ").replace("\r", " "))
