"""Inspect the verified C2S 1.08/1.11/1.12/1.13/1.14 subset without discarding source records.

This is a structural analysis export, not a playable MOD chart converter.
Time conversion uses the fixed C2S coordinate grid. MET changes measure-line
display, not the absolute note clock; SFL is retained as a separate visual event.
"""

import argparse
from collections import Counter, defaultdict
from fractions import Fraction
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from speed_change_markers import build_speed_change_markers
from scroll_profiles import compile_profiles


NOTE_FIELDS = {"TAP": (4,), "MNE": (4,), "CHR": (5,), "FLK": (5,), "HLD": (5,), "HXD": (6,),
               "SLD": (7, 8), "SLC": (7, 8), "SXD": (9,), "SXC": (9,),
               **{kind: (5, 6) for kind in ("AIR", "AUL", "AUR", "ADW", "ADL", "ADR")},
               "AHD": (6, 7), "AHX": (6, 7), "ASC": (11,), "ASD": (11,), "ALD": (10, 11, 12)}
AIR_TYPES = {"AIR", "AUL", "AUR", "ADW", "ADL", "ADR"}
SLIDE_TYPES = {"SLD", "SLC", "SXD", "SXC"}
AIR_PATH_TYPES = {"ASC", "ASD"}
AIR_SUSTAIN_TYPES = {"AHD", "AHX", *AIR_PATH_TYPES}
FAMILY = {"HXD": "HLD", "SXD": "SLD", "SXC": "SLC"}
HEADER_COMMANDS = {"VERSION", "MUSIC", "SEQUENCEID", "DIFFICULT", "LEVEL", "CREATOR",
                   "BPM_DEF", "MET_DEF", "RESOLUTION", "CLK_DEF", "PROGJUDGE_BPM",
                   "PROGJUDGE_AER", "TUTORIAL"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    header, statistics, events, records = {}, {}, [], []
    unclassified, visual_controls = [], []
    counts = Counter()
    for line_number, line in enumerate(args.source.read_text(encoding="utf-8-sig").splitlines(), 1):
        if not line.strip():
            continue
        command, *fields = line.split("\t")
        fields = [field.strip() for field in fields]
        counts[command] += 1
        row = {"line": line_number, "command": command, "fields": fields}
        if command in NOTE_FIELDS:
            if len(fields) not in NOTE_FIELDS[command]:
                raise ValueError(f"Unexpected fields at line {line_number}: {line}")
            records.append(row)
        elif command in ("BPM", "MET", "SFL", "CLK", "DCM"):
            expected = 2 if command == "CLK" else 3 if command == "BPM" else 4
            if len(fields) != expected:
                raise ValueError(f"Unexpected event fields at line {line_number}: {line}")
            events.append(row)
        elif command in ("SLP", "SLA"):
            if len(fields) != (5 if command == "SLP" else 6):
                raise ValueError(f"Unexpected visual control at line {line_number}: {line}")
            visual_controls.append(row)
        elif command.startswith("T_"):
            statistics[command] = fields
        elif command in HEADER_COMMANDS:
            header[command] = fields
        else:
            unclassified.append(row)

    resolution = int(header["RESOLUTION"][0])
    if resolution != 384 or int(header["CLK_DEF"][0]) < 0:
        raise ValueError("Only the verified 384-resolution subset is supported")
    # CLK_DEF is a click cue interval, not the musical coordinate resolution.
    # Nirv's Heart uses 576 while source first/final times still match the 384 grid.
    # MET's source order is denominator, numerator. It does not rescale the
    # fixed 384-coordinate measure used by the note clock.
    if any(int(value) <= 0 for value in header["MET_DEF"]):
        raise ValueError("Invalid default display meter")

    tempo = {0: Fraction(header["BPM_DEF"][0])}
    for row in events:
        if row["command"] == "BPM":
            m, o, bpm = row["fields"]
            tempo[int(m) * resolution + int(o)] = Fraction(bpm)
    tempo = sorted(tempo.items())
    if any(bpm <= 0 for _, bpm in tempo):
        raise ValueError("Invalid tempo")

    def time_ms(tick):
        result = Fraction(0)
        for index, (start, bpm) in enumerate(tempo):
            if tick <= start:
                break
            end = min(tick, tempo[index + 1][0]) if index + 1 < len(tempo) else tick
            result += Fraction((end - start) * 240000, resolution) / bpm
        return round(float(result), 6)

    for row in events:
        fields = row["fields"]
        measure, offset = map(int, fields[:2])
        if measure < 0 or not 0 <= offset < resolution:
            raise ValueError(f"Invalid event position at line {row['line']}")
        tick = measure * resolution + offset
        row.update(tick=tick, timeMs=time_ms(tick))
        if row["command"] == "MET":
            denominator, numerator = map(int, fields[2:])
            # A zero numerator disables subsequent measure lines; it is not a clock change.
            if denominator <= 0 or numerator < 0:
                raise ValueError("Invalid display meter")
            row.update(numerator=numerator, denominator=denominator)
        elif row["command"] in ("SFL", "DCM"):
            row["id"] = f"{row['command'].lower()}_line_{row['line']}"
            duration = int(fields[2])
            multiplier = Fraction(fields[3])
            if duration <= 0:
                raise ValueError("Invalid scroll event duration")
            end = tick + duration
            row.update(durationTicks=duration, endTick=end, endTimeMs=time_ms(end),
                       durationMs=round(time_ms(end) - time_ms(tick), 6),
                       multiplier=float(multiplier))

    # Preserve source controls, then resolve profile curves and note assignments.
    for row in visual_controls:
        fields = row['fields']
        measure, offset = map(int, fields[:2])
        duration = int(fields[2] if row['command'] == 'SLP' else fields[4])
        if measure < 0 or not 0 <= offset < resolution or duration <= 0:
            raise ValueError('Invalid SLP/SLA bounds')
        tick = measure * resolution + offset
        row.update(tick=tick,timeMs=time_ms(tick),endTick=tick+duration,endTimeMs=time_ms(tick+duration))
        if row['command'] == 'SLP':
            row.update(multiplier=float(Fraction(fields[3])),profileId=int(fields[4]))
        else:
            lane,width,profile=map(int,(fields[2],fields[3],fields[5]))
            if not 0 <= lane < 16 or not 0 < width <= 16-lane:
                raise ValueError('Invalid SLA lane bounds')
            row.update(lane=lane,width=width,profileId=profile)

    anchors = defaultdict(list)
    slide_segments, air_holds = [], []
    for row in records:
        kind, fields = row["command"], row["fields"]
        measure, offset, lane, width = map(int, fields[:4])
        # Source coordinates can lie outside the playfield (Melodiniq ULTIMA).
        # Keep them intact through export and rendering, including path endpoints.
        if not (0 < width <= 16 and measure >= 0 and 0 <= offset < resolution):
            raise ValueError(f"Invalid time or lane bounds at line {row['line']}")
        tick = measure * resolution + offset
        row.update(id=f"c2s_line_{row['line']}", tick=tick, timeMs=time_ms(tick), lane=lane, width=width)
        end_tick, end_lane, end_width = tick, lane, width
        if kind in {"HLD", "HXD", *SLIDE_TYPES, *AIR_SUSTAIN_TYPES, "ALD"}:
            duration = int(fields[6] if kind in AIR_PATH_TYPES or kind == "ALD" else fields[5] if kind in {"AHD", "AHX"} else fields[4])
            if duration <= 0:
                raise ValueError("Non-positive sustain duration")
            end_tick = tick + duration
            if kind in SLIDE_TYPES or kind in AIR_PATH_TYPES or kind == "ALD":
                end_lane, end_width = map(int, fields[7:9] if kind in AIR_PATH_TYPES or kind == "ALD" else fields[5:7])
                if not (0 < end_width <= 16):
                    raise ValueError("Invalid segment endpoint")
            row.update(endTick=end_tick, endTimeMs=time_ms(end_tick),
                       durationTicks=duration, durationMs=round(time_ms(end_tick) - time_ms(tick), 6),
                       endLane=end_lane, endWidth=end_width)
        if kind in AIR_PATH_TYPES or kind == "ALD":
            row.update(height=float(fields[5]), endHeight=float(fields[9]), color=fields[10] if len(fields)>10 else "DEF")
            if row["height"] < 0 or row["endHeight"] < 0:
                raise ValueError("Negative air path height")
            if kind == "ALD":
                row["crushTick"] = int(fields[4])
                if row["crushTick"] < 0:
                    raise ValueError("Invalid ALD action interval")
        if kind not in AIR_TYPES and kind != "ALD":
            anchor_kind = FAMILY.get(kind, kind)
            anchors[(anchor_kind, tick, lane, width)].append({"id": row["id"], "part": "START"})
            if end_tick > tick:
                anchors[(anchor_kind, end_tick, end_lane, end_width)].append({"id": row["id"], "part": "END"})
                # A visible Slide node can be followed by an SLC geometry segment.
                if anchor_kind == "SLC":
                    anchors[("SLD", end_tick, end_lane, end_width)].append({"id": row["id"], "part": "CONTROL_END"})
        if kind in SLIDE_TYPES:
            slide_segments.append(row)
        elif kind in AIR_SUSTAIN_TYPES:
            air_holds.append(row)

    slide_starts, slide_ends = defaultdict(list), defaultdict(list)
    for row in slide_segments:
        slide_starts[(row["tick"], row["lane"], row["width"])].append(row["id"])
        slide_ends[(row["endTick"], row["endLane"], row["endWidth"])].append(row["id"])
    # Coincident paths may contain more starts than incoming segments. Preserve
    # the extra new head instead of merging it into an already-running slide.
    # Source-order pairing resolves the excess; source T_NOTE_SLD verifies it.
    extra_heads = set()
    for key, outgoing in slide_starts.items():
        incoming = slide_ends.get(key, [])
        if incoming and len(outgoing) > len(incoming):
            extra_heads.update(outgoing[len(incoming):])
    roots = []
    ambiguous = []
    for row in slide_segments:
        row["predecessors"] = [] if row["id"] in extra_heads else slide_ends.get((row["tick"], row["lane"], row["width"]), [])
        row["successors"] = [key for key in slide_starts.get((row["endTick"], row["endLane"], row["endWidth"]), []) if key not in extra_heads]
        if not row["predecessors"]:
            roots.append(row["id"])
        if len(row["predecessors"]) > 1 or len(row["successors"]) > 1:
            ambiguous.append(row["id"])

    air_matches = Counter()
    for row in records:
        if row["command"] not in AIR_TYPES | AIR_SUSTAIN_TYPES:
            continue
        target_type = row["fields"][4]
        matches = anchors.get((target_type, row["tick"], row["lane"], row["width"]), [])
        matches = [match for match in matches if match["id"] != row["id"]]
        row["anchorCandidates"] = matches
        air_matches["matched" if matches else "unresolved"] += 1
    for row in air_holds:
        row["root"] = row["fields"][4] not in AIR_SUSTAIN_TYPES
        row["terminal"] = not any(
            other["id"] != row["id"] and other["tick"] == row["endTick"]
            and other["lane"] == row["endLane"] and other["width"] == row["endWidth"]
            and other["fields"][4] == row["command"] for other in air_holds
        )
    # ALD records with a positive action interval contribute to source first/final
    # statistics regardless of colour (2579 ends with DEF; 2838 ends with NON).
    # Interval-zero trails remain visual-only and may extend beyond T_FINAL_RES.
    playable = [row for row in records if row["command"] != "ALD" or row.get("crushTick", 0) > 0]
    first_tick = min(row["tick"] for row in playable)
    # ALD may visually continue beyond its last sampled action. A one-tick
    # tail shorter than crushTick contributes only its start (Spider's Thread).
    last_tick = max(row["tick"] + row["durationTicks"] // row["crushTick"] * row["crushTick"]
                    if row["command"] == "ALD" else row.get("endTick", row["tick"]) for row in playable)
    ahd_starts = sum(row["fields"][4] not in AIR_SUSTAIN_TYPES for row in air_holds)
    logical_starts = sum(counts[kind] for kind in {"TAP", "CHR", "MNE", "FLK", "HLD", "HXD", *AIR_TYPES}) + len(roots) + ahd_starts
    # Newer formats include *C path controls in T_REC_SLD; legacy versions do not.
    modern = tuple(map(int, header["VERSION"][0].split("."))) >= (1, 11, 0)
    record_families = {"TAP": counts["TAP"], "CHR": counts["CHR"], "FLK": counts["FLK"], "MNE": counts["MNE"],
                       "HLD": counts["HLD"] + counts["HXD"],
                       "SLD": sum(counts[kind] for kind in SLIDE_TYPES if modern or kind.endswith("D")),
                       "AIR": sum(counts[kind] for kind in AIR_TYPES), "AHD": counts["AHD"] + counts["ASD"]}
    checks = {
        "firstTick": first_tick == int(statistics["T_FIRST_RES"][0]),
        "lastTick": last_tick == int(statistics["T_FINAL_RES"][0]),
        "firstTimeWithinOneMs": abs(time_ms(first_tick) - int(statistics["T_FIRST_MSEC"][0])) < 1,
        "lastTimeWithinOneMs": abs(time_ms(last_tick) - int(statistics["T_FINAL_MSEC"][0])) < 1,
        "slideStartCount": len(roots) == int(statistics["T_NOTE_SLD"][0]),
        "airHoldStartCount": ahd_starts == int(statistics["T_NOTE_AHD"][0]),
        "logicalStartCount": logical_starts == int(statistics["T_NOTE_ALL"][0]),
        "recordCountExcludingControls": sum(record_families.values()) == int(statistics["T_REC_ALL"][0]),
        "recordFamilyCounts": all(value == int(statistics[f"T_REC_{kind}"][0]) for kind, value in record_families.items()),
        "allAirHaveAnchorCandidates": air_matches["unresolved"] == 0,
    }
    if not all(checks.values()):
        raise ValueError(f"Source statistic checks failed: {checks}")
    profiles = compile_profiles(visual_controls, records,
                                [row for row in events if row['command'] == 'SFL'], time_ms)
    identity = None
    music_xml = args.source.parent / "Music.xml"
    if music_xml.exists():
        xml = ET.parse(music_xml).getroot()
        matches = [item for item in xml.findall(".//MusicFumenData")
                   if item.findtext("file/path") == args.source.name]
        if len(matches) == 1:
            fumen = matches[0]
            level = Fraction(fumen.findtext("level", "0")) + Fraction(fumen.findtext("levelDecimal", "0")) / 100
            identity = {
                "source": str(music_xml), "musicId": int(xml.findtext("name/id")),
                "title": xml.findtext("name/str"), "difficulty": fumen.findtext("type/str"),
                "difficultyId": int(fumen.findtext("type/id")), "internalLevel": float(level),
                "enabled": fumen.findtext("enable") == "true",
                "headerMusic": header.get("MUSIC"), "headerDifficulty": header.get("DIFFICULT"),
            }
    outside = [row for row in records if min(row['lane'], row.get('endLane', row['lane'])) < 0
               or max(row['lane'] + row['width'], row.get('endLane', row['lane']) + row.get('endWidth', row['width'])) > 16]
    spatial = [row for row in records if 'height' in row]
    report = {
        "purpose": "structural-analysis-only", "source": str(args.source),
        "identity": identity, "header": header,
        "tempoEvents": [row for row in events if row["command"] == "BPM"],
        "meterEvents": [row for row in events if row["command"] == "MET"],
        "clockEvents": [row for row in events if row["command"] == "CLK"],
        "scrollEvents": [row for row in events if row["command"] == "SFL"],
        "dynamicScrollEvents": [row for row in events if row["command"] == "DCM"],
        "speedChangeMarkers": build_speed_change_markers(
            [row for row in events if row["command"] == "SFL"], time_ms),
        "unclassifiedRecords": unclassified, "sourceStatistics": statistics,
        "sourceVisualControls": visual_controls,
        "visualWarnings": (["DCM per-note scroll controls are preserved in sourceDynamicScrollEvents but are not rendered."]
                           if any(row["command"] == "DCM" for row in events) else []),
        **profiles,
        "recordCounts": dict(sorted(counts.items())), "noteRecordCountIncludingControls": len(records),
        "slide": {"segments": len(slide_segments), "rootSegments": len(roots),
                  "ambiguousSegmentIds": ambiguous, "rootIds": roots},
        "airHoldStartTargetCounts": dict(Counter(row["fields"][4] for row in air_holds)),
        "airAnchorMatches": dict(air_matches),
        "validation": checks,
        "timing": {"firstTick": first_tick, "firstTimeMs": time_ms(first_tick),
                   "lastTick": last_tick, "lastTimeMs": time_ms(last_tick),
                   "visualLastTimeMs": max(row.get("endTimeMs", row["timeMs"]) for row in records)},
        "records": records,
        "spatialSummary": {
            "outsideRecordCount": len(outside),
            "outsideTypes": dict(sorted(Counter(row['command'] for row in outside).items())),
            "minLeft": min(min(row['lane'], row.get('endLane', row['lane'])) for row in records),
            "maxRight": max(max(row['lane'] + row['width'], row.get('endLane', row['lane']) + row.get('endWidth', row['width'])) for row in records),
            "widthChangeRecords": sum(row['width'] != row.get('endWidth', row['width']) for row in records),
            "outsideWidthChangeRecords": sum(row['width'] != row.get('endWidth', row['width']) for row in outside),
            "minHeight": min((min(row['height'], row['endHeight']) for row in spatial), default=0),
            "maxHeight": max((max(row['height'], row['endHeight']) for row in spatial), default=0),
        },
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({
        "noteRecords": len(records), "slideSegments": len(slide_segments),
        "slideRoots": len(roots), "ambiguousSlideSegments": len(ambiguous),
        "tempoEvents": len(report["tempoEvents"]), "meterEvents": len(report["meterEvents"]),
        "scrollEvents": len(report["scrollEvents"]), "unclassifiedRecords": len(unclassified),
        "speedChangeMarkers": len(report["speedChangeMarkers"]),
        "airAnchorMatches": dict(air_matches), "timing": report["timing"],
        "validation": checks,
    }, indent=2))
    print(f"Report: {args.output}")


if __name__ == "__main__":
    main()
