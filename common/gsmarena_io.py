"""Atomic checkpoints: a failed scrape must not destroy the last readable dataset."""
import csv
import json
import os
import tempfile
from pathlib import Path

from gsmarena_http import ScrapeError


def atomic_write(filename, write):
    target = Path(filename)
    temp = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="", dir=target.parent,
                                         prefix=target.name + ".", suffix=".tmp", delete=False) as stream:
            temp = Path(stream.name)
            write(stream)
        os.replace(temp, target)
    finally:
        if temp is not None:
            temp.unlink(missing_ok=True)


def save_json(data, filename):
    atomic_write(filename, lambda stream: json.dump(data, stream, indent=2, ensure_ascii=False))
    return True


def load_json(filename, expected_type):
    path = Path(filename)
    if not path.exists():
        return expected_type()
    # A corrupt existing file is an error, not permission to overwrite it with an empty dataset.
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, expected_type):
            raise ValueError(f"Expected {expected_type.__name__}")
        return value
    except (ValueError, OSError) as exc:
        raise ScrapeError(f"Cannot read checkpoint {filename}: {exc}") from exc


def save_csv(data, filename, columns=None):
    if not data:
        raise ScrapeError(f"Refusing to publish empty CSV: {filename}")
    columns = columns or sorted({key for row in data for key in row})

    def write(stream):
        writer = csv.DictWriter(stream, fieldnames=columns)
        writer.writeheader()
        writer.writerows(data)

    atomic_write(filename, write)
    return True
