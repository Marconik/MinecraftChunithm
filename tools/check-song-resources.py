"""Verify song resource references and exact packaged audio/cover contents."""

import json
import re
from pathlib import Path
import zipfile


def main():
    project = Path(__file__).resolve().parents[1]
    resources = project / "src/main/resources"
    jar = project / "build/libs/minecraft-chunithm-0.1.0-dev.jar"
    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, "JAR CRC validation failed"
        assert not any(path.startswith("data/minecraftchunithm/charts/") for path in archive.namelist()), "Stale chart resources in JAR"
        catalog = json.loads(archive.read("data/minecraftchunithm/songs/index.json"))
        genre_path = "data/minecraftchunithm/songs/genres.json"
        genres = json.loads(archive.read(genre_path))["genres"]
        assert archive.read(genre_path) == (resources / genre_path).read_bytes(), "Genre registry is stale"
        assert [genre["id"] for genre in genres] == sorted({genre["id"] for genre in genres}), "Genre IDs must be unique and sorted"
        genre_names = {genre["name"] for genre in genres}
        assert len(genre_names) == len(genres), "Duplicate genre names"
        sounds = json.loads(archive.read("assets/minecraftchunithm/sounds.json"))
        item_atlas = json.loads(archive.read("assets/minecraft/atlases/items.json"))
        assert any(source.get("type") == "minecraft:directory" and source.get("source") == "song"
                   and source.get("prefix") == "song/" for source in item_atlas["sources"]), "Song covers missing from item atlas"
        source_catalog = json.loads((resources / "data/minecraftchunithm/songs/index.json").read_text(encoding="utf-8"))
        assert catalog == source_catalog and catalog["songs"], "Packaged song catalog is stale or empty"
        assert len({entry["id"] for entry in catalog["songs"]}) == len(catalog["songs"])
        for entry in catalog["songs"]:
            namespace, metadata_path = entry["metadata"].split(":", 1)
            metadata = json.loads(archive.read(f"data/{namespace}/{metadata_path}"))
            assert metadata["id"] == entry["id"]
            assert metadata.get("genres") and all(name in genre_names for name in metadata["genres"]), "Song has an unregistered genre"
            assert entry["title"] == metadata["title"]
            assert entry["difficulties"] == [name for name, chart in sorted(
                metadata["charts"].items(), key=lambda item: item[1]["difficultyId"]
            ) if chart["imported"]]
            assert metadata["audio"]["codec"] == "vorbis"
            assert not re.search(r"[A-Za-z]:[\\/]", json.dumps(metadata, ensure_ascii=False)), "Absolute local path in metadata"
            for resource in (metadata["audio"]["resource"], metadata["cover"]["texture"]):
                namespace, path = resource.split(":", 1)
                packaged = f"assets/{namespace}/{path}"
                assert archive.read(packaged) == (resources / packaged).read_bytes()
            namespace, event = metadata["audio"]["soundEvent"].split(":", 1)
            sound = sounds[event]["sounds"][0]
            assert sound["stream"] is True
            sound_namespace, sound_path = sound["name"].split(":", 1)
            assert metadata["audio"]["resource"] == f"{sound_namespace}:sounds/{sound_path}.ogg"
            namespace, model_path = metadata["cover"]["itemModel"].split(":", 1)
            item = json.loads(archive.read(f"assets/{namespace}/items/{model_path}.json"))
            namespace, model = item["model"]["model"].split(":", 1)
            geometry = json.loads(archive.read(f"assets/{namespace}/models/{model}.json"))
            namespace, texture = geometry["textures"]["layer0"].split(":", 1)
            assert metadata["cover"]["texture"] == f"{namespace}:textures/{texture}.png"
            for difficulty, chart in metadata["charts"].items():
                if chart["imported"]:
                    chart_namespace,chart_path=chart["chartResource"].split(":",1)
                    assert Path(chart_path).parent.as_posix() == f"songs/{metadata['id']:04d}", "Chart must be stored in its song directory"
                    definition=json.loads(archive.read(f"data/{chart_namespace}/{chart_path}"))
                    assert archive.read(f"data/{chart_namespace}/{chart_path}") == (resources / f"data/{chart_namespace}/{chart_path}").read_bytes()
                    assert definition["songId"]==metadata["id"] and definition["notes"]
                    assert definition["difficulty"]==difficulty, "Wrong chart difficulty"
                else:
                    assert chart["chartResource"] is None
            print(f"Verified song {entry['id']}: audio, cover, model, metadata and streaming references in JAR")
    print(f"JAR: {jar}")


if __name__ == "__main__":
    main()
