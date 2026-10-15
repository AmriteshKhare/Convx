"""Hash source and build configuration inputs for repeatable release comparisons."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    files = set()
    for module in root.iterdir():
        source = module / "src"
        if module.is_dir() and source.is_dir():
            files.update(p for p in source.rglob("*") if p.is_file() and
                         p.suffix in {".kt", ".java", ".xml", ".aidl", ".proto"})
        build = module / "build.gradle.kts"
        if build.is_file():
            files.add(build)
    for relative in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                     "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
                     "gradle/gradle-daemon-jvm.properties", "app/src/main/baseline-prof.txt"):
        path = root / relative
        if path.is_file():
            files.add(path)
    manifest = {str(p.relative_to(root)).replace("\\", "/"): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(files)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Recorded {len(manifest)} source/configuration hashes in {args.output}")


if __name__ == "__main__":
    main()
