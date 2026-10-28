"""Package a portable Android Studio project without local caches or signing keys."""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent.parent
output = root / "output" / "Topperia-sorgenti.zip"
excluded = {".git", ".gradle", ".build", "build", "output", ".idea", "__pycache__", "signing", ".secrets"}
output.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(root.rglob("*")):
        relative = path.relative_to(root)
        if not path.is_file() or excluded.intersection(relative.parts):
            continue
        name = path.name.lower()
        if name in {"local.properties", "signing.properties", ".env"} or name.startswith(".env."):
            continue
        if path.suffix.lower() in {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".log", ".iml", ".class"}:
            continue
        archive.write(path, "Topperia/" + relative.as_posix())
print(output)
