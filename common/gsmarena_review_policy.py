"""Explicit exclusions for articles which cannot describe one specification-sheet device."""
import re

from gsmarena_http import ScrapeError


class NonDeviceReview(ScrapeError):
    def __init__(self, title, reason, rule):
        self.title, self.reason, self.rule = title, reason, rule
        super().__init__(f"Non-device review ({reason}): {title}")


RULES = (
    (r"^(?:The\s+)?(?:GSMArena\s+)?(?:smartphone|tablet)\s+(?:buyer['’]s|shopping)\s+guide(?:\s*[:,-].*)?$", "buying_guide"),
    (r"^Our editors(?: talk(?:\s*:.*)?|['’] favorite smartphones of \d{4})$", "editorial"),
    (r"^The best-sounding smartphones(?: and tablets)? of \d{4}$", "roundup"),
    (r"^(?:Apple\s+)?iOS\s+\d+(?:\s+(?:review|preview|for beginners))?(?:\s*:\s*.+)?$", "software_article"),
    (r"^Android\s+\d+(?:\.\d+)?(?:\s+Q\s+beta|\s+Ice\s+Cream\s+Sandwich)?(?:\s+(?:review|preview))?(?:\s*:\s*.+)?$", "software_article"),
    (r"^(?:Microsoft\s+)?Windows(?:\s+Phone)?\s+\d+(?:\.\d+)?(?:\s+(?:OS|Series|for phones))*\s*(?:review|preview)?(?:\s*:\s*.+)?$", "software_article"),
    (r"^(?:Various brands|Sony|Samsung|Sony Ericsson|Fashion phones) overview(?:\s*:\s*.+)?$", "multi_device_article"),
)

# Exact, inspectable titles; never infer a multi-device article just from "and" or a comma.
EXACT_TITLES = {
    "Samsung G800 launch event: A night at the museum": "multi_device_article",
    "GSMArena feature lab: Enter new goodies": "editorial",
    "Dual SIM review: Mobiles go two-in-one": "technology_article",
    "Sony Ericsson Media Go review: First look": "software_article",
    "MWC 2009: The top 7 things we wish we took home": "editorial",
    "Mobile phones go scalp hunting: The Red List": "editorial",
    "Samsung Galaxy S III Jelly Bean preview: First look": "software_article",
    "GSMArena feature labs: The tests": "technology_article",
    "Acer 2015: Liquid Jade Z, Liquid Z520 and Z220, Liquid M220, Liquid Leap Plus hands-on": "multi_device_article",
    "Samsung Galaxy S6 and S6 edge video review": "multi_device_article",
    "Charge test: Battle of the fast chargers": "comparison_article",
    "Our mobile chipset guide: The 2017 edition": "technology_article",
    "Introducing time-saver reviews": "editorial",
    "SmartSensor Image Stabilization and Super VOOC: Oppo at MWC 2016": "technology_article",
    "Amazon Kindle Fire HD hands-on: First look": "multi_device_article",
    "Testing 5G: Hype vs. Reality": "technology_article",
    "Galaxy Note8 vs iPhone 8 Plus camera blind vote": "comparison_article",
    "OnePlus 5 vs. Galaxy S8 vs. iPhone 7 Plus": "comparison_article",
    "HTC U11 vs. iPhone 7 Plus vs. Xperia XZ Premium": "comparison_article",
    "Blind shootout revealed": "comparison_article",
    "Honor 6X vs. ZTE Blade V8 Pro": "comparison_article",
    "iPhone 7 vs Galaxy S7 vs Xperia XZ vs LG G5": "comparison_article",
    "Galaxy Note 5 vs. iPhone 6s Plus vs. LG V10": "comparison_article",
    "iPhone 6s Plus vs. Lumia 950 XL vs. Galaxy Note5": "comparison_article",
    "Galaxy shootout": "comparison_article",
    "Samsung Galaxy S5 vs Oppo Find 7a": "comparison_article",
    "LG G Pro 2 vs Samsung Galaxy Note 3": "comparison_article",
    "Samsung Galaxy Note 3 vs. Nokia Lumia 1520": "comparison_article",
    "Samsung I9500 Galaxy S4 vs Samsung I9505 Galaxy S4": "comparison_article",
    "Smartphones vs. laptops": "comparison_article",
    "Virtual personal assistant shootout": "comparison_article",
    "8 megapixel mega shootout": "comparison_article",
    "Grand 5 megapixel shootout": "comparison_article",
    "Testing the brand new Snapdragon 8+ Gen 1": "technology_article",
    "Which cloud gaming service is the best?": "software_article",
    "Best phones for selfies, Jan 2020": "roundup",
    "Camera test: The phone with the best Portrait mode": "comparison_article",
    "Best and worst smartphone trends of 2018": "editorial",
    "Best phones, worst phones 2017": "roundup",
    "Our mobile chipset guide": "technology_article",
    "Introducing our new time-saver review format": "editorial",
    "SmartSensor Image Stabilization and Super VOOC": "technology_article",
    "GSMArena.com turns 15": "editorial",
    "Our 2010 holiday gift guide": "buying_guide",
    "Mobile phone usage report 2011": "editorial",
    "Mobile phones evolution": "editorial",
    "The top 7 things we wish we took home": "editorial",
    "GPS navigation overview": "technology_article",
    "HTC 2010 event coverage": "multi_device_article",
    "New Sony Ericsson models": "multi_device_article",
    "3GSM Barcelona 2006 live coverage": "multi_device_article",
    "Setting up your Android smartphone": "software_article",
    "iOS for beginners": "software_article",
    "Android for beginners": "software_article",
    "Zune software review": "software_article",
    "Gear Sport, Fit2 Pro, IconX (2018) hands-on review": "multi_device_article",
    "Pop Up, Start, First and Idol 3C hands-on": "multi_device_article",
    "Acer Predator 6 and Jade Primo hands-on": "multi_device_article",
    "Samsung Galaxy S6 and S6 edge": "multi_device_article",
    "Samsung Galaxy S6 and S6 edge preview": "multi_device_article",
    "HTC One M9, Grip hands-on": "multi_device_article",
    "Samsung Galaxy S6 and S6 edge hands-on": "multi_device_article",
    "Acer Liquid Jade Z, Liquid Z520 and Z220, Liquid M": "multi_device_article",
    "Asus Zenfone 2, Zenfone Zoom hands-on": "multi_device_article",
    "HTC Titan and HTC Radar hands-on": "multi_device_article",
    "T-Mobile MDA Vario IV and Compact IV": "multi_device_article",
    "vivo X500, X500 Pro and X500 Pro Max hands-on review": "multi_device_article",
    "Samsung Galaxy Z Fold8, Z Fold8 Ultra, Z Flip8 hands-on review": "multi_device_article",
}

# Verified articles which provide only prose/benchmark comparisons, not a usable device sheet.
PROSE_ONLY_REVIEW_TITLES = {title.casefold() for title in (
    "The Card phone review: The future is modular",
    "Zopo Speed 8 hands-on: Zopo at MWC 2016",
    "HTC One hands-on: First look",
    "Samsung I9300 Galaxy S III hands-on: First look",
    "Samsung Galaxy Note hands-on: First look",
    "Samsung F480 preview: First look",
)}


def non_device_reason(title):
    title = " ".join(title.split())
    event_title = re.sub(r"^(?:.+? at )?(?:IFA|MWC|CES|CTIA|CeBIT|Computex)\s+\d{4}:\s*", "", title,
                         flags=re.IGNORECASE)
    for candidate, reason in EXACT_TITLES.items():
        if title.casefold() == candidate.casefold() or title.casefold().startswith(candidate.casefold() + ":"):
            return reason, f"exact:{candidate}"
        if reason == "multi_device_article" and (event_title.casefold() == candidate.casefold()
                or event_title.casefold().startswith(candidate.casefold() + ":")):
            return reason, f"event_suffix:{candidate}"
    comparison_title = event_title
    separators = list(re.finditer(r"\bvs\.?(?=\s)", comparison_title, re.IGNORECASE))
    model = re.match(r"^Honor Magic Vs\b", comparison_title, re.IGNORECASE)
    if model:
        separators = [item for item in separators if item.start() >= model.end()]
    if separators:
        left = comparison_title[:separators[0].start()].strip()
        right = re.sub(r"\s*(?:hands[- ]on(?: review)?|review|preview)$", "",
                       comparison_title[separators[-1].end():], flags=re.IGNORECASE).strip()
        if left and right:
            return "comparison_article", "comparison_separator:vs (Honor Magic Vs model token excluded)"
    for pattern, reason in RULES:
        candidate = event_title if reason in ("multi_device_article", "software_article") else title
        if re.search(pattern, candidate, re.IGNORECASE):
            return reason, pattern
    return None
