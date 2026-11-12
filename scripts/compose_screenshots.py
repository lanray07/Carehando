"""Compose localized Amazon screenshots from actual device captures."""
import argparse
import csv
import json
import pathlib

from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = pathlib.Path(__file__).resolve().parents[1]
STORE = ROOT / "deliverables/store-assets"
COLORS = ("#F4F0E6", "#D8E9E1", "#EBE3F1", "#F2E6DB")


def wrap(draw, text, font, width, japanese=False):
    if japanese:
        lines, line = [], ""
        for character in text:
            if line and character not in "。、」）" and draw.textlength(line + character, font=font) > width:
                lines.append(line)
                line = ""
            line += character
        return lines + [line]
    lines, line = [], ""
    for word in text.split():
        proposed = f"{line} {word}".strip()
        if line and draw.textlength(proposed, font=font) > width:
            lines.append(line)
            line = word
        else:
            line = proposed
    return lines + [line]


def fit_copy(draw, title, subtitle, font, width, height, title_size, caption_size, japanese):
    for reduction in range(24):
        size = title_size - reduction
        headline = ImageFont.truetype(font, size)
        caption = ImageFont.truetype(font, caption_size - reduction // 3)
        heading = wrap(draw, title, headline, width, japanese)
        description = wrap(draw, subtitle, caption, width, japanese)
        heading_step = size + 13
        caption_step = caption.size + 12
        used = len(heading) * heading_step + 24 + len(description) * caption_step
        if used <= height and all(draw.textlength(line, font=headline) <= width for line in heading) and all(
            draw.textlength(line, font=caption) <= width for line in description
        ):
            return headline, caption, heading, description, heading_step, caption_step
    raise ValueError(f"Screenshot copy does not fit: {title}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", type=pathlib.Path)
    parser.add_argument("--device", required=True)
    parser.add_argument("--font", default="C:/Windows/Fonts/georgia.ttf")
    parser.add_argument("--japanese-font", default="C:/Windows/Fonts/YuGothM.ttc")
    args = parser.parse_args()
    for locale_dir in sorted(args.captures.iterdir()):
        if not locale_dir.is_dir():
            continue
        metadata_path = STORE / "metadata" / f"{locale_dir.name}.json"
        if not metadata_path.is_file():
            print(f"{locale_dir.name}: no store copy, keeping the device captures")
            continue
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        headlines = metadata["screenshotHeadlines"]
        captions = metadata["screenshotCaptions"]
        japanese = locale_dir.name == "ja-JP"
        font = args.japanese_font if japanese else args.font
        out = STORE / "screenshots" / args.device / locale_dir.name
        out.mkdir(parents=True, exist_ok=True)
        manifest = []
        for index, (key, title) in enumerate(headlines.items()):
            path = locale_dir / f"{key}.png"
            if not path.exists():
                continue
            with Image.open(path) as original:
                screen = original.convert("RGB")
                landscape = screen.width > screen.height
            size = (1920, 1080) if landscape else (1080, 1920)
            canvas = Image.new("RGB", size, COLORS[index % len(COLORS)])
            draw = ImageDraw.Draw(canvas)
            if landscape:
                # Fire HD 10 capture geometry. Trim the device status and navigation bars.
                if screen.size == (1920, 1200):
                    screen = screen.crop((0, 36, screen.width, screen.height - 72))
                brand_y, copy_y, copy_width = 264, 370, 510
                screen_box = (602, 80, 1244, 920)
                headline_size, caption_size = 59, 32
            else:
                if args.device == "phone-viewport-on-fire" and screen.size == (1080, 1920):
                    screen = screen.crop((0, 63, screen.width, screen.height - 126))
                brand_y, copy_y, copy_width = 76, 158, 932
                screen_box = (68, 448, 944, 1400)
                headline_size, caption_size = 66, 32
            mark_path = ROOT / "deliverables/brand-assets/carehando-114.png"
            with Image.open(mark_path) as mark:
                mark = mark.convert("RGBA").resize((48, 48), Image.Resampling.LANCZOS)
                canvas.paste(mark, (70, brand_y), mark)
            brand = ImageFont.truetype(args.font, 35)
            draw.text((135, brand_y + 4), "Carehando", font=brand, fill="#245F61")
            copy_bottom = 1010 if landscape else 438
            headline, caption, heading, description, line_height, caption_step = fit_copy(
                draw, title, captions[key], font, copy_width, copy_bottom - copy_y,
                headline_size, caption_size, japanese,
            )
            for offset, line in enumerate(heading):
                draw.text((70, copy_y + offset * line_height), line, font=headline, fill="#233F3D")
            caption_y = copy_y + len(heading) * line_height + 24
            for offset, line in enumerate(description):
                draw.text((72, caption_y + offset * caption_step), line, font=caption, fill="#334A47")
            x, y, width, height = screen_box
            screen = ImageOps.contain(screen, (width - 30, height - 30), Image.Resampling.LANCZOS)
            left, top = x + (width - screen.width) // 2, y + (height - screen.height) // 2
            draw.rounded_rectangle((left - 15, top - 15, left + screen.width + 15, top + screen.height + 15),
                                   radius=23, fill="#183D3B")
            canvas.paste(screen, (left, top))
            result = out / f"{key}.png"
            canvas.save(result, optimize=True)
            manifest.append([locale_dir.name, args.device, key, str(result.relative_to(ROOT)), *size, title])
        with (out / "manifest.csv").open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["locale", "device", "screen", "file", "width", "height", "headline"])
            writer.writerows(manifest)


if __name__ == "__main__":
    main()

