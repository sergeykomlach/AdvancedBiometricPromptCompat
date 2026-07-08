import csv
import json
import random
import re
import time
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup


BASE_DOMAIN = "https://www.gsmarena.com/"
DEFAULT_STATE_FILE = "gsmarena_specs_state.json"


@dataclass
class FetchOutcome:
    response: Optional[requests.Response]
    error: Optional[str] = None
    rate_limited: bool = False
    retry_after_seconds: Optional[float] = None


class PoliteHttpClient:
    def __init__(self, base_delay: float = 15.0, max_delay: float = 180.0, timeout: int = 20):
        self.session = requests.Session()
        self.base_delay = max(base_delay, 0.0)
        self.max_delay = max(max_delay, self.base_delay)
        self.timeout = timeout
        self.default_headers = {
            "User-Agent": "GSMArenaResearchScraper/1.0 (+respectful crawling)",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
        }

    def sleep(self, seconds: float):
        if seconds <= 0:
            return
        jitter = random.uniform(0.0, min(2.5, seconds * 0.15))
        time.sleep(seconds + jitter)

    @staticmethod
    def _parse_retry_after(value: Optional[str]) -> Optional[float]:
        if not value:
            return None
        value = value.strip()
        if value.isdigit():
            return float(value)
        try:
            dt = parsedate_to_datetime(value)
            return max(0.0, dt.timestamp() - time.time())
        except Exception:
            return None

    def get_once(self, url: str, headers: Optional[Dict[str, str]] = None) -> FetchOutcome:
        merged_headers = dict(self.default_headers)
        if headers:
            merged_headers.update(headers)
        try:
            response = self.session.get(url, headers=merged_headers, timeout=self.timeout)
            if response.status_code == 429:
                return FetchOutcome(
                    response=response,
                    error="429 Too Many Requests",
                    rate_limited=True,
                    retry_after_seconds=self._parse_retry_after(response.headers.get("Retry-After")),
                )
            if response.status_code in (500, 502, 503, 504):
                return FetchOutcome(response=response, error=f"HTTP {response.status_code}")
            response.raise_for_status()
            return FetchOutcome(response=response)
        except requests.RequestException as exc:
            return FetchOutcome(response=None, error=str(exc))

    def get_with_retry(self, url: str, headers: Optional[Dict[str, str]] = None, max_attempts: int = 5) -> FetchOutcome:
        delay = max(self.base_delay, 1.0)
        attempt = 1
        while attempt <= max_attempts:
            outcome = self.get_once(url, headers=headers)
            if outcome.response is not None and not outcome.error:
                return outcome
            if outcome.rate_limited:
                wait_time = outcome.retry_after_seconds if outcome.retry_after_seconds is not None else delay
                wait_time = min(max(wait_time, self.base_delay), self.max_delay)
                print(f"    Rate limited. Waiting {wait_time:.1f}s before retry {attempt}/{max_attempts}...")
                self.sleep(wait_time)
            else:
                if attempt == max_attempts:
                    return outcome
                print(f"    Request failed ({outcome.error}). Waiting {delay:.1f}s before retry {attempt}/{max_attempts}...")
                self.sleep(delay)
            delay = min(delay * 2, self.max_delay)
            attempt += 1
        return outcome



def load_state(state_file: str) -> dict:
    path = Path(state_file)
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except Exception as exc:
        print(f"Warning: could not read state file {state_file}: {exc}")
        return {}



def save_state(state: dict, state_file: str):
    try:
        Path(state_file).write_text(json.dumps(state, indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception as exc:
        print(f"Warning: could not save state file {state_file}: {exc}")



def save_to_json(data, filename):
    try:
        Path(filename).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
        return True
    except Exception as exc:
        print(f"✗ Error saving JSON: {exc}")
        return False



def load_existing_specs(output_file: str) -> List[dict]:
    path = Path(output_file)
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else []
    except Exception as exc:
        print(f"Warning: could not read existing specs from {output_file}: {exc}")
        return []



def read_phone_rows(csv_file: str) -> List[dict]:
    rows = []
    with open(csv_file, "r", encoding="utf-8") as file:
        reader = csv.DictReader(file)
        for row in reader:
            if row.get("review_url"):
                rows.append({
                    "phone_name": row.get("phone_name", "Unknown"),
                    "review_url": row["review_url"],
                    "date": row.get("date", "Unknown"),
                })
    return rows



def find_spec_url_from_review(review_url: str, client: PoliteHttpClient) -> Optional[str]:
    outcome = client.get_with_retry(review_url)
    if outcome.response is None or outcome.error:
        print(f"  ✗ Error opening review page: {outcome.error}")
        return None

    try:
        soup = BeautifulSoup(outcome.response.content, "html.parser")
        links = soup.find_all("a", href=True)

        for link in links:
            href = link["href"]
            text = link.get_text(" ", strip=True).lower()
            if ("specification" in text or "specs" in text or "full phone" in text) and ".php" in href:
                return urljoin(BASE_DOMAIN, href)

        for link in links:
            href = link["href"]
            if re.search(r"-\d{4,5}\.php", href) and "review" not in href:
                return urljoin(BASE_DOMAIN, href)
    except Exception as exc:
        print(f"  ✗ Error finding spec URL: {exc}")
        return None

    return None



def scrape_specifications(spec_url: str, client: PoliteHttpClient) -> Optional[dict]:
    outcome = client.get_with_retry(spec_url)
    if outcome.response is None or outcome.error:
        print(f"  ✗ Error scraping specifications: {outcome.error}")
        return None

    try:
        soup = BeautifulSoup(outcome.response.content, "html.parser")
        specifications = {}

        phone_title = soup.find("h1", class_="specs-phone-name-title")
        if phone_title:
            specifications["phone_name"] = phone_title.get_text(strip=True)

        spec_tables = soup.find_all("table")
        for table in spec_tables:
            category_header = table.find_previous("th")
            category = category_header.get_text(strip=True) if category_header else "General"
            specifications.setdefault(category, {})

            for row in table.find_all("tr"):
                cells = row.find_all("td")
                if len(cells) >= 2:
                    spec_name = re.sub(r"\s+", " ", cells[0].get_text(strip=True))
                    spec_value = re.sub(r"\s+", " ", cells[1].get_text(strip=True))
                    if spec_name and spec_value:
                        specifications[category][spec_name] = spec_value

        if len(specifications) <= 1:
            spec_list = soup.find("div", id="specs-list")
            if spec_list:
                for table in spec_list.find_all("table"):
                    header = table.find("th")
                    if not header:
                        continue
                    category = header.get_text(strip=True)
                    specifications.setdefault(category, {})
                    rows = table.find_all("tr")
                    for row in rows:
                        cells = row.find_all("td", class_="ttl")
                        values = row.find_all("td", class_="nfo")
                        if cells and values:
                            for cell, value in zip(cells, values):
                                spec_name = cell.get_text(strip=True)
                                spec_value = value.get_text(strip=True)
                                if spec_name and spec_value:
                                    specifications[category][spec_name] = spec_value

        if not specifications:
            return None
        return specifications
    except Exception as exc:
        print(f"  ✗ Error scraping specifications: {exc}")
        return None



def flatten_specs_for_csv(specs_data):
    flattened = []
    for phone_specs in specs_data:
        row = {}
        if "_metadata" in phone_specs:
            row.update(phone_specs["_metadata"])
        for category, specs in phone_specs.items():
            if category != "_metadata" and isinstance(specs, dict):
                for spec_name, spec_value in specs.items():
                    row[f"{category} - {spec_name}"] = spec_value
        flattened.append(row)
    return flattened



def save_specs_to_csv(data, filename="gsmarena_specifications.csv"):
    try:
        if not data:
            return False
        flattened_data = flatten_specs_for_csv(data)
        if not flattened_data:
            return False

        all_columns = set()
        for row in flattened_data:
            all_columns.update(row.keys())

        metadata_cols = ["phone_name", "date", "review_url", "spec_url"]
        spec_cols = sorted(col for col in all_columns if col not in metadata_cols)
        columns = [col for col in metadata_cols if col in all_columns] + spec_cols

        with open(filename, "w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=columns)
            writer.writeheader()
            writer.writerows(flattened_data)

        print(f"✓ Specifications saved to {filename}")
        print(f"  Columns: {len(columns)}")
        return True
    except Exception as exc:
        print(f"✗ Error saving CSV: {exc}")
        return False



def scrape_specs_from_csv(
    csv_file,
    output_file="gsmarena_specifications.json",
    output_csv="gsmarena_specifications.csv",
    max_phones=None,
    delay=15,
    start_from=0,
    state_file=DEFAULT_STATE_FILE,
):
    print(f"Reading review URLs from {csv_file}...")
    try:
        phone_data = read_phone_rows(csv_file)
    except Exception as exc:
        print(f"✗ Error reading CSV: {exc}")
        return []

    print(f"Found {len(phone_data)} phones in CSV")

    if start_from > 0:
        phone_data = phone_data[start_from:]
        print(f"Starting from phone #{start_from + 1}")
    if max_phones:
        phone_data = phone_data[:max_phones]
        print(f"Limiting to {max_phones} phones")

    existing_specs = load_existing_specs(output_file)
    existing_review_urls = {
        entry.get("_metadata", {}).get("review_url")
        for entry in existing_specs
        if isinstance(entry, dict)
    }
    existing_review_urls.discard(None)

    client = PoliteHttpClient(base_delay=delay)
    all_specifications = list(existing_specs)
    state = load_state(state_file)

    print("=" * 80)
    if delay > 0:
        print(f"Initial pause: waiting {delay} seconds before first request...")
        client.sleep(delay)

    processed_in_run = 0
    for idx, phone_info in enumerate(phone_data, 1):
        if phone_info["review_url"] in existing_review_urls:
            print(f"\n[{idx}/{len(phone_data)}] {phone_info['phone_name']}")
            print("  Skipped: already present in output JSON")
            continue

        print(f"\n[{idx}/{len(phone_data)}] {phone_info['phone_name']}")
        print(f"  Review URL: {phone_info['review_url']}")

        spec_url = find_spec_url_from_review(phone_info["review_url"], client)
        if not spec_url:
            print("  ✗ Could not find specification URL")
            state["last_attempted_review_url"] = phone_info["review_url"]
            state["last_error"] = "spec_url_not_found"
            save_state(state, state_file)
            continue

        print(f"  ✓ Found spec URL: {spec_url}")
        specifications = scrape_specifications(spec_url, client)
        if not specifications:
            print("  ✗ Failed to scrape specifications")
            state["last_attempted_review_url"] = phone_info["review_url"]
            state["last_error"] = "spec_scrape_failed"
            save_state(state, state_file)
            continue

        specifications["_metadata"] = {
            "phone_name": phone_info["phone_name"],
            "review_url": phone_info["review_url"],
            "spec_url": spec_url,
            "date": phone_info["date"],
        }
        all_specifications.append(specifications)
        existing_review_urls.add(phone_info["review_url"])
        processed_in_run += 1

        total_specs = sum(len(value) for key, value in specifications.items() if isinstance(value, dict))
        print(f"  ✓ Extracted {len(specifications) - 1} categories with {total_specs} specifications")

        save_to_json(all_specifications, output_file)
        save_specs_to_csv(all_specifications, output_csv)
        state["last_successful_review_url"] = phone_info["review_url"]
        state["last_error"] = None
        save_state(state, state_file)

        if idx < len(phone_data) and delay > 0:
            print(f"  Waiting about {delay} seconds...")
            client.sleep(delay)

    print("\n" + "=" * 80)
    print("\n✓ Scraping complete!")
    print(f"  New phones scraped in this run: {processed_in_run}")
    print(f"  Total phones available in output: {len(all_specifications)}")
    print("=" * 80)
    return all_specifications


if __name__ == "__main__":
    print("GSMArena Specifications Scraper")
    print("=" * 80)

    INPUT_CSV = "gsmarena_reviews.csv"
    OUTPUT_JSON = "gsmarena_specifications.json"
    OUTPUT_CSV = "gsmarena_specifications.csv"
    MAX_PHONES = None
    DELAY = 15
    START_FROM = 0
    STATE_FILE = DEFAULT_STATE_FILE

    print("\nConfiguration:")
    print(f"  Input CSV: {INPUT_CSV}")
    print(f"  Output JSON: {OUTPUT_JSON}")
    print(f"  Output CSV: {OUTPUT_CSV}")
    print(f"  Max Phones: {MAX_PHONES if MAX_PHONES else 'All'}")
    print(f"  Delay: {DELAY} seconds")
    print(f"  Start From: Phone #{START_FROM + 1}")
    print(f"  State File: {STATE_FILE}")
    print()

    specifications = scrape_specs_from_csv(
        csv_file=INPUT_CSV,
        output_file=OUTPUT_JSON,
        output_csv=OUTPUT_CSV,
        max_phones=MAX_PHONES,
        delay=DELAY,
        start_from=START_FROM,
        state_file=STATE_FILE,
    )

    if specifications:
        save_to_json(specifications, OUTPUT_JSON)
        save_specs_to_csv(specifications, OUTPUT_CSV)
        print(f"\n{'=' * 80}")
        print(f"✓ Successfully collected specifications for {len(specifications)} phones!")
        print(f"{'=' * 80}")
    else:
        print("\n✗ No specifications scraped.")
        print("\nTroubleshooting tips:")
        print("  1. Make sure gsmarena_reviews.csv already contains valid review URLs")
        print("  2. Increase DELAY to reduce rate limiting")
        print("  3. Resume later from the saved JSON/state files")
