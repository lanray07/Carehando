"""Rasterize the original Carehando mark and compose the promotional wordmark."""
import argparse
import pathlib

import aggdraw
from PIL import Image, ImageDraw, ImageFont

OUT = pathlib.Path(__file__).resolve().parents[1] / "deliverables/brand-assets"
PATHS = [
    ("#184B4E", "M 26 0 L 82 0 C 99 0 108 9 108 26 L 108 82 C 108 99 99 108 82 108 "
                "L 26 108 C 9 108 0 99 0 82 L 0 26 C 0 9 9 0 26 0 Z"),
    ("#F8F4E7", "M 54 32 C 43 19 24 28 25 43 C 26 57 43 68 54 75 C 65 68 82 57 83 43 C 84 28 65 19 54 32 Z"),
    ("#A7BFAF", "M 22 62 C 28 60 37 65 43 70 L 53 78 L 64 72 C 70 69 76 69 79 74 "
                "C 75 82 63 90 54 91 C 44 91 30 81 22 74 C 18 70 16 64 22 62 Z"),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", default="C:/Windows/Fonts/georgia.ttf")
    args = parser.parse_args()
    image = Image.new("RGBA", (1024, 1024))
    canvas = aggdraw.Draw(image)
    canvas.settransform((1024 / 108, 0, 0, 0, 1024 / 108, 0))
    for color, path in PATHS:
        canvas.symbol((0, 0), aggdraw.Symbol(path), aggdraw.Brush(color))
    canvas.flush()
    for size in (1024, 512, 114):
        image.resize((size, size), Image.Resampling.LANCZOS).save(OUT / f"carehando-{size}.png")
    promo = Image.new("RGB", (1024, 500), "#F4F0E6")
    paint = ImageDraw.Draw(promo)
    paint.ellipse((-490, -560, 985, 740), fill="#D8E9E1")
    paint.ellipse((-370, -455, 930, 665), outline="#9BB9AA", width=3)
    paint.ellipse((878, 345, 1188, 655), fill="#EBE3F1")
    icon = image.resize((180, 180), Image.Resampling.LANCZOS)
    promo.paste(icon, (180, 160), icon)
    wordmark = ImageFont.truetype(args.font, 92)
    paint.text((398, 248), "Carehando", anchor="lm", font=wordmark, fill="#183D3B")
    promo.save(OUT / "carehando-amazon-promo.png", optimize=True)


if __name__ == "__main__":
    main()
