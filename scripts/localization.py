"""Export source messages, stage API translations, or validate Android resource coverage."""
import argparse
import collections
import json
import os
import pathlib
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
TARGETS = "en-GB en-US en-AU es-ES es-419 fr-FR fr-CA de-DE it-IT pt-BR pt-PT nl-NL pl-PL sv-SE ja-JP hi-IN ar tr-TR id-ID".split()
BASE_CATALOGUES = {"en-GB": "values", "en-US": "values", "en-AU": "values",
                   "fr-FR": "values-fr", "es-ES": "values-es"}
LOCALE_CONFIG = RES / "xml/locales_config.xml"
ANDROID_NAME = "{http://schemas.android.com/apk/res/android}name"


def resource_folder(locale):
    if locale == "id-ID":
        return "values-in-rID"
    language, _, region = locale.partition("-")
    return f"values-b+{language}+{region}" if region else f"values-{language}"


PUBLISHED = {
    tag: BASE_CATALOGUES.get(tag, resource_folder(tag))
    for node in ET.parse(LOCALE_CONFIG).getroot()
    if (tag := node.attrib[ANDROID_NAME])
}
GLOSSARY = {"handover": "a caregiver’s shift or visit summary", "care recipient": "the person receiving help"}
FORMAT = re.compile(r"%(?:\d+\$)?[dsf]|%%")
PLURAL_QUANTITIES = {
    "ar": {"zero", "one", "two", "few", "many", "other"},
    "pl": {"one", "few", "many", "other"},
    **{language: {"one", "many", "other"} for language in ("es", "fr", "it", "pt")},
    **{language: {"other"} for language in ("ja", "id")},
}


def resources(folder):
    root = ET.parse(RES / folder / "strings.xml").getroot()
    result = {}
    for node in root:
        key = node.attrib["name"]
        if key in result:
            raise ValueError(f"Duplicate resource {key}")
        if node.tag == "plurals":
            result[key] = {item.attrib["quantity"]: item.text or "" for item in node}
        else:
            result[key] = node.text or ""
    return result


def errors(source, target):
    problems = [f"Unknown resource: {key}" for key in sorted(target.keys() - source.keys())]
    for key, original in source.items():
        translated = target.get(key)
        if not translated or type(translated) is not type(original):
            problems.append(f"Missing or malformed: {key}")
            continue
        values = [(original, translated)]
        if isinstance(original, dict):
            if "other" not in translated:
                problems.append(f"Missing plural 'other': {key}")
            values = [(original.get(quantity, original["other"]), text) for quantity, text in translated.items()]
        for before, after in values:
            if not isinstance(after, str) or not after.strip():
                problems.append(f"Empty or malformed translation: {key}")
                continue
            if collections.Counter(FORMAT.findall(before)) != collections.Counter(FORMAT.findall(after)):
                problems.append(f"Placeholder mismatch: {key}")
        if key.startswith("voice_") and "|" in str(original):
            if any(not alias.strip() for alias in str(translated).split("|")):
                problems.append(f"Empty voice alias: {key}")
    return problems


def plural_errors(locale, strings):
    required = PLURAL_QUANTITIES.get(locale.split("-")[0], {"one", "other"})
    return [
        f"Missing plural quantities for {key}: {', '.join(sorted(required - value.keys()))}"
        for key, value in strings.items()
        if isinstance(value, dict) and required - value.keys()
    ]


def stage(source, locale):
    """Use the configured translation service. Exchange source resource text only."""
    cache = ROOT / "deliverables/localization/staged" / f"{locale}.json"
    if cache.exists():
        previous = json.loads(cache.read_text(encoding="utf-8"))
        if previous.get("source") == source and not errors(source, previous["strings"]):
            return
    url = os.environ.get("CAREHANDO_TRANSLATION_URL", "")
    key = os.environ.get("CAREHANDO_TRANSLATION_KEY", "")
    if not url.startswith("https://") or not key:
        raise SystemExit("Configure the HTTPS translation URL and key to stage translations.")
    request = urllib.request.Request(url, data=json.dumps({
        "locale": locale, "strings": source, "glossary": GLOSSARY,
        "instructions": "Preserve Android format placeholders and voice command separators. Translate strings only."
    }).encode(), headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=90) as response:
        translated = json.load(response)["strings"]
    problems = errors(source, translated)
    if problems:
        raise SystemExit("\n".join(problems))
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(json.dumps({
        "source": source, "locale": locale, "review": "pending", "strings": translated,
    }, ensure_ascii=False, indent=2), encoding="utf-8")


def generate(source, locale):
    path = ROOT / "deliverables/localization/staged" / f"{locale}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("source") != source or data.get("review") != "approved":
        raise SystemExit("Review the current translations and mark them approved before generation.")
    problems = errors(source, data["strings"]) + plural_errors(locale, data["strings"])
    if problems:
        raise SystemExit("\n".join(problems))
    folder = RES / resource_folder(locale)
    folder.mkdir(exist_ok=True)
    root = ET.Element("resources")
    for key, value in data["strings"].items():
        if isinstance(value, dict):
            node = ET.SubElement(root, "plurals", name=key)
            for quantity, text in value.items():
                ET.SubElement(node, "item", quantity=quantity).text = android_escape(text)
        else:
            ET.SubElement(root, "string", name=key).text = android_escape(value)
    ET.indent(root, space="    ")
    ET.ElementTree(root).write(folder / "strings.xml", encoding="utf-8", xml_declaration=True)


def android_escape(value):
    return value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")


def main():
    parser = argparse.ArgumentParser()
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--extract", action="store_true")
    actions.add_argument("--stage", choices=TARGETS)
    actions.add_argument("--generate", choices=TARGETS)
    args = parser.parse_args()
    source = resources("values")
    if args.extract:
        print(json.dumps(source, ensure_ascii=True, indent=2))
    elif args.stage:
        stage(source, args.stage)
    elif args.generate:
        generate(source, args.generate)
    else:
        failed = False
        for locale in TARGETS:
            folder = PUBLISHED.get(locale)
            if not folder:
                path = ROOT / "deliverables/localization/staged" / f"{locale}.json"
                if not path.is_file():
                    print(f"{locale}: BLOCKED (no staged or approved resource catalogue)")
                    continue
                draft = json.loads(path.read_text(encoding="utf-8"))
                problems = errors(source, draft["strings"])
                if draft.get("source") != source or draft.get("locale") != locale:
                    problems.append("staged catalogue does not match the current source or locale")
                if problems:
                    failed = True
                    print(f"{locale}: FAIL (staged catalogue)")
                    for problem in problems:
                        print(f"  {problem}")
                else:
                    print(f"{locale}: STAGED ({len(source)}/{len(source)} resources, review {draft['review']})")
                continue
            target = resources(folder)
            missing = errors(source, target) + plural_errors(locale, target)
            coverage = f"{len(source.keys() & target.keys())}/{len(source)} resources"
            print(f"{locale}: {'FAIL' if missing else 'PASS'} ({coverage})")
            for problem in missing:
                print(f"  {problem}")
            failed |= bool(missing)
        raise SystemExit(int(failed))


if __name__ == "__main__":
    main()
