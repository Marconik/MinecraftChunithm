"""Extract a single-stream AWB song and convert HCA to Ogg Vorbis."""

import argparse
import csv
import json
import shutil
import struct
import subprocess
from pathlib import Path


def run(arguments):
    return subprocess.run(arguments, check=True, capture_output=True, text=True)


def probe(path):
    return json.loads(run([
        "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)
    ]).stdout)


def extract_single_hca(path):
    data = path.read_bytes()
    if len(data) < 16 or data[:4] != b"AFS2":
        raise ValueError("Expected an AFS2 AWB container")
    offset_width = data[5]
    id_width = int.from_bytes(data[6:8], "little")
    count, alignment, subkey = struct.unpack_from("<IHH", data, 8)
    if count != 1:
        raise ValueError(f"Expected one song stream; found {count}. Select streams manually.")
    if offset_width not in (2, 4) or not id_width or not alignment:
        raise ValueError("Unsupported AWB offset or alignment")
    table = 16 + count * id_width
    table_end = table + (count + 1) * offset_width
    if table_end > len(data):
        raise ValueError("Truncated AWB table")
    start = int.from_bytes(data[table:table + offset_width], "little")
    end = int.from_bytes(data[table + offset_width:table_end], "little")
    start = (start + alignment - 1) // alignment * alignment
    if not table_end <= start < end == len(data):
        raise ValueError("Invalid single-stream AWB boundaries")
    payload = data[start:end]
    if bytes(value & 0x7F for value in payload[:4]) != b"HCA\0":
        raise ValueError("AWB stream is not HCA")
    return payload, subkey


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("song_id", type=int)
    parser.add_argument("--index", type=Path, default=Path(__file__).resolve().parents[1] / "build/hdd-music-index.csv")
    parser.add_argument("--output", type=Path)
    # Public CHUNITHM NEW key documented by vgmstream:
    # https://github.com/vgmstream/vgmstream/blob/master/src/meta/hca_keys.h
    parser.add_argument("--hca-key", type=int, default=32931609366120192)
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()
    for tool in ("ffmpeg", "ffprobe"):
        if not shutil.which(tool):
            parser.error(f"{tool} is not on PATH")
    with args.index.open(encoding="utf-8-sig", newline="") as stream:
        songs = [row for row in csv.DictReader(stream) if int(row["Id"]) == args.song_id]
    if len(songs) != 1:
        parser.error("Song ID must match exactly one CSV row")
    song = songs[0]
    source = Path(song["AwbPath"])
    directory = args.output or args.index.parent / "audio-tests" / f"music{args.song_id:04d}"
    directory.mkdir(parents=True, exist_ok=True)
    stem = f"music{args.song_id:04d}"
    hca = directory / f"{stem}.hca"
    ogg = directory / f"{stem}.ogg"
    report_path = directory / "conversion.json"
    if not args.overwrite and any(path.exists() for path in (hca, ogg, report_path)):
        parser.error("Output already exists; use --overwrite to regenerate")
    payload, subkey = extract_single_hca(source)
    hca.write_bytes(payload)
    temporary = directory / f"{stem}.converting.ogg"
    try:
        run([
            "ffmpeg", "-hide_banner", "-nostdin", "-y", "-v", "warning",
            "-hca_lowkey", str(args.hca_key & 0xFFFFFFFF),
            "-hca_highkey", str(args.hca_key >> 32), "-hca_subkey", str(subkey),
            "-i", str(hca), "-map", "0:a:0", "-map_metadata", "-1",
            "-c:a", "libvorbis", "-q:a", "6", str(temporary),
        ])
        result = probe(temporary)
        original = probe(hca)
        stream = result["streams"][0]
        if stream["codec_name"] != "vorbis":
            raise ValueError("Output is not Vorbis")
        duration = float(result["format"]["duration"])
        if abs(duration - float(original["format"]["duration"])) > 0.05:
            raise ValueError("Duration changed during conversion")
        run(["ffmpeg", "-v", "error", "-nostdin", "-xerror", "-i", str(temporary), "-f", "null", "-"])
        temporary.replace(ogg)
        report = {
            "Id": args.song_id, "Name": song["Name"], "SourceAwb": str(source),
            "SourceAcb": song["AcbPath"], "Output": str(ogg.resolve()),
            "Codec": stream["codec_name"], "SampleRate": int(stream["sample_rate"]),
            "Channels": stream["channels"], "DurationSeconds": duration,
            "Bytes": ogg.stat().st_size, "HcaSubkey": subkey,
            "FullDecodeValidated": True,
        }
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=True, indent=2))
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
