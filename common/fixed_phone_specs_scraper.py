"""GSMArena specification parsing with resumable, atomic per-phone checkpoints."""
import csv
import re
import sys
from contextlib import nullcontext
from urllib.parse import urljoin, urlparse

from bs4 import BeautifulSoup, NavigableString, CData

from gsmarena_http import BASE_DOMAIN, PoliteHttpClient, ScrapeError, validate_url
from gsmarena_io import load_json, save_json, save_csv
from gsmarena_review_policy import NonDeviceReview, non_device_reason, PROSE_ONLY_REVIEW_TITLES
from gsmarena_cache import fetch_page

DEFAULT_STATE_FILE = "gsmarena_specs_state.json"
SPEC_PATH = re.compile(r"/[^/]+-\d{1,6}\.php$")

# Exact source corrections, verified against each article's H1 and body; never fuzzy model matching.
INLINE_MODEL_CORRECTIONS = {
    ("https://www.gsmarena.com/samsung_galaxy_note_ii_n7100-review-806.php", "samsung galaxy note ii n7100", "samsung galaxy note ii"):
        ("Samsung Galaxy Note II N7100", "Article title omits the N7100 model code explicitly supplied by its summary"),
    ("https://www.gsmarena.com/htc_re_camera-review-1179.php", "key features", "htc re camera"):
        ("HTC Re camera", "Generic summary heading identifies the HTC Re camera described by the H1 and body"),
    ("https://www.gsmarena.com/pebble_time-review-1265.php", "key features", "pebble time"):
        ("Pebble Time", "Generic summary heading identifies the single Pebble Time described by the H1 and body"),
    ("https://www.gsmarena.com/huawei_mate_book_mwc_2016-review-1389.php", "huawei media book", "huawei mate book"):
        ("Huawei Mate Book", "Summary model typo; H1 and body identify Huawei Mate Book"),
    ("https://www.gsmarena.com/letv_le_1s_hands_on-review-1373.php", "letv le 1s", "letv (leeco) le 1s"):
        ("LeTV Le 1s", "Article identifies LeTV and LeEco as names for this same model"),
}


class SpecificationParseError(ScrapeError):
    pass


def parse_downloaded_page(parser, *args, **kwargs):
    try:
        return parser(*args, **kwargs)
    except NonDeviceReview:
        raise
    except ScrapeError as exc:
        raise SpecificationParseError(str(exc)) from exc


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
    urls = [entry["_metadata"]["review_url"] for entry in data]
    if len(urls) != len(set(urls)):
        raise ScrapeError(f"Duplicate review URL in specification checkpoint: {output_file}")
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


def verified_review_heading(soup, review_url):
    title = soup.select_one("h1.article-info-name")
    body = soup.select_one("#review-body")
    canonical = soup.find("link", rel="canonical")
    if (title is None or canonical is None or canonical.get("href") != review_url
            or body is None or not body.get_text(strip=True)):
        raise ScrapeError(f"Unverified review identity or incomplete article: {review_url}")
    heading = title.get_text(" ", strip=True)
    excluded = non_device_reason(heading)
    if excluded:
        raise NonDeviceReview(heading, *excluded)
    return heading


def inline_review_specifications(soup, review_url):
    heading = verified_review_heading(soup, review_url)
    summaries = []
    for block in soup.select("#review-body ul"):
        title = block.find_previous_sibling(["h2", "h3"])
        previous = block.find_previous_sibling()
        if previous is not None and previous.name == "p":
            wrapped = previous.find(["h2", "h3"], recursive=False)
            if wrapped is not None and previous.get_text(" ", strip=True) == wrapped.get_text(" ", strip=True):
                title = wrapped
        if title is None:
            continue
        title_text = " ".join(title.get_text(" ", strip=True).split())
        if "article-blurb" in block.get("class", []):
            if "article-blurb-title" not in title.get("class", []):
                continue
        elif block.get("class") or not re.search(
                r"\s+(?:(?:specs|specifications|key features)(?: at a glance)?|at a glance)\s*[:.]?$",
                title_text, re.IGNORECASE):
            continue
        name = re.sub(r"\s+(?:(?:specs|specifications|key features)(?: at a glance)?|at a glance)\s*[:.]?$", "",
                      title_text, flags=re.IGNORECASE).strip()
        fields = {}
        container = block
        while not container.find_all("li", recursive=False):
            wrappers = container.find_all("ul", recursive=False)
            if len(wrappers) != 1:
                break
            container = wrappers[0]
        # HTMLParser nests legacy LI elements when their optional closing tags were omitted.
        # Keep each logical item within this UL, excluding genuine nested sublists.
        items = [item for item in container.find_all("li") if item.find_parent("ul") is container]
        logical_item_ids = {id(item) for item in items}
        def field_owner(node):
            return next((parent for parent in node.parents if id(parent) in logical_item_ids), None)
        for index, item in enumerate(items, 1):
            text = " ".join(str(node).strip() for node in item.find_all(string=True)
                if type(node) in (NavigableString, CData) and field_owner(node) is item and str(node).strip())
            if not text:
                continue
            label = next((node for node in item.find_all(["b", "strong"]) if node.find_parent("li") is item), None)
            if label is None:
                fields[f"Feature {index}"] = text
                continue
            raw_label = label.get_text(" ", strip=True)
            key = raw_label.rstrip(":").strip()
            value = text[len(raw_label):].strip()
            if not raw_label.endswith(":"):
                if not value.startswith(":"):
                    fields[f"Feature {index}"] = text
                    continue
                value = value[1:].strip()
            if key and value:
                fields[key] = value
        if len(fields) >= 3:
            summaries.append((name, fields))
    article_name = re.sub(r"\s+(?:long[- ]term review|hands[- ]on(?: review)?|review|preview)(?:\s*:\s*.*)?$", "",
                          " ".join(heading.split()), flags=re.IGNORECASE).strip().casefold()
    matching = []
    for name, fields in summaries:
        original_name, expected_name, provenance = name, article_name, {}
        correction = INLINE_MODEL_CORRECTIONS.get((review_url, name.casefold(), article_name))
        if correction:
            name, note = correction
            expected_name = name.casefold()
            provenance = dict(inline_original_model=original_name, inline_model_note=note)
        allowed_names = {name.casefold(), *(f"{name.casefold()} {kind}" for kind in
            ("android game console", "watch", "smartwatch", "camera", "tablet", "phone", "smartphone"))}
        if len(name) >= 4 and expected_name in allowed_names:
            matching.append((name, fields, provenance))
    if len(matching) != 1:
        return None
    name, fields, provenance = matching[0]
    result = {"phone_name": name, "Review specifications": fields}
    if provenance:
        result["_metadata"] = provenance
    return result


def find_spec_url_from_review(review_url, client, html=None):
    soup = BeautifulSoup(client.get_html(review_url) if html is None else html, "html.parser")
    heading = verified_review_heading(soup, review_url)

    def spec_url(link):
        url = urljoin(BASE_DOMAIN, link["href"])
        parts = urlparse(url)
        if (parts.hostname in ("gsmarena.com", "www.gsmarena.com") and SPEC_PATH.fullmatch(parts.path)
                and "review" not in parts.path and "news" not in parts.path and "-phones-" not in parts.path):
            return validate_url(url)
        return None

    primary = {url for link in soup.select(".article-info .meta-link-specs a[href]") if (url := spec_url(link))}
    if len(primary) == 1:
        return primary.pop()
    if len(primary) > 1:
        raise ScrapeError(f"Multiple primary device specifications on {review_url}")

    # Compatibility links must belong to the article, never global maker/sidebar navigation.
    links = soup.select(".article-info a[href], #review-body a[href]")
    labelled = set()
    for link in links:
        text = link.get_text(" ", strip=True).lower()
        if any(word in text for word in ("specification", "specs", "full phone")):
            candidate = spec_url(link)
            if candidate:
                labelled.add(candidate)
    if len(labelled) == 1:
        return labelled.pop()
    if len(labelled) > 1:
        raise ScrapeError(f"No unambiguous specification link on {review_url}")
    if inline_review_specifications(soup, review_url) is not None:
        return review_url
    if " ".join(heading.split()).casefold() in PROSE_ONLY_REVIEW_TITLES:
        raise NonDeviceReview(heading, "no_standalone_specifications", f"verified_prose_only:{heading}")
    tags = {link.get_text(" ", strip=True).casefold() for link in soup.select(".article-tags a")}
    unsupported_tags = tags & {"smart wearables", "wear os"}
    if unsupported_tags:
        # Some wearable articles contain only an inline prose spec summary, not a database sheet.
        raise NonDeviceReview(heading, "no_standalone_specifications",
            f"article_tag:{sorted(unsupported_tags)[0]}; no spec link")
    # A single unambiguous device link is usable. Do not arbitrarily take a sidebar's first phone.
    candidates = {url for link in links if (url := spec_url(link))}
    if len(candidates) == 1:
        return candidates.pop()
    raise ScrapeError(f"No unambiguous specification link on {review_url}")


def scrape_specifications(spec_url, client, html=None):
    soup = BeautifulSoup(client.get_html(spec_url) if html is None else html, "html.parser")
    title = soup.find("h1", class_="specs-phone-name-title")
    spec_list = soup.find("div", id="specs-list")
    if title is None or spec_list is None:
        inline = inline_review_specifications(soup, spec_url)
        if inline is not None:
            return inline
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
    excluded_reviews = state.setdefault("excluded_reviews", {})
    if not isinstance(excluded_reviews, dict) or any(
            not isinstance(info, dict) or not all(isinstance(info.get(key), str) and info[key]
                for key in ("title", "reason", "matched_rule")) for info in excluded_reviews.values()):
        raise ScrapeError(f"Invalid review exclusion checkpoint: {state_file}")
    unresolved = state.setdefault("unresolved_reviews", {})
    if not isinstance(unresolved, dict) or any(not isinstance(info, dict) or not isinstance(info.get("error"), str)
            for info in unresolved.values()):
        raise ScrapeError(f"Invalid unresolved review checkpoint: {state_file}")
    for url in list(unresolved):
        if url in known or url in excluded_reviews:
            del unresolved[url]
    state["complete"] = False
    save_state(state, state_file)
    with nullcontext(client) if client is not None else PoliteHttpClient(base_delay=delay) as http:
        for index, row in enumerate(rows, 1):
            if row["review_url"] in known or row["review_url"] in excluded_reviews:
                continue
            print(f"Specifications [{index}/{len(rows)}]: {row['phone_name']}", flush=True)
            try:
                html = fetch_page(row["review_url"], http)
                url = parse_downloaded_page(find_spec_url_from_review, row["review_url"], http, html=html)
                spec_html = html if url == row["review_url"] else fetch_page(url, http)
                specifications = parse_downloaded_page(scrape_specifications, url, http, html=spec_html)
                provenance = specifications.get("_metadata", {})
                specifications["_metadata"] = dict(row, spec_url=url, **provenance)
                if url == row["review_url"]:
                    specifications["_metadata"].update(spec_source="review_inline", spec_scope="inline_summary")
                all_specs.append(specifications)
                save_to_json(all_specs, output_file)
                known.add(row["review_url"])
                unresolved.pop(row["review_url"], None)
                state.update(last_successful_review_url=row["review_url"], last_error=None)
                save_state(state, state_file)
            except NonDeviceReview as exc:
                excluded_reviews[row["review_url"]] = dict(title=exc.title, reason=exc.reason, matched_rule=exc.rule)
                unresolved.pop(row["review_url"], None)
                state.update(last_error=None)
                save_state(state, state_file)
                print(f"Excluded {row['review_url']}: {exc.reason} ({exc.title})", flush=True)
            except SpecificationParseError as exc:
                unresolved[row["review_url"]] = dict(error=str(exc))
                state.update(last_attempted_review_url=row["review_url"], last_error=str(exc))
                save_state(state, state_file)
                print(f"Unresolved {row['review_url']}: {exc}", flush=True)
            except (ScrapeError, OSError) as exc:
                # A blocking response stops the whole run, instead of hammering the next phone.
                state.update(last_attempted_review_url=row["review_url"], last_error=str(exc))
                save_state(state, state_file)
                raise
    missing = {row["review_url"] for row in rows} - known - set(excluded_reviews)
    if missing:
        state.update(complete=False, unresolved_count=len(missing), last_error=f"Unresolved review specifications: {len(missing)}")
        save_state(state, state_file)
        raise ScrapeError(f"Unresolved review specifications: {len(missing)}")
    if not all_specs:
        raise ScrapeError("No specifications collected")
    save_specs_to_csv(all_specs, output_csv)
    state.update(complete=start_from == 0 and max_phones is None, last_error=None,
                 specifications_count=len(all_specs), excluded_count=len(excluded_reviews), reviews_count=len(rows), unresolved_count=0)
    save_state(state, state_file)
    print(f"Specifications complete: {len(all_specs)} records, {len(excluded_reviews)} explicit exclusions", flush=True)
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
