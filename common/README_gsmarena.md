# GSMArena device metadata refresh

`gsmarenaScrapeAndCopy` runs `gsmarena_scrape.py` from `common/`. Both stages use one
`requests.Session` across successful requests, with a random pause before **every** HTTP request.
The old `phone_*` filenames are compatibility entry points to the maintained `fixed_*` modules.

Run manually from `common/`:

```powershell
python -X utf8 gsmarena_scrape.py
```

Defaults: delay uniformly distributed between 15 and 22.5 seconds; at most **5 total attempts
per URL** (the first request plus up to 4 retries), shared across blocking responses and
temporary failures. HTTP 403/429 reset the session before retrying; timeouts, connection
failures and transient 5xx responses retry with backoff in the current session. A single-stage
review run defaults to 12–18 seconds. Set delay bounds for either entry point:

```powershell
$env:GSMARENA_DELAY_MIN_SECONDS = '20'
$env:GSMARENA_DELAY_MAX_SECONDS = '35'
```

The default pool contains Windows, Linux and macOS desktop UAs using the existing Chrome/153
version. Pin a UA without editing Python files:

```powershell
$env:GSMARENA_USER_AGENT = '<your User-Agent>'
```

Alternatively set `GSMARENA_USER_AGENTS` to a JSON array of UA strings; one entry is chosen
randomly at session creation. On plain HTTP 403/429, the old session is closed and replaced
with a fresh cookie jar and a different UA from that pool (when at least two distinct UAs
are available). The recovered session continues across subsequent requests and stages.
`GSMARENA_USER_AGENT` or an explicit per-request User-Agent pins the UA even when the session
is reset. A one-entry pool also keeps the same UA. The retry counter is never reset along
with the session, and no extra session is created after the last failed attempt.
UA selection does not guarantee acceptance by the site's protection.

`Retry-After` is a minimum wait, including HTTP-date values. If it exceeds the 180-second
wait budget, the run stops before session reset instead of retrying early. CAPTCHA/JS-challenge
pages (including those delivered with HTTP 403/429/503), authentication errors such as 401,
permanent errors such as 404/410/451, and redirects still stop the run. A generic HTTP 403
"Access denied" page without challenge markers is eligible for the bounded session retries.

JSON/CSV/state writes use temporary files and replacement. Successful phones are saved
incrementally, and already saved reviews/specifications avoid redundant requests. Review
refresh starts at page 1 so a historical page checkpoint cannot hide newly published reviews.
An interrupted index backfill does not treat its partial first page as a completed dataset.

Network/parsing/checkpoint errors exit with a nonzero code. Gradle therefore stops before
copying partial or stale output into device assets. It also validates the output structure
and replaces the asset from a temporary file. The JSON in `common/` remains a checkpoint
on failure; only a completed run is eligible for copying.

Before scraping, the pipeline recovers any records missing from the working specification
checkpoint from `src/main/assets/devices/specifications.json`. Existing checkpoint records
win on overlap, so an interrupted or older checkpoint cannot discard published devices.
Duplicate review URLs are rejected; multiple reviews may legitimately share a specification URL.

Some index entries are software reviews, roundups, buying guides or multi-device articles.
`gsmarena_review_policy.py` contains explicit title rules for these exclusions. The fetched
page must first have the expected canonical URL, article heading and nonempty review body.
Exclusions, their fetched titles and matched rules are recorded in `gsmarena_specs_state.json`;
they are not fabricated specification records. Unknown/ambiguous single-device articles still
stop the run. Device discovery prefers the article's primary `meta-link-specs` link before
compatibility fallbacks, and accepts legacy device IDs with fewer than four digits.

An article may supply its own structured specification summary without a separate device
database page. A single model-matching `article-blurb` list with at least three labelled
fields is retained under `Review specifications`; metadata explicitly records
`spec_source: review_inline`, `spec_scope: inline_summary`, and the actual review source URL.
These summaries retain only source fields: missing Sensors/Models or other fields are not
invented. Base-model summaries cannot populate variant-model reviews. Wearable articles
without either a usable summary or a standalone sheet have an explicit exclusion reason.
Unlabelled specification bullets remain verbatim as `Feature N`. Multiple summaries are
usable only when exactly one matches the article model. Per-article typo/brand corrections
are restricted to verified canonical URLs and recorded in metadata. Android sensor lookup
keeps its existing fallback when an inline summary lacks usable explicit Sensors data.

The specification state becomes `complete: true` only after every review in a full run has
either a stored specification or an explicit exclusion and the CSV export succeeds. The review
index must also be complete before the combined pipeline can proceed to specifications.
Parsing failures on successfully downloaded pages accumulate under `unresolved_reviews`;
other reviews can continue, but any unresolved entry prevents publication. HTTP, challenge,
cache-integrity and write failures still stop the run immediately.

Optionally set `GSMARENA_PAGE_CACHE_DIR` to a dedicated directory for one refresh. Successful
review/specification responses can then be reused for parser repairs for up to six hours,
with URL and SHA-256 checks and atomic writes. The review index is always fetched live.
Use a fresh directory for an independent refresh; invalid cache contents are errors.

For parser troubleshooting, optionally set `GSMARENA_DIAGNOSTICS_DIR` to a local directory.
The last HTTP response is retained as `last_response.html` and `last_response.json` (URL,
status and error; no cookie/header capture). This allows inspecting the actual failed page
before retrying. CAPTCHA/challenge and HTTP error handling remain unchanged.

Unrecognized pages and ambiguous specification links are errors: a sidebar phone must not
be silently attributed to the current review. Inspect such a reported URL before retrying.
Legacy records remain readable (including records named only in `_metadata`); their old
category assignments are not silently rewritten. The corrected parser uses each table's
own header for newly fetched specifications. A historical data rebuild is a separate operation.

Offline verification from the repository root:

```powershell
python -X utf8 -m unittest discover -s common/tests -p test_gsmarena.py
```

The Gradle refresh remains explicitly gated by `refreshDeviceAssets`; normal builds do not
contact GSMArena. Tests use fixture HTML and mocked HTTP responses, with no live scraping.

References: [Requests sessions](https://requests.readthedocs.io/en/latest/user/advanced/#session-objects),
[HTTP Retry-After semantics](https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after).
