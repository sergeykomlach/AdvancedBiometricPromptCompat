import csv
import json
import random
import time
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Dict, List, Optional, Set
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup


BASE_DOMAIN = "https://www.gsmarena.com/"
DEFAULT_BASE_URL = "https://www.gsmarena.com/reviews.php3"
DEFAULT_STATE_FILE = "gsmarena_reviews_state.json"


@dataclass
class FetchOutcome:
    response: Optional[requests.Response]
    error: Optional[str] = None
    rate_limited: bool = False
    retry_after_seconds: Optional[float] = None


@dataclass
class PageScrapeResult:
    reviews: List[dict]
    has_content: bool
    rate_limited: bool = False
    should_retry: bool = False
    error: Optional[str] = None


class PoliteHttpClient:
    def __init__(self, base_delay: float = 12.0, max_delay: float = 180.0, timeout: int = 20):
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
                print(f"  Rate limited. Waiting {wait_time:.1f}s before retry {attempt}/{max_attempts}...")
                self.sleep(wait_time)
            else:
                if attempt == max_attempts:
                    return outcome
                print(f"  Request failed ({outcome.error}). Waiting {delay:.1f}s before retry {attempt}/{max_attempts}...")
                self.sleep(delay)
            delay = min(delay * 2, self.max_delay)
            attempt += 1
        return outcome


def load_existing_reviews(json_file: str) -> List[dict]:
    path = Path(json_file)
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(data, list):
            return data
    except Exception as exc:
        print(f"Warning: could not read existing reviews from {json_file}: {exc}")
    return []



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



def extract_reviews_from_soup(soup: BeautifulSoup) -> List[dict]:
    reviews: List[dict] = []

    selectors = [
        ("div", {"class_": "review-item"}),
    ]

    review_items = []
    for tag, kwargs in selectors:
        review_items = soup.find_all(tag, **kwargs)
        if review_items:
            break

    if not review_items:
        review_body = soup.find("div", id="review-body")
        if review_body:
            review_items = review_body.find_all("div", class_="review-item")

    if not review_items:
        review_items = soup.select(".review-item-new")

    for item in review_items:
        review_data = {}

        title = item.find("h3") or item.find("h2") or item.find("a", class_="review-item-title")
        if title:
            review_data["phone_name"] = title.get_text(strip=True)
            link = title.find("a") if title.name != "a" else title
            if link and link.get("href"):
                review_data["review_url"] = urljoin(BASE_DOMAIN, link["href"])

        img = item.find("img")
        if img:
            review_data["image_url"] = img.get("src") or img.get("data-src") or ""
            review_data["image_alt"] = img.get("alt", "")

        date_elem = item.find("li") or item.find("span", class_="review-date")
        if date_elem:
            review_data["date"] = date_elem.get_text(strip=True)

        snippet = item.find("p")
        if snippet:
            review_data["snippet"] = snippet.get_text(" ", strip=True)

        if review_data.get("phone_name") and review_data.get("review_url"):
            reviews.append(review_data)

    return reviews



def scrape_single_page(url: str, client: PoliteHttpClient) -> PageScrapeResult:
    outcome = client.get_with_retry(url)
    if outcome.response is None or outcome.error:
        return PageScrapeResult(
            reviews=[],
            has_content=True,
            rate_limited=bool(outcome.rate_limited),
            should_retry=False,
            error=outcome.error,
        )

    try:
        response = outcome.response
        soup = BeautifulSoup(response.content, "html.parser")
        reviews = extract_reviews_from_soup(soup)
        has_content = len(response.content or b"") > 1000
        return PageScrapeResult(reviews=reviews, has_content=has_content)
    except Exception as exc:
        return PageScrapeResult(reviews=[], has_content=True, error=f"Parse error: {exc}")



def dedupe_reviews(reviews: List[dict]) -> List[dict]:
    unique = []
    seen: Set[str] = set()
    for review in reviews:
        key = review.get("review_url") or json.dumps(review, sort_keys=True, ensure_ascii=False)
        if key in seen:
            continue
        seen.add(key)
        unique.append(review)
    return unique



def save_to_json(data: List[dict], filename: str = "gsmarena_reviews.json") -> bool:
    try:
        Path(filename).write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"\n✓ Data saved to {filename}")
        return True
    except Exception as exc:
        print(f"\n✗ Error saving to JSON: {exc}")
        return False



def save_to_csv(data: List[dict], filename: str = "gsmarena_reviews.csv") -> bool:
    try:
        if not data:
            return False
        keys = sorted({key for row in data for key in row.keys()})
        with open(filename, "w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=keys)
            writer.writeheader()
            writer.writerows(data)
        print(f"✓ Data saved to {filename}")
        return True
    except Exception as exc:
        print(f"✗ Error saving to CSV: {exc}")
        return False



def scrape_gsmarena_reviews(
    base_url: str = DEFAULT_BASE_URL,
    start_page: int = 1,
    max_pages: Optional[int] = None,
    delay: float = 12.0,
    json_output: str = "gsmarena_reviews.json",
    state_file: str = DEFAULT_STATE_FILE,
    stop_on_known_review: bool = True,
):
    existing_reviews = load_existing_reviews(json_output)
    known_urls = {row.get("review_url") for row in existing_reviews if row.get("review_url")}
    state = load_state(state_file)

    client = PoliteHttpClient(base_delay=delay)
    all_reviews = list(existing_reviews)
    page_num = max(start_page, int(state.get("last_successful_page", start_page)))
    pages_scraped = 0
    consecutive_empty = 0
    max_consecutive_empty = 3

    print("Starting pagination scrape...")
    print("=" * 80)
    if page_num == start_page and delay > 0:
        print(f"Initial pause: waiting {delay} seconds before first request...")
        client.sleep(delay)

    while True:
        current_url = base_url if page_num == 1 else f"{base_url}?iPage={page_num}"
        print(f"\nScraping page {page_num}...")
        print(f"URL: {current_url}")

        result = scrape_single_page(current_url, client)

        if result.error:
            print(f"✗ Error on page {page_num}: {result.error}")
            if result.rate_limited:
                print("  Stopping for now because the site is rate limiting requests.")
            elif not result.has_content:
                print("  Stopping because the page returned no usable content.")
            state["last_attempted_page"] = page_num
            state["last_error"] = result.error
            save_state(state, state_file)
            break

        new_reviews = []
        already_seen_on_page = 0
        for review in result.reviews:
            review_url = review.get("review_url")
            if review_url and review_url in known_urls:
                already_seen_on_page += 1
                continue
            if review_url:
                known_urls.add(review_url)
            new_reviews.append(review)

        if new_reviews:
            all_reviews.extend(new_reviews)
            all_reviews = dedupe_reviews(all_reviews)
            consecutive_empty = 0
            print(f"✓ Found {len(result.reviews)} reviews on page {page_num}")
            print(f"  New reviews added: {len(new_reviews)}")
            if already_seen_on_page:
                print(f"  Already known reviews skipped: {already_seen_on_page}")
            print(f"  Total unique reviews so far: {len(all_reviews)}")
            save_to_json(all_reviews, json_output)
            state["last_successful_page"] = page_num + 1
            state["last_error"] = None
            save_state(state, state_file)
        else:
            consecutive_empty += 1
            print(f"✗ No new reviews found on page {page_num}")
            if already_seen_on_page:
                print(f"  Page contains {already_seen_on_page} already-known reviews")
            if stop_on_known_review and already_seen_on_page and not result.reviews:
                print("\nReached already processed area. Stopping.")
                break
            if not result.has_content or consecutive_empty >= max_consecutive_empty:
                message = "Page has no content" if not result.has_content else f"No reviews found for {consecutive_empty} consecutive pages"
                print(f"\n{message}. Stopping.")
                break

        pages_scraped += 1
        if max_pages and pages_scraped >= max_pages:
            print(f"\nReached maximum page limit ({max_pages})")
            break

        page_num += 1
        if delay > 0:
            print(f"Waiting about {delay} seconds before next request...")
            client.sleep(delay)

    print("\n" + "=" * 80)
    print("\n✓ Scraping complete!")
    print(f"  Total unique reviews collected: {len(all_reviews)}")
    print(f"  Pages visited in this run: {pages_scraped}")
    print("=" * 80)

    if all_reviews:
        print("\nShowing first 5 reviews:")
        for index, review in enumerate(all_reviews[:5], 1):
            print(f"\n{index}. {review.get('phone_name', 'N/A')}")
            print(f"   Date: {review.get('date', 'N/A')}")
            print(f"   URL: {review.get('review_url', 'N/A')}")
            snippet = review.get("snippet")
            if snippet:
                print(f"   Snippet: {snippet[:100]}{'...' if len(snippet) > 100 else ''}")

    return all_reviews


if __name__ == "__main__":
    print("GSMArena Reviews Scraper")
    print("=" * 80)

    BASE_URL = DEFAULT_BASE_URL
    START_PAGE = 1
    MAX_PAGES = None
    DELAY = 12
    OUTPUT_JSON = "gsmarena_reviews.json"
    OUTPUT_CSV = "gsmarena_reviews.csv"
    STATE_FILE = DEFAULT_STATE_FILE

    print("\nConfiguration:")
    print(f"  Base URL: {BASE_URL}")
    print(f"  Start Page: {START_PAGE}")
    print(f"  Max Pages: {MAX_PAGES if MAX_PAGES else 'All pages'}")
    print(f"  Delay: {DELAY} seconds")
    print(f"  Output JSON: {OUTPUT_JSON}")
    print(f"  Output CSV: {OUTPUT_CSV}")
    print(f"  State File: {STATE_FILE}")
    print()

    reviews_data = scrape_gsmarena_reviews(
        base_url=BASE_URL,
        start_page=START_PAGE,
        max_pages=MAX_PAGES,
        delay=DELAY,
        json_output=OUTPUT_JSON,
        state_file=STATE_FILE,
    )

    if reviews_data:
        save_to_json(reviews_data, OUTPUT_JSON)
        save_to_csv(reviews_data, OUTPUT_CSV)
        print(f"\n{'=' * 80}")
        print(f"✓ Successfully collected {len(reviews_data)} unique reviews from GSMArena!")
        print(f"{'=' * 80}")
    else:
        print("\n✗ No reviews found or the site is currently rate limiting requests.")
        print("\nTroubleshooting tips:")
        print("  1. Wait longer before retrying if you recently ran the scraper")
        print("  2. Increase DELAY to 20-30 seconds")
        print("  3. Reuse the saved JSON/state files instead of re-scraping everything")
        print("  4. Resume later from the saved state file")
