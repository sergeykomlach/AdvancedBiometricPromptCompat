"""GSMArena specification parsing with resumable, atomic per-phone checkpoints."""
import csv
import re
import sys
from contextlib import nullcontext
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup

from gsmarena_http import BASE_DOMAIN, PoliteHttpClient, ScrapeError, validate_url
from gsmarena_io import load_json, save_json, save_csv

DEFAULT_STATE_FILE = "gsmarena_specs_state.json"
SPEC_PATH = re.compile(r"/[^/]+-\d{4,6}\.php$")


def load_state(state_file):
    return load_json(state_file, dict)


def save_state(state, state_file):
    return save_json(state, state_file)


def save_to_json(data, filename):
    return save_json(data, filename)


def valid_specification(entry):
    return (isinstance(entry, dict) and isinstance(entry.get("_metadata"), dict)
            and bool(entry.get("phone_name") or entry["_metadata"].get("phone_name"))
            and bool(entry["_metadata"].get("review_url")) and bool(entry["_metadata"].get("spec_url"))
            and any(isinstance(value, dict) and bool(value) for key, value in entry.items()
                    if key != "_metadata"))


def load_existing_specs(output_file):
    data = load_json(output_file, list)
    if any(not valid_specification(entry) for entry in data):
        raise ScrapeError(f"Invalid specification checkpoint: {output_file}")
    return data


def read_phone_rows(csv_file):
    with open(csv_file, encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or "review_url" not in reader.fieldnames:
            raise ScrapeError(f"Missing review_url column in {csv_file}")
        rows = [dict(phone_name=row.get("phone_name", ""), review_url=validate_url(row["review_url"]),
                     date=row.get("date", "")) for row in reader if row.get("review_url")]
    if not rows:
        raise ScrapeError(f"No review URLs in {csv_file}")
    return rows


def find_spec_url_from_review(review_url, client):
    soup = BeautifulSoup(client.get_html(review_url), "html.parser")
    links = soup.find_all("a", href=True)

    def spec_url(link):
        url = urljoin(BASE_DOMAIN, link["href"])
        parts = urlparse(url)
        if (parts.hostname in ("gsmarena.com", "www.gsmarena.com") and SPEC_PATH.fullmatch(parts.path)
                and "review" not in parts.path and "news" not in parts.path):
            return validate_url(url)
        return None

    for link in links:
        text = link.get_text(" ", strip=True).lower()
        if any(word in text for word in ("specification", "specs", "full phone")):
            candidate = spec_url(link)
            if candidate:
                return candidate
    # A single unambiguous device link is usable. Do not arbitrarily take a sidebar's first phone.
    candidates = {url for link in links if (url := spec_url(link))}
    if len(candidates) == 1:
        return candidates.pop()
    raise ScrapeError(f"No unambiguous specification link on {review_url}")


def scrape_specifications(spec_url, client):
    soup = BeautifulSoup(client.get_html(spec_url), "html.parser")
    title = soup.find("h1", class_="specs-phone-name-title")
    spec_list = soup.find("div", id="specs-list")
    if title is None or spec_list is None:
        raise ScrapeError(f"Unrecognized specification page: {spec_url}")
    specifications = {"phone_name": title.get_text(" ", strip=True)}
    for table in spec_list.find_all("table"):
        # The header belongs to THIS table. find_previous('th') assigned the preceding category.
        header = table.find("th")
        if header is None:
            continue
        category = header.get_text(" ", strip=True)
        values = {}
        for row in table.find_all("tr"):
            label = row.find("td", class_="ttl")
            value = row.find("td", class_="nfo")
            if label is not None and value is not None:
                name = re.sub(r"\s+", " ", label.get_text(" ", strip=True))
                text = re.sub(r"\s+", " ", value.get_text(" ", strip=True))
                if name and text:
                    values[name] = text
        if category and values:
            specifications.setdefault(category, {}).update(values)
    if len(specifications) <= 1:
        raise ScrapeError(f"No specification fields on {spec_url}")
    return specifications


def flatten_specs_for_csv(specs_data):
    result = []
    for entry in specs_data:
        row = dict(entry.get("_metadata", {}))
        for category, fields in entry.items():
            if category != "_metadata" and isinstance(fields, dict):
                row.update({f"{category} - {name}": value for name, value in fields.items()})
        result.append(row)
    return result


def save_specs_to_csv(data, filename="gsmarena_specifications.csv"):
    return save_csv(flatten_specs_for_csv(data), filename)


def scrape_specs_from_csv(csv_file, output_file="gsmarena_specifications.json",
                          output_csv="gsmarena_specifications.csv", max_phones=None, delay=15,
                          start_from=0, state_file=DEFAULT_STATE_FILE, client=None):
    if start_from < 0 or (max_phones is not None and max_phones < 1):
        raise ValueError("Invalid phone range")
    rows = read_phone_rows(csv_file)[start_from:]
    if max_phones is not None:
        rows = rows[:max_phones]
    all_specs = load_existing_specs(output_file)
    known = {entry["_metadata"]["review_url"] for entry in all_specs}
    state = load_state(state_file)
    with nullcontext(client) if client is not None else PoliteHttpClient(base_delay=delay) as http:
        for row in rows:
            if row["review_url"] in known:
                continue
            print(f"Specifications: {row['phone_name']}")
            try:
                url = find_spec_url_from_review(row["review_url"], http)
                specifications = scrape_specifications(url, http)
                specifications["_metadata"] = dict(row, spec_url=url)
                all_specs.append(specifications)
                save_to_json(all_specs, output_file)
                known.add(row["review_url"])
                state.update(last_successful_review_url=row["review_url"], last_error=None)
                save_state(state, state_file)
            except (ScrapeError, OSError) as exc:
                # A blocking response stops the whole run, instead of hammering the next phone.
                state.update(last_attempted_review_url=row["review_url"], last_error=str(exc))
                save_state(state, state_file)
                raise
    if not all_specs:
        raise ScrapeError("No specifications collected")
    save_specs_to_csv(all_specs, output_csv)
    return all_specs


def main():
    try:
        scrape_specs_from_csv("gsmarena_reviews.csv")
        return 0
    except (ScrapeError, OSError, ValueError) as exc:
        print(f"Scrape failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
