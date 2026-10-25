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
    def test_optional_diagnostics_keep_response_for_parser_failure_investigation(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {"GSMARENA_DIAGNOSTICS_DIR": directory}):
            session = Mock(headers={})
            session.get.return_value = response(body=b"<html>Actual review markup</html>")
            with patch("gsmarena_http.time.sleep"):
                self.assertEqual(b"<html>Actual review markup</html>",
                    PoliteHttpClient(session=session).get_html(REVIEW_URL))
            self.assertEqual(b"<html>Actual review markup</html>", (Path(directory) / "last_response.html").read_bytes())
            self.assertEqual(REVIEW_URL, json.loads((Path(directory) / "last_response.json").read_text())["url"])

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

    def article(self, title, extra="", canonical=REVIEW_URL, body="Recognized article content."):
        return (f'<link rel="canonical" href="{canonical}"><div class="article-info">'
                f'<h1 class="article-info-name">{title}</h1>{extra}</div>'
                f'<div id="review-body">{body}</div>').encode()

    def test_honor_magic_vs_is_not_a_comparison_article(self):
        for title in ("Honor Magic Vs review", "Honor Magic Vs hands-on review", "Honor Magic Vs 5G review",
                      "Honor Magic Vs Pro review", "MWC 2023: Honor Magic Vs Pro hands-on: First look"):
            html = self.article(title, f'<li class="meta-link-specs"><a href="{SPEC_URL}">Honor Magic Vs</a></li>')
            self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL,
                Mock(get_html=Mock(return_value=html))))

    def test_renamed_comparison_is_not_a_single_primary_phone(self):
        html = self.article("Apple iPhone 8 Plus vs. Samsung Galaxy Note8 blind camera shootout",
            f'<li class="meta-link-specs"><a href="{SPEC_URL}">First phone</a></li>')
        with self.assertRaisesRegex(ScrapeError, "comparison_article"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_event_prefix_keeps_explicit_multi_device_classification(self):
        html = self.article("Samsung at IFA 2017: Gear Sport, Fit2 Pro, IconX (2018) hands-on review")
        with self.assertRaisesRegex(ScrapeError, "multi_device_article"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_single_device_at_an_event_remains_eligible(self):
        html = self.article("Samsung at IFA 2017: Gear Sport hands-on review",
            f'<li class="meta-link-specs"><a href="{SPEC_URL}">Gear Sport</a></li>')
        self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html))))

    def test_wearable_without_standalone_specsheet_is_accounted_for(self):
        for tag in ("Smart wearables", "Wear OS"):
            with self.subTest(tag=tag):
                html = self.article("Watch review", f'<div class="article-tags"><a><b>{tag}</b></a></div>',
                    body=f'<a href="{SPEC_URL}">Another device mentioned in prose</a>')
                with self.assertRaisesRegex(ScrapeError, "no_standalone_specifications"):
                    specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_wearable_with_a_primary_specsheet_remains_eligible(self):
        html = self.article("Watch review", '<div class="article-tags"><a>Smart wearables</a></div>'
            f'<li class="meta-link-specs"><a href="{SPEC_URL}">Watch</a></li>')
        self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html))))

    def inline_summary(self, name="MOQI i7s"):
        return (f'<h3 class="article-blurb-title">{name} specs</h3><ul class="article-blurb">'
                '<li><b>Body:</b>Plastic, 280 grams</li><li><b>OS:</b>Android 8.1</li>'
                '<li><b>Battery:</b>6000 mAh</li></ul>')

    def test_inline_spec_summary_is_collected_without_another_http_request(self):
        source = self.root / "reviews.csv"
        reviews.save_to_csv([{"phone_name": "MOQI i7s review", "review_url": REVIEW_URL}], source)
        html = self.article("MOQI i7s Android game console review", body=self.inline_summary())
        http = Mock(get_html=Mock(return_value=html))
        result = specs.scrape_specs_from_csv(source, output_file=self.root / "specs.json",
            output_csv=self.root / "specs.csv", state_file=self.root / "state.json", client=http)
        self.assertEqual(1, http.get_html.call_count)
        self.assertEqual("MOQI i7s", result[0]["phone_name"])
        self.assertEqual("Android 8.1", result[0]["Review specifications"]["OS"])
        self.assertEqual("review_inline", result[0]["_metadata"]["spec_source"])
        self.assertEqual("inline_summary", result[0]["_metadata"]["spec_scope"])
        self.assertEqual(REVIEW_URL, result[0]["_metadata"]["spec_url"])

    def test_two_inline_device_summaries_remain_ambiguous(self):
        html = self.article("Device A and Device B review", body=self.inline_summary("Device A") + self.inline_summary("Device B"))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_inline_base_model_cannot_supply_a_variant_review(self):
        html = self.article("Samsung Galaxy S6 edge review", body=self.inline_summary("Samsung Galaxy S6"))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_inline_key_features_accepts_colon_outside_bold_label(self):
        body = ('<h3 class="article-blurb-title">Apple TV 4K Key Features</h3>'
            '<ul class="article-blurb"><li><b>Chipset</b>: Apple A10X</li>'
            '<li><b>Memory:</b>3GB RAM</li><li><b>Video:</b>4K HDR</li></ul>')
        html = self.article("Apple TV 4K review", body=body)
        parsed = specs.scrape_specifications(REVIEW_URL, Mock(), html=html)
        self.assertEqual("Apple TV 4K", parsed["phone_name"])
        self.assertEqual("Apple A10X", parsed["Review specifications"]["Chipset"])

    def test_inline_review_tagline_does_not_change_the_model(self):
        html = self.article("Doogee Mix review: Dress to impress", body=self.inline_summary("Doogee Mix"))
        self.assertEqual("Doogee Mix", specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["phone_name"])

    def test_inline_selects_one_exact_model_among_multiple_summaries(self):
        body = (self.inline_summary("Moto Z Droid Edition") + self.inline_summary("Moto Z Force Droid Edition")).replace(" specs</h3>", " at a glance:</h3>")
        html = self.article("Moto Z Force Droid Edition hands-on: First look", body=body)
        self.assertEqual("Moto Z Force Droid Edition", specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["phone_name"])

    def test_inline_nested_list_wrapper_and_unlabelled_facts_are_preserved(self):
        body = ('<h3 class="article-blurb-title">Sony Xperia X Performance at a glance:</h3>'
            '<ul class="article-blurb"><ul><li>5-inch LCD display</li><li><b>Front camera</b>: 13MP</li>'
            '<li>No fingerprint sensor on US model</li></ul></ul>')
        html = self.article("Sony Xperia X Performance hands-on: Second encounter", body=body)
        fields = specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["Review specifications"]
        self.assertEqual("5-inch LCD display", fields["Feature 1"])
        self.assertEqual("13MP", fields["Front camera"])
        self.assertEqual("No fingerprint sensor on US model", fields["Feature 3"])
        self.assertNotIn("Sensors", fields)

    def test_verified_inline_model_corrections_are_limited_to_exact_source(self):
        cases = [
            ("https://www.gsmarena.com/huawei_mate_book_mwc_2016-review-1389.php", "Huawei Mate Book", "Huawei Media Book", "Huawei Mate Book"),
            ("https://www.gsmarena.com/letv_le_1s_hands_on-review-1373.php", "LeTV (LeEco) Le 1s", "LeTV Le 1s", "LeTV Le 1s"),
            ("https://www.gsmarena.com/pebble_time-review-1265.php", "Pebble Time", "Key features", "Pebble Time"),
            ("https://www.gsmarena.com/htc_re_camera-review-1179.php", "HTC Re camera", "Key features", "HTC Re camera"),
            ("https://www.gsmarena.com/samsung_galaxy_note_ii_n7100-review-806.php", "Samsung Galaxy Note II", "Samsung Galaxy Note  II N7100", "Samsung Galaxy Note II N7100"),
        ]
        for url, heading, summary, expected in cases:
            with self.subTest(url=url):
                html = self.article(heading + " hands-on: First look", canonical=url, body=self.inline_summary(summary))
                parsed = specs.scrape_specifications(url, Mock(), html=html)
                self.assertEqual(expected, parsed["phone_name"])
                wrong_source = self.article(heading + " hands-on: First look", body=self.inline_summary(summary))
                with self.assertRaises(ScrapeError):
                    specs.scrape_specifications(REVIEW_URL, Mock(), html=wrong_source)

    def test_verified_editorial_title_variants_are_accounted_for(self):
        for title in ("Our mobile chipset guide: The 2017 edition", "Introducing time-saver reviews",
            "SmartSensor Image Stabilization and Super VOOC: Oppo at MWC 2016", "Amazon Kindle Fire HD hands-on: First look",
            "Pop Up, Start, First and Idol 3C hands-on: Alcatel Day 2", "GSMArena.com turns 15: A video trip down memory lane",
            "Samsung Galaxy S6 and S6 edge video review", "Charge test: Battle of the fast chargers",
            "Galaxy shootout: Blind camera test", "Samsung Galaxy S6 and S6 edge preview: Closer encounter",
            "MWC 2015: HTC One M9, Grip hands-on", "MWC 2015: Samsung Galaxy S6 and S6 edge hands-on",
            "CES 2014: Various brands overview", "Samsung Galaxy S III Jelly Bean preview: First look",
            "GSMArena feature labs: The tests", "Fashion phones overview: Man’s best friend",
            "Mobile phones go scalp hunting: The Red List", "MWC 2010: Microsoft Windows Phone 7 Series",
            "Sony Ericsson Media Go review: First look", "MWC 2009: The top 7 things we wish we took home",
            "Samsung G800 launch event: A night at the museum", "GSMArena feature lab: Enter new goodies",
            "Dual SIM review: Mobiles go two-in-one"):
            with self.subTest(title=title), self.assertRaisesRegex(ScrapeError, "Non-device review"):
                specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=self.article(title))))

    def test_editorial_device_links_are_not_exported_as_the_article_device(self):
        html = self.article("MWC 2009: The top 7 things we wish we took home",
            body='<a href="http://www.gsmarena.com/samsung_i8910_omnia_hd-2691.php">Specifications</a>')
        with self.assertRaisesRegex(ScrapeError, "editorial"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_legacy_unclassed_summary_requires_named_spec_heading(self):
        body = self.inline_summary("Samsung Galaxy A3").replace(' class="article-blurb-title"', '').replace(' class="article-blurb"', '')
        html = self.article("Samsung Galaxy A3 preview: First look", body=body)
        self.assertEqual("Samsung Galaxy A3", specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["phone_name"])
        unrelated = self.article("Samsung Galaxy A3 preview", body=body.replace("Samsung Galaxy A3 specs", "Samsung Galaxy A3"))
        with self.assertRaises(ScrapeError):
            specs.scrape_specifications(REVIEW_URL, Mock(), html=unrelated)

    def test_omitted_li_end_tags_do_not_merge_adjacent_spec_fields(self):
        heading = '<h3>Samsung I9300 Galaxy S III at a glance:</h3>'
        for source_heading in (heading, '<p>' + heading + '</p>'):
            with self.subTest(heading=source_heading):
                body = source_heading + '<ul><li><b>General:</b>GSM<li><b>Display:</b>AMOLED<li><b>Sensors:</b>Fingerprint</ul>'
                html = self.article("Samsung I9300 Galaxy S III preview: First look", body=body)
                fields = specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["Review specifications"]
                self.assertEqual({"General": "GSM", "Display": "AMOLED", "Sensors": "Fingerprint"}, fields)

    def test_real_nested_notes_remain_with_their_own_specification(self):
        body = ('<h3 class="article-blurb-title">Phone specs</h3><ul class="article-blurb">'
            '<li><b>Sensors:</b>Fingerprint<ul><li>Not available in US model</li></ul></li>'
            '<li><b>Display:</b>AMOLED</li><li><b>Battery:</b>4000 mAh</li></ul>')
        html = self.article("Phone review", body=body)
        fields = specs.scrape_specifications(REVIEW_URL, Mock(), html=html)["Review specifications"]
        self.assertEqual("Fingerprint Not available in US model", fields["Sensors"])
        self.assertEqual("AMOLED", fields["Display"])
        self.assertEqual(3, len(fields))

    def test_os_article_subtitles_preserve_single_phone_reviews(self):
        for title in ("Windows 10 for phones preview: First look", "Windows Phone 8.1 review: Major league"):
            with self.assertRaisesRegex(ScrapeError, "software_article"):
                specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=self.article(title))))
        phone = self.article("HTC Windows Phone 8X review: First look", f'<li class="meta-link-specs"><a href="{SPEC_URL}">HTC</a></li>')
        self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=phone))))

    def test_verified_prose_only_devices_are_explicitly_accounted_for(self):
        for title in ("The Card phone review: The future is modular", "Zopo Speed 8 hands-on: Zopo at MWC 2016",
                      "HTC One hands-on: First look", "Samsung I9300 Galaxy S III hands-on: First look",
                      "Samsung Galaxy Note hands-on: First look", "Samsung F480 preview: First look"):
            with self.subTest(title=title):
                with self.assertRaisesRegex(ScrapeError, "no_standalone_specifications"):
                    specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=self.article(title))))
                primary = self.article(title, f'<li class="meta-link-specs"><a href="{SPEC_URL}">Phone</a></li>')
                self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=primary))))

    def test_parser_failures_collect_remaining_phones_and_reuse_downloaded_source(self):
        source, state, output = self.root / "reviews.csv", self.root / "state.json", self.root / "specs.json"
        other = "https://www.gsmarena.com/other-review-2222.php"
        reviews.save_to_csv([{"phone_name": "Unknown", "review_url": REVIEW_URL},
                            {"phone_name": "Phone", "review_url": other}], source)
        unknown = self.article("Unknown phone review")
        recognized = self.article("Phone review", f'<li class="meta-link-specs"><a href="{SPEC_URL}">Phone</a></li>', canonical=other)
        with patch.dict(os.environ, {"GSMARENA_PAGE_CACHE_DIR": str(self.root / "pages")}):
            http = Mock(get_html=Mock(side_effect=[unknown, recognized, SPEC_HTML]))
            with self.assertRaisesRegex(ScrapeError, "Unresolved"):
                specs.scrape_specs_from_csv(source, output_file=output, output_csv=self.root / "out.csv", state_file=state, client=http)
            self.assertEqual(3, http.get_html.call_count)
            self.assertEqual(other, load_json(output, list)[0]["_metadata"]["review_url"])
            self.assertIn(REVIEW_URL, load_json(state, dict)["unresolved_reviews"])
            self.assertFalse(load_json(state, dict)["complete"])
            retry = Mock(get_html=Mock(side_effect=AssertionError("Already downloaded source must be reused")))
            with self.assertRaisesRegex(ScrapeError, "Unresolved"):
                specs.scrape_specs_from_csv(source, output_file=output, output_csv=self.root / "out.csv", state_file=state, client=retry)
            retry.get_html.assert_not_called()

    def test_corrupt_page_cache_is_not_silently_accepted(self):
        source = self.root / "reviews.csv"
        reviews.save_to_csv([{"phone_name": "Unknown", "review_url": REVIEW_URL}], source)
        with patch.dict(os.environ, {"GSMARENA_PAGE_CACHE_DIR": str(self.root / "pages")}):
            with self.assertRaises(ScrapeError):
                specs.scrape_specs_from_csv(source, output_file=self.root / "specs.json", state_file=self.root / "state.json",
                    client=Mock(get_html=Mock(return_value=self.article("Unknown phone review"))))
            paths = list((self.root / "pages").glob("*.html"))
            self.assertEqual(1, len(paths))
            paths[0].write_bytes(b"truncated")
            with self.assertRaisesRegex(ScrapeError, "cache"):
                specs.scrape_specs_from_csv(source, output_file=self.root / "specs.json", state_file=self.root / "state.json", client=Mock())

    def test_inline_model_must_belong_to_review_and_identity_must_match(self):
        for title, canonical in (("Different phone review", REVIEW_URL),
                                 ("MOQI i7s review", "https://www.gsmarena.com/wrong-review-9999.php")):
            html = self.article(title, canonical=canonical, body=self.inline_summary())
            with self.assertRaises(ScrapeError):
                specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_maker_directories_are_not_legacy_phone_candidates(self):
        html = self.article("Phone preview", body='<a href="samsung-phones-9.php">Samsung</a>'
                            '<a href="nokia_5140-587.php">Nokia 5140</a>')
        self.assertEqual("https://www.gsmarena.com/nokia_5140-587.php",
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html))))

    def test_missing_article_identity_cannot_follow_a_spec_link(self):
        with self.assertRaisesRegex(ScrapeError, "identity"):
            specs.find_spec_url_from_review(REVIEW_URL,
                Mock(get_html=Mock(return_value=f'<a href="{SPEC_URL}">Specifications</a>'.encode())))

    def test_multiple_body_spec_links_are_not_reduced_to_the_first(self):
        html = self.article("Unknown device preview", body=f'<a href="{SPEC_URL}">Specifications</a>'
                            '<a href="other-23456.php">Specifications</a>')
        with self.assertRaisesRegex(ScrapeError, "unambiguous"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_primary_device_link_wins_over_sidebar_specs(self):
        html = self.article("Phone review", f'<a href="other-23456.php">Specifications</a>'
                            f'<li class="meta-link-specs"><a href="{SPEC_URL}">Phone</a></li>')
        self.assertEqual(SPEC_URL, specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html))))

    def test_legacy_three_digit_device_id_is_supported(self):
        url = "https://www.gsmarena.com/nokia_5140-587.php"
        self.assertEqual(url, specs.find_spec_url_from_review(REVIEW_URL,
            Mock(get_html=Mock(return_value=self.article("Nokia 5140 review",
                body=f'<a href="{url}">Specifications</a>')))))

    def test_roundup_cannot_become_its_single_sidebar_phone(self):
        html = self.article("The best-sounding smartphones and tablets of 2024",
                            f'<a href="{SPEC_URL}">Phone</a>')
        with self.assertRaisesRegex(ScrapeError, "Non-device review"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_article_identity_mismatch_cannot_be_excluded(self):
        html = self.article("Android 12 review", canonical="https://www.gsmarena.com/other-review-9999.php")
        with self.assertRaisesRegex(ScrapeError, "identity"):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_editorial_exclusion_is_checkpointed_and_not_refetched(self):
        source, state, output = self.root / "reviews.csv", self.root / "state.json", self.root / "specs.json"
        reviews.save_to_csv([{"phone_name": "Android 12 review", "review_url": REVIEW_URL}], source)
        save_json([dict(self.record(), _metadata=dict(self.record()["_metadata"],
            review_url="https://www.gsmarena.com/existing-review-9999.php"))], output)
        http = Mock(get_html=Mock(return_value=self.article("Android 12 review")))
        result = specs.scrape_specs_from_csv(source, output_file=output, output_csv=self.root / "specs.csv",
            state_file=state, client=http)
        self.assertEqual(1, len(result))
        saved = load_json(state, dict)
        self.assertTrue(saved["complete"])
        self.assertEqual("software_article", saved["excluded_reviews"][REVIEW_URL]["reason"])
        self.assertEqual("Android 12 review", saved["excluded_reviews"][REVIEW_URL]["title"])
        specs.scrape_specs_from_csv(source, output_file=output, output_csv=self.root / "specs.csv",
            state_file=state, client=http)
        self.assertEqual(1, http.get_html.call_count)

    def test_ordinary_phone_without_specs_is_still_an_error(self):
        html = self.article("Oppo Find X3 Pro review", '<a href="a-12345.php">A</a><a href="b-23456.php">B</a>')
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, Mock(get_html=Mock(return_value=html)))

    def test_duplicate_review_checkpoint_is_rejected(self):
        output = self.root / "specs.json"
        save_json([self.record(), self.record()], output)
        with self.assertRaisesRegex(ScrapeError, "Duplicate"):
            specs.load_existing_specs(output)

    def test_pipeline_recovers_missing_checkpoint_records_from_published_asset(self):
        published = self.root / "src/main/assets/devices/specifications.json"
        published.parent.mkdir(parents=True)
        recovered = dict(self.record(), _metadata=dict(self.record()["_metadata"],
            review_url="https://www.gsmarena.com/older-review-9999.php"))
        save_json([self.record(), recovered], published)
        current = dict(self.record(), Features={"Sensors": "New sensor"})
        save_json([current], self.root / "gsmarena_specifications.json")
        result = pipeline.run(self.root, Mock(get_html=Mock(return_value=index_html())))
        self.assertEqual([current, recovered], result)

    def test_specs_use_header_inside_each_table(self):
        result = specs.scrape_specifications(SPEC_URL, Mock(get_html=Mock(return_value=SPEC_HTML)))
        self.assertEqual({"Technology": "GSM"}, result["Network"])
        self.assertEqual({"Sensors": "Fingerprint (side-mounted)"}, result["Features"])

    def test_unrecognized_200_pages_are_errors_not_empty_success(self):
        for call, url in ((specs.scrape_specifications, SPEC_URL), (reviews.scrape_single_page, REVIEW_URL)):
            with self.assertRaises(ScrapeError):
                call(url, Mock(get_html=Mock(return_value=b"<html>Something changed</html>")))

    def test_external_spec_link_is_not_followed(self):
        http = Mock(get_html=Mock(return_value=self.article("Phone review",
            body='<a href="https://example.org/phone-12345.php">Specifications</a>')))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, http)

    def test_ambiguous_sidebar_links_do_not_choose_an_arbitrary_phone(self):
        http = Mock(get_html=Mock(return_value=self.article("Phone review",
            '<a href="a-12345.php">A</a><a href="b-23456.php">B</a>')))
        with self.assertRaises(ScrapeError):
            specs.find_spec_url_from_review(REVIEW_URL, http)

    def test_pipeline_reuses_client_and_second_run_skips_known_specs(self):
        http = Mock(get_html=Mock(side_effect=[index_html(), self.article("Phone review",
            body=f'<a href="{SPEC_URL}">Specifications</a>'), SPEC_HTML]))
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
