"""Gradle entry point: one paced HTTP session across review discovery and specifications."""
import sys
from contextlib import nullcontext
from pathlib import Path

from fixed_phone_review_url_scraper import scrape_gsmarena_reviews, save_to_csv
from fixed_phone_specs_scraper import scrape_specs_from_csv, load_existing_specs
from gsmarena_http import PoliteHttpClient, ScrapeError
from gsmarena_io import load_json, save_json


def run(output_dir=".", client=None):
    root = Path(output_dir)
    output = root / "gsmarena_specifications.json"
    asset = root / "src/main/assets/devices/specifications.json"
    if asset.exists():
        current = load_existing_specs(output)
        known = {row["_metadata"]["review_url"] for row in current}
        recovered = [row for row in load_existing_specs(asset) if row["_metadata"]["review_url"] not in known]
        if recovered:
            save_json(current + recovered, output)
            print(f"Recovered {len(recovered)} existing specifications from published assets", flush=True)
    with nullcontext(client) if client is not None else PoliteHttpClient() as http:
        reviews = scrape_gsmarena_reviews(
            json_output=root / "gsmarena_reviews.json", state_file=root / "gsmarena_reviews_state.json", client=http)
        if load_json(root / "gsmarena_reviews_state.json", dict).get("complete") is not True:
            raise ScrapeError("Review discovery is incomplete; assets must not be replaced")
        review_csv = root / "gsmarena_reviews.csv"
        save_to_csv(reviews, review_csv)
        return scrape_specs_from_csv(
            review_csv, output_file=output,
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
