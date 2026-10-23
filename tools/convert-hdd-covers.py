"""Convert song DDS covers to uniformly sized, metadata-free PNG files."""

from __future__ import annotations

import argparse
import csv
import os
import struct
import tempfile
from collections import Counter
from pathlib import Path

from PIL import Image


def find_covers(root: Path) -> list[Path]:
    return sorted(
        path
        for package in root.iterdir()
        if package.is_dir()
        and package.name.startswith("A")
        and package.name[1:].isdigit()
        for directory in (package / "music").glob("music*")
        if directory.is_dir() and directory.name[5:].isdigit()
        for path in directory.iterdir()
        if path.is_file() and path.suffix.lower() == ".dds"
    )


def validate_png(path: Path, expected_size: tuple[int, int]) -> None:
    # Permit only essential PNG chunks; EXIF, text, ICC, timestamps, etc. fail.
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise ValueError("Invalid PNG signature")
        chunks = []
        while True:
            header = stream.read(8)
            if len(header) != 8:
                raise ValueError("Truncated PNG")
            length, chunk = struct.unpack(">I4s", header)
            if chunk not in {b"IHDR", b"IDAT", b"IEND"}:
                raise ValueError(f"Unexpected metadata chunk: {chunk!r}")
            chunks.append(chunk)
            stream.seek(length + 4, 1)
            if chunk == b"IEND":
                if stream.read(1):
                    raise ValueError("Trailing PNG data")
                break
        if chunks[0] != b"IHDR" or b"IDAT" not in chunks:
            raise ValueError("Missing essential PNG chunks")
    with Image.open(path) as image:
        image.load()
        if image.size != expected_size:
            raise ValueError(f"Wrong dimensions: {image.size}, expected {expected_size}")
        if image.info:
            raise ValueError(f"Unexpected PNG metadata: {image.info.keys()}")


def convert_cover(source: Path, width: int) -> dict[str, str | int]:
    destination = source.with_suffix(".png")
    if destination.exists():
        raise FileExistsError(f"Existing PNG will not be overwritten: {destination}")

    with Image.open(source) as image:
        image.load()
        source_width, source_height = image.size
        height = max(1, round(source_height * width / source_width))
        mode = "RGBA" if "A" in image.getbands() else "RGB"
        pixels = image.convert(mode)
        if pixels.size != (width, height):
            pixels = pixels.resize((width, height), Image.Resampling.LANCZOS)
        # Create a new pixel-only image instead of copying the DDS info dict.
        clean = Image.frombytes(mode, pixels.size, pixels.tobytes())

    descriptor, temporary_name = tempfile.mkstemp(
        prefix=".cover-convert-", suffix=".tmp", dir=destination.parent
    )
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        clean.save(temporary, format="PNG", compress_level=6)
        validate_png(temporary, (width, height))
        # On Windows rename fails if another process created the output meanwhile.
        temporary.rename(destination)
    finally:
        temporary.unlink(missing_ok=True)
        clean.close()

    return {
        "Source": str(source),
        "Output": str(destination),
        "SourceWidth": source_width,
        "SourceHeight": source_height,
        "Width": width,
        "Height": height,
        "Status": "Converted",
        "Error": "",
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("data_root", type=Path)
    parser.add_argument("--width", type=int, default=300)
    parser.add_argument("--limit", type=int, help="Convert only the first N covers")
    parser.add_argument("--output-dir", type=Path, help="Write sample PNGs here instead")
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    if args.width <= 0 or (args.limit is not None and args.limit <= 0):
        parser.error("Width and limit must be positive")
    files = find_covers(args.data_root.resolve(strict=True))
    if args.limit:
        files = files[: args.limit]
    if not files:
        parser.error("No DDS song covers found")
    existing = [p.with_suffix(".png") for p in files if p.with_suffix(".png").exists()]
    if existing and args.output_dir is None:
        parser.error(f"Found {len(existing)} existing PNG files; no files changed")

    args.report.parent.mkdir(parents=True, exist_ok=True)
    counts: Counter[str] = Counter()
    fields = ["Source", "Output", "SourceWidth", "SourceHeight", "Width", "Height", "Status", "Error"]
    with args.report.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for index, source in enumerate(files, start=1):
            try:
                if args.output_dir:
                    # Preserve package/song names so duplicate covers cannot collide.
                    relative = source.relative_to(args.data_root.resolve())
                    sample_source = args.output_dir / relative
                    sample_source.parent.mkdir(parents=True, exist_ok=True)
                    import shutil

                    shutil.copyfile(source, sample_source)
                    try:
                        row = convert_cover(sample_source, args.width)
                        row["Source"] = str(source)
                    finally:
                        sample_source.unlink(missing_ok=True)
                else:
                    row = convert_cover(source, args.width)
            except Exception as error:
                row = {"Source": str(source), "Status": "Failed", "Error": str(error)}
            counts[str(row["Status"])] += 1
            writer.writerow(row)
            stream.flush()
            if index % 50 == 0 or index == len(files):
                print(f"Processed {index}/{len(files)}: {dict(counts)}", flush=True)
    print(f"Report: {args.report.resolve()}", flush=True)
    return 1 if counts["Failed"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
