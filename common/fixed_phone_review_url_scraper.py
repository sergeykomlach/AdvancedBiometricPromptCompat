"""Incremental GSMArena review index; shared HTTP client also serves the specs stage."""
import sys
from contextlib import nullcontext
from dataclasses import dataclass
from urllib.parse import urljoin, parse_qs, urlparse, urlencode, urlunparse

from bs4 import BeautifulSoup

from gsmarena_http import BASE_DOMAIN, PoliteHttpClient, ScrapeError, validate_url
from gsmarena_io import load_json, save_json, save_csv

DEFAULT_BASE_URL = BASE_DOMAIN + "reviews.php3"
DEFAULT_STATE_FILE = "gsmarena_reviews_state.json"


@dataclass
class PageScrapeResult:
    reviews: list
    has_next: bool


def load_existing_reviews(json_file):
    reviews = load_json(json_file, list)
    if any(not isinstance(row, dict) or not row.get("review_url") or not row.get("phone_name") for row in reviews):
        raise ScrapeError(f"Invalid review checkpoint: {json_file}")
    return reviews


def load_state(state_file):
    return load_json(state_file, dict)


def save_state(state, state_file):
    return save_json(state, state_file)


def save_to_json(data, filename="gsmarena_reviews.json"):
    return save_json(data, filename)


def save_to_csv(data, filename="gsmarena_reviews.csv"):
    return save_csv(data, filename)


def extract_reviews_from_soup(soup):
    reviews = []
    for item in soup.select(".review-item, .review-item-new"):
        title = item.find("h3") or item.find("h2") or item.find("a", class_="review-item-title")
        if title is None:
            continue
        link = title if title.name == "a" else title.find("a")
        if link is None or not link.get("href"):
            continue
        url = validate_url(urljoin(BASE_DOMAIN, link["href"]))
        img = item.find("img")
        date = item.find("li") or item.find("span", class_="review-date")
        snippet = item.find("p")
        reviews.append({
            "phone_name": title.get_text(" ", strip=True),
            "review_url": url,
            "image_url": (img.get("src") or img.get("data-src") or "") if img else "",
            "image_alt": img.get("alt", "") if img else "",
            "date": date.get_text(" ", strip=True) if date else "",
            "snippet": snippet.get_text(" ", strip=True) if snippet else "",
        })
    return reviews


def page_url(base_url, page):
    parts = urlparse(base_url)
    query = parse_qs(parts.query)
    query["iPage"] = [str(page)]
    return urlunparse(parts._replace(query=urlencode(query, doseq=True)))


def scrape_single_page(url, client):
    soup = BeautifulSoup(client.get_html(url), "html.parser")
    reviews = extract_reviews_from_soup(soup)
    if not reviews:
        # An access challenge or changed markup must not masquerade as the end of pagination.
        raise ScrapeError(f"No recognizable reviews on {url}")
    page = int(parse_qs(urlparse(url).query).get("iPage", ["1"])[0])
    has_next = False
    for link in soup.select("a[href]"):
        target = urlparse(urljoin(url, link["href"]))
        if target.hostname not in ("www.gsmarena.com", "gsmarena.com") or target.path != urlparse(url).path:
            continue
        candidate = parse_qs(target.query).get("iPage", ["0"])[0]
        if candidate.isdigit() and int(candidate) > page:
            has_next = True
    return PageScrapeResult(reviews, has_next)


def dedupe_reviews(reviews):
    return list({row["review_url"]: row for row in reviews}.values())


def scrape_gsmarena_reviews(base_url=DEFAULT_BASE_URL, start_page=1, max_pages=None, delay=12.0,
                            json_output="gsmarena_reviews.json", state_file=DEFAULT_STATE_FILE,
                            stop_on_known_review=True, client=None):
    if start_page < 1 or (max_pages is not None and max_pages < 1):
        raise ValueError("start_page and max_pages must be positive")
    existing = load_existing_reviews(json_output)
    state = load_state(state_file)
    known = {row["review_url"] for row in existing}
    # Always check the first page for new arrivals; a saved page number drifts as reviews are added.
    # Interrupted backfills cannot stop at their own partial checkpoint's already-known first page.
    can_stop_known = stop_on_known_review and bool(existing) and (not state or state.get("complete") is True)
    all_reviews = list(existing)
    state["complete"] = False
    page = start_page
    visited = 0
    with nullcontext(client) if client is not None else PoliteHttpClient(base_delay=delay) as http:
        while True:
            url = page_url(base_url, page)
            print(f"Review index page {page}: {url}")
            try:
                result = scrape_single_page(url, http)
                new = [row for row in result.reviews if row["review_url"] not in known]
                all_reviews = dedupe_reviews(all_reviews + new)
                # Checkpoint data before advancing state. Exceptions propagate to the CLI/Gradle.
                save_to_json(all_reviews, json_output)
                known.update(row["review_url"] for row in new)
                visited += 1
                complete = not result.has_next or (can_stop_known and len(new) < len(result.reviews))
                state.update(last_successful_page=page, last_error=None, complete=complete)
                save_state(state, state_file)
                if complete or (max_pages is not None and visited >= max_pages):
                    break
                page += 1
            except (ScrapeError, OSError) as exc:
                state.update(last_attempted_page=page, last_error=str(exc), complete=False)
                save_state(state, state_file)
                raise
    print(f"Review index: {len(all_reviews)} records, {visited} pages visited")
    return all_reviews


def main():
    try:
        reviews = scrape_gsmarena_reviews()
        save_to_csv(reviews)
        return 0
    except (ScrapeError, OSError, ValueError) as exc:
        print(f"Scrape failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
