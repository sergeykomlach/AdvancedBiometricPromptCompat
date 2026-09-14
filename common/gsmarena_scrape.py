"""Gradle entry point: one paced HTTP session across review discovery and specifications."""
import sys
from contextlib import nullcontext
from pathlib import Path

from fixed_phone_review_url_scraper import scrape_gsmarena_reviews, save_to_csv
from fixed_phone_specs_scraper import scrape_specs_from_csv
from gsmarena_http import PoliteHttpClient, ScrapeError


def run(output_dir=".", client=None):
    root = Path(output_dir)
    with nullcontext(client) if client is not None else PoliteHttpClient() as http:
        reviews = scrape_gsmarena_reviews(
            json_output=root / "gsmarena_reviews.json", state_file=root / "gsmarena_reviews_state.json", client=http)
        review_csv = root / "gsmarena_reviews.csv"
        save_to_csv(reviews, review_csv)
        return scrape_specs_from_csv(
            review_csv, output_file=root / "gsmarena_specifications.json",
            output_csv=root / "gsmarena_specifications.csv", state_file=root / "gsmarena_specs_state.json", client=http)


def main():
    try:
        results = run()
        print(f"Scrape completed: {len(results)} specifications available for copying")
        return 0
    except (ScrapeError, OSError, ValueError) as exc:
        print(f"Scrape failed; assets must not be replaced: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
