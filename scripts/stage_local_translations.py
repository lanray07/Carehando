"""Stage local M2M-100 translations for review without publishing Android resources."""
import argparse
import json
from pathlib import Path
import re

import ctranslate2
import sentencepiece

from localization import FORMAT, PUBLISHED, ROOT, TARGETS, errors, resources


def stage(model_path, locales):
    source = resources("values")
    tokenizer = sentencepiece.SentencePieceProcessor(model_file=str(model_path / "sentencepiece.bpe.model"))
    model = ctranslate2.Translator(str(model_path), device="cpu", compute_type="int8", inter_threads=2, intra_threads=2)
    revision = (model_path / "revision.txt").read_text(encoding="ascii").strip()
    cache = {}
    pattern = re.compile("(" + FORMAT.pattern + r"|\|)")
    folder = ROOT / "deliverables/localization/staged"
    folder.mkdir(parents=True, exist_ok=True)

    def translate(language):
        if language in cache:
            return cache[language]
        parts = {}
        for key, value in source.items():
            values = value if isinstance(value, dict) else {"": value}
            for quantity, text in values.items():
                parts[key, quantity] = pattern.split(text) if key.startswith("voice_") else re.split("(" + FORMAT.pattern + ")", text)
        strings = sorted({piece.strip() for pieces in parts.values() for piece in pieces
                          if piece.strip() and piece != "|" and not FORMAT.fullmatch(piece)
                          and piece.strip() not in {"Carehando", "Carehando Premium", "PDF", "CSV"}})
        target = f"__{language}__"
        results = {}
        for offset in range(0, len(strings), 32):
            batch = strings[offset:offset + 32]
            encoded = [["__en__"] + tokenizer.encode(value, out_type=str) + ["</s>"] for value in batch]
            outputs = model.translate_batch(encoded, target_prefix=[[target]] * len(batch), beam_size=1,
                                            max_decoding_length=160)
            for original, output in zip(batch, outputs):
                tokens = [token for token in output.hypotheses[0] if token not in {target, "</s>"}]
                result = tokenizer.decode(tokens).strip()
                results[original] = result if result and not FORMAT.search(result) and "|" not in result else original

        def translated(key, quantity):
            result = []
            for piece in parts[key, quantity]:
                original = piece.strip()
                if original in results:
                    result.append(piece.replace(original, results[original], 1))
                else:
                    result.append(piece)
            return "".join(result)

        cache[language] = {
            key: {quantity: translated(key, quantity) for quantity in value}
            if isinstance(value, dict) else translated(key, "")
            for key, value in source.items()
        }
        return cache[language]

    for locale in locales:
        if locale in PUBLISHED:
            raise SystemExit(f"{locale} already has an app resource catalogue")
        if locale == "fr-CA":
            catalogue, method = resources("values-fr"), "French catalogue as a regional starting point"
        elif locale == "es-419":
            catalogue, method = resources("values-es"), "Spanish catalogue as a regional starting point"
        else:
            catalogue, method = translate(locale.split("-")[0]), f"M2M-100 int8 at {revision}"
        problems = errors(source, catalogue)
        if problems:
            raise SystemExit(f"{locale}: " + "; ".join(problems))
        record = {"source": source, "locale": locale, "review": "pending", "method": method, "strings": catalogue}
        (folder / f"{locale}.json").write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{locale}: {len(catalogue)} staged resources, review pending", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    parser.add_argument("--locale", action="append", choices=TARGETS)
    args = parser.parse_args()
    stage(args.model, args.locale or [locale for locale in TARGETS if locale not in PUBLISHED])
