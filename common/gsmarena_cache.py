"""Optional per-run source cache: parser fixes can reuse already downloaded pages."""
import hashlib
import json
import os
import time
from pathlib import Path

from gsmarena_http import ScrapeError, validate_url
from gsmarena_io import atomic_write, save_json


def fetch_page(url, client):
    validate_url(url)
    directory = os.environ.get("GSMARENA_PAGE_CACHE_DIR")
    if not directory:
        return client.get_html(url)
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(url.encode("utf-8")).hexdigest()
    html_path, meta_path = root / (key + ".html"), root / (key + ".json")
    if html_path.exists() and meta_path.exists():
        try:
            metadata = json.loads(meta_path.read_text(encoding="utf-8"))
            html = html_path.read_bytes()
            if metadata.get("url") != url or metadata.get("sha256") != hashlib.sha256(html).hexdigest():
                raise ValueError("URL or source digest mismatch")
            age = time.time() - metadata["fetched_at"]
            if 0 <= age <= 6 * 60 * 60:
                return html
        except (ValueError, KeyError, TypeError) as exc:
            raise ScrapeError(f"Invalid page cache for {url}: {exc}") from exc
    # HTTP errors/challenges are never cached. The HTTP client still paces every real request.
    html = client.get_html(url)
    atomic_write(html_path, lambda stream: stream.write(html), mode="wb")
    save_json(dict(url=url, sha256=hashlib.sha256(html).hexdigest(), fetched_at=time.time()), meta_path)
    return html
