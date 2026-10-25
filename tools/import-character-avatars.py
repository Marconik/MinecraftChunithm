"""Import CHUNITHM character DDS portraits as clean Minecraft PNG resources."""

from __future__ import annotations

import argparse
import json
import re
import struct
import tempfile
from pathlib import Path

from PIL import Image


FILE_NAME = re.compile(r"CHU_UI_Character_(\d{4})_\d{2}_\d{2}\.dds", re.IGNORECASE)


def validate_png(path: Path, size: tuple[int, int]) -> None:
    with path.open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"Invalid PNG signature: {path}")
        chunks: list[bytes] = []
        while True:
            header = stream.read(8)
            if len(header) != 8:
                raise ValueError(f"Truncated PNG: {path}")
            length, chunk = struct.unpack(">I4s", header)
            if chunk not in {b"IHDR", b"IDAT", b"IEND"}:
                raise ValueError(f"Unexpected PNG metadata chunk {chunk!r}: {path}")
            chunks.append(chunk)
            stream.seek(length + 4, 1)
            if chunk == b"IEND":
                if stream.read(1):
                    raise ValueError(f"Trailing PNG data: {path}")
                break
        if chunks[0] != b"IHDR" or b"IDAT" not in chunks:
            raise ValueError(f"Missing essential PNG chunks: {path}")
    with Image.open(path) as image:
        image.load()
        if image.mode != "RGBA" or image.size != size or image.info:
            raise ValueError(
                f"Unexpected PNG properties for {path}: "
                f"mode={image.mode}, size={image.size}, metadata={tuple(image.info)}"
            )


def convert(source: Path, destination: Path) -> tuple[int, int]:
    match = FILE_NAME.fullmatch(source.name)
    if not match:
        raise ValueError(f"Unsupported character filename: {source.name}")
    with Image.open(source) as image:
        image.load()
        rgba = image.convert("RGBA")
        size = rgba.size
        clean = Image.frombytes("RGBA", size, rgba.tobytes())
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        prefix=f".{destination.stem}-", suffix=".png", dir=destination.parent, delete=False
    ) as stream:
        temporary = Path(stream.name)
    try:
        clean.save(temporary, format="PNG", compress_level=9, optimize=False)
        validate_png(temporary, size)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
        clean.close()
        rgba.close()
    return size


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("sources", nargs="+", type=Path)
    parser.add_argument("--resources", required=True, type=Path)
    args = parser.parse_args()

    resources = args.resources.absolute()
    if not resources.is_dir():
        raise FileNotFoundError(f"Resources directory not found: {resources}")
    texture_dir = resources / "assets/minecraftchunithm/textures/character"
    item_dir = resources / "assets/minecraftchunithm/items/character"
    model_dir = resources / "assets/minecraftchunithm/models/item"
    index_path = resources / "data/minecraftchunithm/characters/index.json"
    entries: dict[str, dict[str, object]] = {}
    for raw_source in args.sources:
        source = raw_source.absolute()
        if not source.is_file():
            raise FileNotFoundError(f"Character DDS not found: {source}")
        match = FILE_NAME.fullmatch(source.name)
        if not match:
            raise ValueError(f"Unsupported character filename: {source.name}")
        character_id = match.group(1)
        if character_id in entries:
            raise ValueError(f"Duplicate character ID: {character_id}")
        destination = texture_dir / f"{character_id}.png"
        width, height = convert(source, destination)
        model_name = f"character_{character_id}"
        item_dir.mkdir(parents=True, exist_ok=True)
        model_dir.mkdir(parents=True, exist_ok=True)
        (model_dir / f"{model_name}.json").write_text(json.dumps({
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraftchunithm:character/{character_id}"},
        }, indent=2) + "\n", encoding="utf-8")
        (item_dir / f"{character_id}.json").write_text(json.dumps({
            "model": {"type": "minecraft:model", "model": f"minecraftchunithm:item/{model_name}"},
        }, indent=2) + "\n", encoding="utf-8")
        entries[character_id] = {
            "id": character_id,
            "texture": f"minecraftchunithm:character/{character_id}",
            "itemModel": f"minecraftchunithm:character/{character_id}",
            "width": width,
            "height": height,
        }
        print(f"{character_id}: {source.name} -> {destination.relative_to(resources)}")

    index_path.parent.mkdir(parents=True, exist_ok=True)
    catalog = {"formatVersion": 1, "characters": [entries[key] for key in sorted(entries)]}
    index_path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Index: {index_path.relative_to(resources)} ({len(entries)} characters)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
