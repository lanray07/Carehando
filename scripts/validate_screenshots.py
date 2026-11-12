"""Validate composed Carehando screenshot sets against their device captures."""
import csv
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[1]
STORE = ROOT / "deliverables/store-assets/screenshots"
RAW = ROOT / "deliverables/screenshots"
LOCALES = tuple(sorted(path.stem for path in (STORE.parent / "metadata").glob("*.json")))
NAMES = {
    "01-today", "02-plans", "03-handovers", "04-medication",
    "05-appointments", "06-assistant", "07-records", "08-reports",
}
VARIANTS = {
    "fire-hd10-2021": ((1920, 1200), (1920, 1080)),
    "phone-viewport-on-fire": ((1080, 1920), (1080, 1920)),
}


def validate(variant, source_size, output_size):
    for locale in LOCALES:
        folder = STORE / variant / locale
        with (folder / "manifest.csv").open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        assert len(rows) == 8 and {row["screen"] for row in rows} == NAMES, (variant, locale)
        assert {path.stem for path in folder.glob("*.png")} == NAMES, (variant, locale)
        for row in rows:
            capture = RAW / variant / locale / f'{row["screen"]}.png'
            image = folder / capture.name
            assert row["locale"] == locale and row["device"] == variant, row
            assert (ROOT / row["file"]).resolve() == image.resolve(), row
            assert (int(row["width"]), int(row["height"])) == output_size, row
            with Image.open(capture) as raw:
                assert raw.format == "PNG" and raw.size == source_size, capture
                raw.verify()
            with Image.open(image) as shot:
                assert shot.format == "PNG" and shot.mode == "RGB" and shot.size == output_size, image
                shot.verify()
        print(f"{variant} {locale}: 8/8 validated at {output_size[0]} × {output_size[1]}")


if __name__ == "__main__":
    requested = sys.argv[1:] or list(VARIANTS)
    for variant in requested:
        validate(variant, *VARIANTS[variant])
