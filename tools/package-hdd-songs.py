"""Package converted audio, covers and song metadata into MOD resources."""

import argparse
import csv
from fractions import Fraction
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image
from song_catalog import update_song_index

NAMESPACE = "minecraftchunithm"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("song_ids", type=int, nargs="+")
    parser.add_argument("--difficulties", nargs="+", choices=["MASTER", "ULTIMA"], default=["MASTER"])
    args = parser.parse_args()
    project = Path(__file__).resolve().parents[1]
    resources = project / "src/main/resources"
    assets = resources / "assets" / NAMESPACE
    data = resources / "data" / NAMESPACE
    with (project / "build/hdd-music-index.csv").open(encoding="utf-8-sig", newline="") as stream:
        index = {int(row["Id"]): row for row in csv.DictReader(stream)}

    plans = []
    for song_id in dict.fromkeys(args.song_ids):
        folder = f"{song_id:04d}"
        source = Path(index[song_id]["Location"])
        xml = ET.parse(source / "Music.xml").getroot()
        if int(xml.findtext("name/id")) != song_id:
            raise ValueError("CSV/XML song ID mismatch")
        analyses = {difficulty: read_json(project / f"build/chart-analysis/{folder}_{dict(MASTER='03', ULTIMA='04')[difficulty]}.analysis.json")
                    for difficulty in args.difficulties}
        analysis = analyses.get("MASTER", next(iter(analyses.values())))
        conversion = read_json(project / f"build/audio-tests/music{folder}/conversion.json")
        audio = project / f"build/audio-tests/music{folder}/music{folder}.ogg"
        if conversion["Id"] != song_id or not conversion["FullDecodeValidated"]:
            raise ValueError("Missing validated audio conversion")
        probe = json.loads(subprocess.run([
            "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(audio)
        ], check=True, capture_output=True, text=True).stdout)
        audio_stream = probe["streams"][0]
        if audio_stream["codec_name"] != "vorbis":
            raise ValueError("Packaged audio must be Ogg Vorbis")
        duration = float(probe["format"]["duration"])
        if abs(duration - conversion["DurationSeconds"]) > 0.001:
            raise ValueError("Audio conversion report is stale")
        subprocess.run(["ffmpeg", "-v", "error", "-nostdin", "-xerror", "-i", str(audio),
                        "-f", "null", "-"], check=True, capture_output=True)
        cover = source / Path(xml.findtext("jaketFile/path")).with_suffix(".png")
        with Image.open(cover) as image:
            image.load()
            if image.size != (300, 300) or image.info:
                raise ValueError("Expected metadata-free 300x300 song cover")

        charts = {}
        for fumen in xml.findall(".//MusicFumenData"):
            if fumen.findtext("enable") != "true":
                continue
            difficulty = fumen.findtext("type/data")
            level = int(fumen.findtext("level", "0"))
            decimal = int(fumen.findtext("levelDecimal", "0"))
            chart = {
                "difficultyId": int(fumen.findtext("type/id")),
                "level": level, "levelDecimal": decimal,
                "internalLevel": float(Fraction(level) + Fraction(decimal, 100)),
                "sourceFile": fumen.findtext("file/path"),
                "notesDesigner": fumen.findtext("notesDesigner") or None,
                "chartResource": None, "imported": False,
            }
            if difficulty in analyses:
                chart_analysis=analyses[difficulty]
                chart_path=f"songs/{folder}/{difficulty.lower()}.json"
                if (data / chart_path).exists():
                    chart.update(chartResource=f"{NAMESPACE}:{chart_path}", imported=True)
                identity = chart_analysis["identity"]
                if identity["musicId"] != song_id or identity["difficulty"].upper() != difficulty:
                    raise ValueError("Chart analysis identity mismatch")
                chart.update(
                    notesDesigner=chart_analysis["header"]["CREATOR"][0],
                    firstNoteMs=chart_analysis["timing"]["firstTimeMs"],
                    lastNoteMs=chart_analysis["timing"]["lastTimeMs"],
                    logicalNoteCount=int(chart_analysis["sourceStatistics"]["T_NOTE_ALL"][0]),
                    judgmentCount=int(chart_analysis["sourceStatistics"]["T_JUDGE_ALL"][0]),
                    speedChangeMarkerCount=len(chart_analysis["speedChangeMarkers"]),
                    visualWarnings=chart_analysis.get("visualWarnings",[]),
                )
            charts[difficulty] = chart

        sound_event = f"music.{folder}"
        metadata = {
            "schemaVersion": 1, "id": song_id,
            "title": xml.findtext("name/str"), "artist": xml.findtext("artistName/str"),
            "sortName": xml.findtext("sortName"),
            "genres": [node.text for node in xml.findall("genreNames/list/StringID/str")],
            "releaseDate": xml.findtext("releaseDate"),
            "defaultBpm": float(analysis["header"]["BPM_DEF"][0]),
            "cover": {
                "texture": f"{NAMESPACE}:textures/song/{folder}/cover.png",
                "itemModel": f"{NAMESPACE}:cover/{folder}", "width": 300, "height": 300,
            },
            "audio": {
                "soundEvent": f"{NAMESPACE}:{sound_event}",
                "resource": f"{NAMESPACE}:sounds/songs/{folder}/music.ogg",
                "codec": "vorbis", "sampleRate": int(audio_stream["sample_rate"]),
                "channels": audio_stream["channels"], "durationMs": round(duration * 1000, 6),
                "stream": True, "offsetMs": 0,
            },
            "charts": charts,
            "source": {"package": source.parent.parent.name, "musicDirectory": source.name,
                       "metadataFile": "Music.xml"},
        }
        plans.append((folder, metadata, audio, cover, sound_event))

    sound_path = assets / "sounds.json"
    sounds = read_json(sound_path) if sound_path.exists() else {}
    for folder, metadata, audio, cover, sound_event in plans:
        audio_dest = assets / f"sounds/songs/{folder}/music.ogg"
        cover_dest = assets / f"textures/song/{folder}/cover.png"
        for original, target in ((audio, audio_dest), (cover, cover_dest)):
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, target)
            if original.read_bytes() != target.read_bytes():
                raise ValueError(f"Copy validation failed: {target}")
        write_json(data / f"songs/{folder}/song.json", metadata)
        write_json(assets / f"items/cover/{folder}.json", {
            "model": {"type": "minecraft:model", "model": f"{NAMESPACE}:item/cover_{folder}"}
        })
        write_json(assets / f"models/item/cover_{folder}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"{NAMESPACE}:song/{folder}/cover"},
            "display": {"fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}},
        })
        sounds[sound_event] = {"sounds": [{"name": f"{NAMESPACE}:songs/{folder}/music", "stream": True}]}
        print(f"Packaged {metadata['id']}: {metadata['audio']['durationMs']} ms; metadata, audio and cover")
    update_song_index(resources)
    write_json(sound_path, sounds)


if __name__ == "__main__":
    main()
