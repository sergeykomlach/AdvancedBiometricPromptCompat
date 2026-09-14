"""Shared request pacing and bounded retries for both GSMArena scraping stages."""
import json
import math
import os
import random
import time
from dataclasses import dataclass
from email.utils import parsedate_to_datetime
from typing import Optional
from urllib.parse import urlparse

import requests

BASE_DOMAIN = "https://www.gsmarena.com/"
# Keep the configured Chrome version across the default desktop UA profiles.
DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"
DEFAULT_USER_AGENTS = (
    DEFAULT_USER_AGENT,
    DEFAULT_USER_AGENT.replace("Windows NT 10.0; Win64; x64", "X11; Linux x86_64"),
    DEFAULT_USER_AGENT.replace("Windows NT 10.0; Win64; x64", "Macintosh; Intel Mac OS X 10_15_7"),
)
BLOCKING_STATUS_CODES = frozenset((403, 429))


class ScrapeError(RuntimeError):
    pass


def user_agent_pool():
    configured = os.environ.get("GSMARENA_USER_AGENT")
    if configured:
        return (configured,)
    raw_pool = os.environ.get("GSMARENA_USER_AGENTS")
    pool = json.loads(raw_pool) if raw_pool is not None else list(DEFAULT_USER_AGENTS)
    if not isinstance(pool, list) or any(not isinstance(ua, str) or not ua.strip() for ua in pool):
        raise ValueError("GSMARENA_USER_AGENTS must be a JSON array of nonempty UA strings")
    return tuple(dict.fromkeys(pool)) or DEFAULT_USER_AGENTS


def select_user_agent(previous=None, pool=None):
    pool = user_agent_pool() if pool is None else pool
    alternatives = [ua for ua in pool if ua != previous]
    return random.choice(alternatives or pool)


def validate_url(url):
    parsed = urlparse(url)
    if (parsed.scheme != "https" or parsed.hostname not in ("www.gsmarena.com", "gsmarena.com")
            or parsed.username or parsed.password or parsed.port not in (None, 443)):
        raise ScrapeError(f"Unexpected GSMArena URL: {url}")
    return url


@dataclass
class FetchOutcome:
    response: Optional[requests.Response]
    error: Optional[str] = None
    rate_limited: bool = False
    retry_after_seconds: Optional[float] = None
    retryable: bool = False
    reset_session: bool = False


class PoliteHttpClient:
    def __init__(self, base_delay=15.0, max_delay=180.0, timeout=20, *, session=None, session_factory=None):
        minimum = float(os.environ.get("GSMARENA_DELAY_MIN_SECONDS", base_delay))
        maximum = float(os.environ.get("GSMARENA_DELAY_MAX_SECONDS", minimum * 1.5))
        if not all(math.isfinite(x) for x in (minimum, maximum, max_delay)) or minimum < 0 or maximum < minimum or max_delay <= 0:
            raise ValueError("Invalid request delay range")
        self.base_delay, self.delay_max, self.max_delay = minimum, maximum, max_delay
        self.timeout = timeout
        self._session_factory = session_factory if session_factory is not None else requests.Session
        self._user_agents = user_agent_pool()
        self.default_headers = {
            "User-Agent": select_user_agent(pool=self._user_agents),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
        }
        self.session = session if session is not None else self._session_factory()
        self.session.headers.update(self.default_headers)

    def _reset_session(self, request_headers):
        # Explicit per-request UA is pinned just like GSMARENA_USER_AGENT.
        pinned = next((value for key, value in (request_headers or {}).items() if key.lower() == "user-agent"), None)
        previous = self.default_headers["User-Agent"]
        next_ua = pinned if pinned is not None else select_user_agent(previous, self._user_agents)
        self.session.close()
        self.session = self._session_factory()
        self.default_headers["User-Agent"] = next_ua
        self.session.headers.update(self.default_headers)

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.session.close()

    def sleep(self, seconds):
        if seconds > 0:
            time.sleep(seconds)

    @staticmethod
    def _parse_retry_after(value):
        if not value:
            return None
        try:
            seconds = float(value) if value.strip().isdigit() else parsedate_to_datetime(value).timestamp() - time.time()
            return max(0.0, seconds) if math.isfinite(seconds) else None
        except (ValueError, TypeError, OverflowError):
            return None

    def get_once(self, url, headers=None, minimum_wait=0):
        validate_url(url)
        # Pacing covers EVERY request: review -> specs, errors and retries.
        self.sleep(max(minimum_wait, random.uniform(self.base_delay, self.delay_max)))
        try:
            response = self.session.get(url, headers=headers, timeout=self.timeout, allow_redirects=False)
        except (requests.Timeout, requests.ConnectionError) as exc:
            return FetchOutcome(None, str(exc), retryable=True)
        except requests.RequestException as exc:
            return FetchOutcome(None, str(exc))
        status = response.status_code
        retry_after = self._parse_retry_after(response.headers.get("Retry-After"))
        content = response.text[:200_000].lower()
        if (response.headers.get("cf-mitigated", "").lower() == "challenge"
                or any(marker in content for marker in ("/cdn-cgi/challenge-platform/", "g-recaptcha", "hcaptcha.com/", "<title>just a moment"))):
            return FetchOutcome(response, "Access challenge received; stop and retry later")
        if status in BLOCKING_STATUS_CODES:
            return FetchOutcome(response, f"HTTP {status}", status == 429, retry_after,
                                retryable=True, reset_session=True)
        if "<title>access denied" in content:
            return FetchOutcome(response, "Access denied without a retryable blocking status")
        if status in (500, 502, 503, 504):
            return FetchOutcome(response, f"HTTP {status}", retry_after_seconds=retry_after, retryable=True)
        if status != 200:
            # Redirects can lead to challenge/login/foreign endpoints; do not follow implicitly.
            return FetchOutcome(response, f"HTTP {status}; stopped without retry")
        return FetchOutcome(response)

    def get_with_retry(self, url, headers=None, max_attempts=5):
        if not isinstance(max_attempts, int) or not 1 <= max_attempts <= 5:
            raise ValueError("max_attempts must be an integer from 1 to 5 (including the first request)")
        wait = 0.0
        for attempt in range(max_attempts):
            outcome = self.get_once(url, headers, minimum_wait=wait)
            if not outcome.error or not outcome.retryable or attempt + 1 == max_attempts:
                return outcome
            # Never shorten Retry-After. Long server cooldowns stop this run, instead of sleeping
            # for hours or sending another request before the server's requested time.
            if outcome.retry_after_seconds is not None and outcome.retry_after_seconds > self.max_delay:
                outcome.error += f"; Retry-After={outcome.retry_after_seconds:.0f}s exceeds this run's wait budget; resume later"
                return outcome
            backoff = min(max(self.base_delay, 1.0) * 2 ** (attempt + 1), self.max_delay)
            wait = max(backoff + random.uniform(0, min(5.0, backoff * 0.2)), outcome.retry_after_seconds or 0)
            if outcome.reset_session:
                outcome.response.close()
                self._reset_session(headers)
            action = "; fresh session" if outcome.reset_session else ""
            print(f"Request failed ({outcome.error}){action}; attempt {attempt + 2}/{max_attempts} no sooner than {wait:.1f}s")

    def get_html(self, url):
        outcome = self.get_with_retry(url)
        if outcome.error or outcome.response is None:
            raise ScrapeError(f"{url}: {outcome.error or 'No response'}")
        return outcome.response.content
