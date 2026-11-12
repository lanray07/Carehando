"""Run the Android capture test and retrieve real screens from the selected device."""
import argparse
import pathlib
import subprocess
from localization import PUBLISHED

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCALES = ["en-GB", "en-US", "en-AU", "fr-FR", "es-ES"]
NAMES = [
    "01-today", "02-plans", "03-handovers", "04-medication",
    "05-appointments", "06-assistant", "07-records", "08-reports",
]
APP = "app.carehando.debug"
RUNNER = f"{APP}.test/androidx.test.runner.AndroidJUnitRunner"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--device", required=True)
    parser.add_argument("--locale", action="append", choices=(*PUBLISHED, "en-IN", "es-MX"))
    parser.add_argument("--calendar-only", action="store_true")
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    artifacts = ROOT / "deliverables/android"
    root = ROOT / "deliverables/screenshots" / args.device
    names = ["05-appointments"] if args.calendar_only else NAMES
    suffix = "-calendar" if args.calendar_only else ""
    for locale in args.locale or LOCALES:
        result = subprocess.run(adb + [
            "shell", "am", "instrument", "-w", "-r", "-e", "class",
            "app.carehando.CareJourneyTest#captureActualScreens", "-e", "captureLocale", locale,
            "-e", "captureCalendarOnly", str(args.calendar_only).lower(), RUNNER,
        ], capture_output=True, text=True, encoding="utf-8", errors="replace", check=True)
        output = result.stdout + result.stderr
        (artifacts / "build-logs").mkdir(parents=True, exist_ok=True)
        (artifacts / "build-logs" / f"capture-{args.device}-{locale}{suffix}.log").write_text(output, encoding="utf-8")
        if "OK (1 test)" not in output:
            raise SystemExit(f"{locale} failed. See the instrumentation log.")
        folder = root / locale
        folder.mkdir(parents=True, exist_ok=True)
        for key in names:
            command = ["exec-out", "run-as", APP, "cat", f"files/screenshots/{locale}/{key}.png"]
            image = subprocess.check_output(adb + command)
            if not image.startswith(b"\x89PNG\r\n\x1a\n"):
                raise SystemExit(f"Invalid screenshot: {locale}/{key}")
            (folder / f"{key}.png").write_bytes(image)
        print(f"{locale}: {len(names)} device captures")


if __name__ == "__main__":
    main()
