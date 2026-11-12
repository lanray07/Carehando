"""Validate the eight screenshot headlines and captions and Amazon draft field sizes."""
import csv
import json
import pathlib

STORE = pathlib.Path(__file__).resolve().parents[1] / "deliverables/store-assets/metadata"
MARKETS = STORE.parent.parent / "localization/marketplaces.csv"
NAMES = {
    "01-today", "02-plans", "03-handovers", "04-medication",
    "05-appointments", "06-assistant", "07-records", "08-reports",
}
paths = sorted(STORE.glob("*.json"))
with MARKETS.open(encoding="utf-8-sig", newline="") as handle:
    expected = {row["listing_locale"] for row in csv.DictReader(handle) if row["draft_metadata"] == "PASS"}
if {path.stem for path in paths} != expected:
    raise SystemExit("Store metadata files do not match the release marketplace matrix.")

for path in paths:
    metadata = json.loads(path.read_text(encoding="utf-8"))
    for field in ("screenshotHeadlines", "screenshotCaptions"):
        if set(metadata[field]) != NAMES or any(not value.strip() for value in metadata[field].values()):
            raise SystemExit(f"{path.name}: invalid {field}")
    limits = {"displayTitle": 200, "longDescription": 4000}
    for field, limit in limits.items():
        if not 0 < len(metadata[field]) <= limit:
            raise SystemExit(f"{path.name}: invalid {field} length")
    short = metadata["shortDescription"]
    if not 0 < len(short.encode("utf-8")) <= 2000 or "\n" in short:
        raise SystemExit(f"{path.name}: invalid shortDescription")
    bullets = metadata["productFeatureBullets"]
    if not 3 <= len(bullets) <= 5 or any(not bullet.strip() or "\n" in bullet for bullet in bullets):
        raise SystemExit(f"{path.name}: invalid productFeatureBullets")
    print(f"{path.stem}: metadata and screenshot copy checks passed")
