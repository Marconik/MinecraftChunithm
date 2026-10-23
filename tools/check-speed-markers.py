"""Exercise boundary merging and zero-speed recovery-marker generation."""

from speed_change_markers import build_speed_change_markers


def event(line, start, end, multiplier=0):
    return {"line": line, "tick": start, "endTick": end, "multiplier": multiplier}


def main():
    markers = build_speed_change_markers([
        event(21, 27264, 27360), event(22, 27648, 27744), event(23, 28032, 28128)
    ], lambda tick: tick * 3.125)
    assert [marker["timeMs"] for marker in markers] == [85200, 85500, 86400, 86700, 87600, 87900]
    assert all(not marker["judged"] and not marker["countsForCombo"] and not marker["countsForScore"]
               and marker["lane"] == 0 and marker["width"] == 16
               and marker["durationMs"] == 0 and marker["motion"] == "BASE_SPEED" for marker in markers)
    # Adjacent effects share one visible boundary; no event transitions are lost.
    adjacent = build_speed_change_markers([event(1, 0, 96), event(2, 96, 192, 2)], float)
    assert len(adjacent) == 3
    assert [item["phase"] for item in adjacent[1]["transitions"]] == ["END", "START"]
    assert len({marker["id"] for marker in adjacent}) == 3
    simultaneous = build_speed_change_markers([event(3, 0, 96), event(4, 0, 48, -1)], float)
    assert len(simultaneous) == 3 and len(simultaneous[0]["transitions"]) == 2
    assert build_speed_change_markers([], float) == []
    try:
        build_speed_change_markers([event(1, 96, 96)], float)
    except ValueError:
        pass
    else:
        raise AssertionError("Empty speed interval accepted")
    print("Speed markers: stop/recovery, no judgment, adjacent/overlapping boundaries and empty charts passed.")


if __name__ == "__main__":
    main()
