"""Rebuild the song index from packaged metadata and imported chart resources."""

import json
from pathlib import Path


def update_song_index(resources):
    directory = resources / "data/minecraftchunithm/songs"
    entries = []
    for metadata_path in sorted(directory.glob("*/song.json")):
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
        difficulties = []
        for name, chart in sorted(metadata["charts"].items(), key=lambda item: item[1]["difficultyId"]):
            if not chart.get("imported"):
                continue
            resource = chart.get("chartResource")
            if not resource:
                raise ValueError(f"Imported chart has no resource: {metadata['id']} {name}")
            namespace, path = resource.split(":", 1)
            if not (resources / "data" / namespace / path).is_file():
                raise ValueError(f"Imported chart resource is missing: {resource}")
            difficulties.append(name)
        entries.append({
            "id": metadata["id"], "title": metadata["title"],
            "difficulties": difficulties,
            "metadata": f"minecraftchunithm:songs/{metadata_path.parent.name}/song.json",
        })
    if len({entry["id"] for entry in entries}) != len(entries):
        raise ValueError("Duplicate song IDs")
    catalog = {"schemaVersion": 1, "songs": sorted(entries, key=lambda entry: entry["id"])}
    (directory / "index.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return catalog


if __name__ == "__main__":
    project = Path(__file__).resolve().parents[1]
    catalog = update_song_index(project / "src/main/resources")
    print(f"Updated song index: {len(catalog['songs'])} songs")
