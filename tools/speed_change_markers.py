"""Generate non-judged, full-width boundary markers for visual speed effects."""


def build_speed_change_markers(events, time_ms):
    boundaries = {}
    for event in events:
        start, end = event["tick"], event["endTick"]
        if start < 0 or end <= start:
            raise ValueError("Invalid visual speed interval")
        event_id = event.get("id", f"sfl_line_{event['line']}")
        for phase, tick in (("START", start), ("END", end)):
            boundaries.setdefault(tick, []).append({
                "eventId": event_id, "phase": phase, "sourceLine": event["line"],
            })
    markers = []
    for tick, transitions in sorted(boundaries.items()):
        markers.append({
            "id": f"speed_change_{tick}", "kind": "SPEED_CHANGE",
            "tick": tick, "timeMs": time_ms(tick), "lane": 0, "width": 16,
            "durationMs": 0, "direction": "NONE", "parentId": "", "nodes": [],
            "judged": False, "countsForCombo": False, "countsForScore": False,
            "model": "minecraftchunithm:speed_change_line",
            # Independent travel keeps the END marker moving during a zero-speed stop.
            "motion": "BASE_SPEED", "transitions": sorted(
                transitions, key=lambda item: (item["phase"] != "END", item["sourceLine"])
            ),
        })
    return markers
