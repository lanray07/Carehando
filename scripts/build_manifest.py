"""Record hashes for delivered Carehando files without self-referencing the manifest."""
import csv
import hashlib
import pathlib

root = pathlib.Path(__file__).resolve().parents[1]
folder = root / "deliverables"
manifest = folder / "DELIVERABLES_MANIFEST.csv"
with manifest.open("w", newline="", encoding="utf-8") as handle:
    writer = csv.writer(handle)
    writer.writerow(["file", "bytes", "sha256"])
    for path in sorted(folder.rglob("*")):
        if path.is_file() and path != manifest:
            writer.writerow([path.relative_to(root).as_posix(), path.stat().st_size,
                             hashlib.sha256(path.read_bytes()).hexdigest()])
