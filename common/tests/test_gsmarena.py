import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import requests
import fixed_phone_review_url_scraper as reviews
import fixed_phone_specs_scraper as specs
import gsmarena_scrape as pipeline
from gsmarena_http import PoliteHttpClient, ScrapeError, select_user_agent
from gsmarena_io import load_json, save_json

REVIEW_URL = "https://www.gsmarena.com/phone-review-1234.php"
SPEC_URL = "https://www.gsmarena.com/phone-12345.php"
SPEC_HTML = b'''<h1 class="specs-phone-name-title">Phone</h1><div id="specs-list">
<table><tr><th>Network</th><td class="ttl">Technology</td><td class="nfo">GSM</td></tr></table>
<table><tr><th>Features</th><td class="ttl">Sensors</td><td class="nfo">Fingerprint (side-mounted)</td></tr></table>
</div>'''


def index_html(url=REVIEW_URL, next_page=None):
    next_link = f'<a href="reviews.php3?iPage={next_page}">Next</a>' if next_page else ""
    return f'<div class="review-item"><h3><a href="{url}">Phone</a></h3></div>{next_link}'.encode()


def response(status=200, body=b"<html>ok</html>", **headers):
    value = requests.Response()
    value.status_code = status
    value._content = body
    value.headers.update(headers)
    return value


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {}, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)
        self.session = Mock(headers={})
        self.sleeper = patch("gsmarena_http.time.sleep").start()
        self.addCleanup(patch.stopall)

    def client(self, *responses):
        self.session.get.side_effect = responses
        return PoliteHttpClient(base_delay=10, session=self.session, session_factory=lambda: self.session)

    def test_every_request_including_review_to_specs_is_paced(self):
        http = self.client(response(), response())
        http.get_html(REVIEW_URL)
        http.get_html(SPEC_URL)
        self.assertEqual(2, self.sleeper.call_count)
        for call in self.sleeper.call_args_list:
            self.assertTrue(10 <= call.args[0] <= 15)
        self.assertFalse(self.session.get.call_args.kwargs["allow_redirects"])

    def test_retry_after_is_a_minimum_wait(self):
        http = self.client(response(429, **{"Retry-After": "90"}), response())
        self.assertIsNone(http.get_with_retry(REVIEW_URL).error)
        self.assertGreaterEqual(self.sleeper.call_args_list[1].args[0], 90)

    def test_long_retry_after_stops_without_early_retry(self):
        http = self.client(response(429, **{"Retry-After": "3600"}))
        outcome = http.get_with_retry(REVIEW_URL)
        self.assertIn("3600", outcome.error)
        self.assertEqual(1, self.session.get.call_count)
        self.assertEqual(1, self.sleeper.call_count)

    def test_retry_after_http_date_and_invalid_values(self):
        with patch("gsmarena_http.time.time", return_value=0):
            self.assertEqual(120, PoliteHttpClient._parse_retry_after("Thu, 01 Jan 1970 00:02:00 GMT"))
        self.assertIsNone(PoliteHttpClient._parse_retry_after("not-a-date"))

    def test_no_sleep_after_last_failed_attempt(self):
        http = self.client(*(response(429) for _ in range(5)))
        self.assertTrue(http.get_with_retry(REVIEW_URL).error)
        self.assertEqual(5, self.session.get.call_count)
        self.assertEqual(5, self.sleeper.call_count)

    def test_authentication_permanent_errors_and_redirect_are_not_retried(self):
        for status in (401, 404, 410, 451, 302):
            with self.subTest(status=status):
                self.session.reset_mock()
                http = self.client(response(status))
                with self.assertRaises(ScrapeError):
                    http.get_html(REVIEW_URL)
                self.assertEqual(1, self.session.get.call_count)

    def test_challenge_even_with_200_or_503_stops(self):
        for status in (200, 403, 429, 503):
            with self.subTest(status=status):
                self.session.reset_mock()
                http = self.client(response(status, b"<title>Just a moment...</title>"))
                self.assertIn("challenge", http.get_with_retry(REVIEW_URL).error)
                self.assertEqual(1, self.session.get.call_count)

    def test_temporary_failure_retries_but_keeps_one_user_agent(self):
        with patch.dict(os.environ, {"GSMARENA_USER_AGENTS": '["UA A", "UA B"]'}), patch("gsmarena_http.random.choice", return_value="UA B") as choose:
            http = self.client(requests.Timeout("timeout"), response(503), response())
            self.assertIsNone(http.get_with_retry(REVIEW_URL).error)
            self.assertEqual(1, choose.call_count)
            self.assertEqual("UA B", self.session.headers["User-Agent"])

    def test_user_agent_override_and_pool_validation(self):
        with patch.dict(os.environ, {"GSMARENA_USER_AGENT": "Pinned UA", "GSMARENA_USER_AGENTS": "invalid"}):
            self.assertEqual("Pinned UA", select_user_agent())
        with patch.dict(os.environ, {"GSMARENA_USER_AGENTS": '[""]'}):
            with self.assertRaises(ValueError):
                select_user_agent()

    def test_foreign_url_never_sends_a_request(self):
        http = self.client()
        with self.assertRaises(ScrapeError):
            http.get_html("https://example.org/phone-12345.php")
        self.session.get.assert_not_called()

    def test_owned_session_is_closed_on_failure(self):
        with self.assertRaises(ScrapeError):
            with self.client(response(404)) as http:
                http.get_html(REVIEW_URL)
        self.session.close.assert_called_once()

    def test_plain_403_renews_session_and_changes_ua_then_succeeds(self):
        first = Mock(headers={}, cookies={"blocked": "old"})
        second = Mock(headers={}, cookies={})
        first.get.return_value = response(403, b"<title>Access denied</title>")
        second.get.return_value = response()
        factory = Mock(return_value=second)
        with patch.dict(os.environ, {"GSMARENA_USER_AGENTS": '["UA A", "UA B"]'}), patch("gsmarena_http.random.choice", side_effect=lambda values: values[0]):
            with PoliteHttpClient(base_delay=10, session=first, session_factory=factory) as http:
                self.assertIsNone(http.get_with_retry(REVIEW_URL).error)
                self.assertEqual("UA A", first.headers["User-Agent"])
                self.assertEqual("UA B", second.headers["User-Agent"])
                self.assertEqual({}, second.cookies)
                self.assertIs(http.session, second)
                # Successful requests keep the recovered session for the following stage.
                http.get_html(SPEC_URL)
                factory.assert_called_once()
        first.close.assert_called_once()
        second.close.assert_called_once()

    def test_repeated_blocks_have_one_shared_five_attempt_budget(self):
        sessions = [Mock(headers={}, get=Mock(return_value=response(status)))
                    for status in (403, 429, 403, 429, 403)]
        factory = Mock(side_effect=sessions[1:])
        with PoliteHttpClient(base_delay=10, session=sessions[0], session_factory=factory) as http:
            self.assertTrue(http.get_with_retry(REVIEW_URL).error)
            self.assertEqual(4, factory.call_count)
            self.assertEqual(5, sum(session.get.call_count for session in sessions))
            self.assertEqual(5, self.sleeper.call_count)
            for first, second in zip(sessions, sessions[1:]):
                self.assertNotEqual(first.headers["User-Agent"], second.headers["User-Agent"])
        for session in sessions:
            session.close.assert_called_once()

    def test_long_retry_after_on_403_does_not_reset_or_retry(self):
        self.session.get.return_value = response(403, **{"Retry-After": "3600"})
        factory = Mock()
        http = PoliteHttpClient(base_delay=10, session=self.session, session_factory=factory)
        self.assertIn("3600", http.get_with_retry(REVIEW_URL).error)
        factory.assert_not_called()
        self.session.close.assert_not_called()
        self.assertEqual(1, self.session.get.call_count)

    def test_challenge_header_prevents_rotation(self):
        self.session.get.return_value = response(403, **{"cf-mitigated": "challenge"})
        factory = Mock()
        http = PoliteHttpClient(base_delay=10, session=self.session, session_factory=factory)
        self.assertIn("challenge", http.get_with_retry(REVIEW_URL).error)
        factory.assert_not_called()
        self.assertEqual(1, self.session.get.call_count)

    def test_pinned_ua_stays_pinned_when_session_is_replaced(self):
        for per_request in (False, True):
            with self.subTest(per_request=per_request):
                first = Mock(headers={}, get=Mock(return_value=response(403)))
                second = Mock(headers={}, get=Mock(return_value=response()))
                env = {} if per_request else {"GSMARENA_USER_AGENT": "Pinned UA"}
                headers = {"user-agent": "Pinned UA"} if per_request else None
                with patch.dict(os.environ, env):
                    http = PoliteHttpClient(base_delay=10, session=first, session_factory=lambda: second)
                    self.assertIsNone(http.get_with_retry(REVIEW_URL, headers=headers).error)
                    self.assertEqual("Pinned UA", second.headers["User-Agent"])

    def test_single_ua_pool_can_still_reset_a_blocked_session(self):
        first = Mock(headers={}, get=Mock(return_value=response(403)))
        second = Mock(headers={}, get=Mock(return_value=response()))
        with patch.dict(os.environ, {"GSMARENA_USER_AGENTS": '["UA A", "UA A"]'}):
            http = PoliteHttpClient(base_delay=10, session=first, session_factory=lambda: second)
            self.assertIsNone(http.get_with_retry(REVIEW_URL).error)
        self.assertEqual("UA A", second.headers["User-Agent"])
        first.close.assert_called_once()

    def test_max_attempts_cannot_exceed_five(self):
        http = self.client()
        for attempts in (0, 6, 1.5):
            with self.assertRaises(ValueError):
                http.get_with_retry(REVIEW_URL, max_attempts=attempts)
        self.session.get.assert_not_called()

    def test_single_attempt_does_not_reset_after_its_failure(self):
        self.session.get.return_value = response(403)
        factory = Mock()
        http = PoliteHttpClient(base_delay=10, session=self.session, session_factory=factory)
        self.assertTrue(http.get_with_retry(REVIEW_URL, max_attempts=1).error)
        factory.assert_not_called()
        self.assertEqual(1, self.sleeper.call_count)


class ScraperTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)

    def record(self):
        return {"phone_name": "Phone", "Features": {"Sensors": "Fingerprint"},
                "_metadata": {"review_url": REVIEW_URL, "spec_url": SPEC_URL, "phone_name": "Phone"}}

    def test_specs_use_header_inside_each_table(self):
        result = specs.scrape_specifications(SPEC_URL, Mock(get_html=Mock(return_value=SPEC_HTML)))
        self.assertEqual({"Technology": "GSM"}, result["Network"])
        self.assertEqual({"Sensors": "Fingerprint (side-mounted)"}, result["Features"])

    def test_unrecognized_200_pages_are_errors_not_empty_success(self):
        for call, url in ((specs.scrape_specifications, SPEC_URL), (reviews.scrape_single_page, REVIEW_URL)):
            with self.assertRaises(ScrapeError):
                call(url, Mock(get_html=Mock(return_value=b"<html>Something changed</html>")))

    def test_external_spec_link_is_not_followed(self):
        http = Mock(get_html=Mock(return_value=b'<a href="https://example.org/phone-12345.php">Specifications</a>'))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, http)

    def test_ambiguous_sidebar_links_do_not_choose_an_arbitrary_phone(self):
        http = Mock(get_html=Mock(return_value=b'<a href="a-12345.php">A</a><a href="b-23456.php">B</a>'))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, http)

    def test_pipeline_reuses_client_and_second_run_skips_known_specs(self):
        http = Mock(get_html=Mock(side_effect=[index_html(), f'<a href="{SPEC_URL}">Specifications</a>'.encode(), SPEC_HTML]))
        self.assertEqual(1, len(pipeline.run(self.root, http)))
        self.assertEqual(3, http.get_html.call_count)
        second = Mock(get_html=Mock(return_value=index_html(next_page=2)))
        self.assertEqual(1, len(pipeline.run(self.root, second)))
        self.assertEqual(1, second.get_html.call_count)

    def test_blocked_review_stops_before_specs_and_preserves_existing_json(self):
        output = self.root / "gsmarena_specifications.json"
        save_json([self.record()], output)
        before = output.read_bytes()
        http = Mock(get_html=Mock(side_effect=ScrapeError("HTTP 403")))
        with self.assertRaises(ScrapeError):
            pipeline.run(self.root, http)
        self.assertEqual(before, output.read_bytes())
        self.assertEqual(1, http.get_html.call_count)

    def test_blocked_specs_stop_instead_of_requesting_the_next_phone(self):
        source = self.root / "reviews.csv"
        reviews.save_to_csv([{"phone_name": "A", "review_url": REVIEW_URL},
                             {"phone_name": "B", "review_url": "https://www.gsmarena.com/b-review-2222.php"}], source)
        http = Mock(get_html=Mock(side_effect=ScrapeError("HTTP 429")))
        with self.assertRaises(ScrapeError):
            specs.scrape_specs_from_csv(source, output_file=self.root / "out.json",
                                       state_file=self.root / "state.json", client=http)
        self.assertEqual(1, http.get_html.call_count)

    def test_interrupted_backfill_does_not_stop_at_its_own_first_page(self):
        out = self.root / "reviews.json"
        state = self.root / "state.json"
        save_json([{"phone_name": "Phone", "review_url": REVIEW_URL}], out)
        save_json({"complete": False, "last_successful_page": 2}, state)
        second_url = "https://www.gsmarena.com/b-review-2222.php"
        http = Mock(get_html=Mock(side_effect=[index_html(next_page=2), index_html(second_url)]))
        result = reviews.scrape_gsmarena_reviews(json_output=out, state_file=state, client=http)
        self.assertEqual(2, len(result))
        self.assertIn("iPage=1", http.get_html.call_args_list[0].args[0])
        self.assertTrue(load_json(state, dict)["complete"])

    def test_capped_run_is_not_marked_complete(self):
        state = self.root / "state.json"
        reviews.scrape_gsmarena_reviews(json_output=self.root / "out.json", state_file=state,
                                       max_pages=1, client=Mock(get_html=Mock(return_value=index_html(next_page=2))))
        self.assertFalse(load_json(state, dict)["complete"])

    def test_corrupt_checkpoint_is_not_treated_as_empty(self):
        output = self.root / "specs.json"
        output.write_text("truncated-json", encoding="utf-8")
        with self.assertRaises(ScrapeError):
            specs.load_existing_specs(output)
        self.assertEqual("truncated-json", output.read_text())

    def test_atomic_write_failure_preserves_previous_checkpoint(self):
        output = self.root / "specs.json"
        save_json([self.record()], output)
        before = output.read_bytes()
        with patch("gsmarena_io.os.replace", side_effect=OSError("disk error")):
            with self.assertRaises(OSError):
                save_json([], output)
        self.assertEqual(before, output.read_bytes())
        self.assertEqual([output], list(self.root.iterdir()))

    def test_legacy_records_with_metadata_name_remain_readable(self):
        old = self.record()
        del old["phone_name"]
        output = self.root / "specs.json"
        save_json([old], output)
        self.assertEqual([old], specs.load_existing_specs(output))

    def test_main_returns_nonzero_on_failure_even_with_old_output(self):
        with patch("gsmarena_scrape.run", side_effect=ScrapeError("HTTP 403")):
            self.assertEqual(1, pipeline.main())


if __name__ == "__main__":
    unittest.main()
