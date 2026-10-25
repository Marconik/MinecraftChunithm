"""Resolve SLP speed curves and SLA note groups without changing musical timestamps.

Format reference: https://github.com/beer-psi/Chunithm-Research/blob/main/Charting.md#slp
Non-zero profiles override the default curve where defined, inheriting it elsewhere.
SLA time/lane windows select complete notes; attached Air and connected paths share a curve.
"""
from collections import defaultdict
import math


def compile_profiles(controls, records, global_events, time_ms):
    curves = defaultdict(list)
    curves[0].extend(global_events)
    areas = []
    for row in controls:
        profile = row['profileId']
        if profile < 0 or profile > 65535:
            raise ValueError('Invalid scroll profile ID')
        if row['command'] == 'SLP':
            if not math.isfinite(row['multiplier']):
                raise ValueError('Non-finite scroll multiplier')
            curves[profile].append(row)
        else:
            if profile == 0:
                raise ValueError('SLA must select a non-zero profile')
            areas.append(row)
    if any(area['profileId'] not in curves for area in areas):
        raise ValueError('SLA references an undefined scroll profile')

    def flatten(profile):
        base = curves[0]
        local = curves[profile] if profile else []
        points = sorted({t for row in base + local for t in (row['tick'], row['endTick'])})
        result = []
        for a, b in zip(points, points[1:]):
            choices = [r for r in local if r['tick'] <= a < r['endTick']]
            if not choices:
                choices = [r for r in base if r['tick'] <= a < r['endTick']]
            multiplier = max(choices, key=lambda r:(r['tick'], r['line']))['multiplier'] if choices else 1.0
            if multiplier == 1:
                continue
            if result and result[-1]['endTick'] == a and result[-1]['multiplier'] == multiplier:
                result[-1].update(endTick=b, endTimeMs=time_ms(b))
            else:
                result.append(dict(tick=a, endTick=b, timeMs=time_ms(a), endTimeMs=time_ms(b), multiplier=multiplier))
        return result

    # Union only the source's explicit path connections and Air anchors, preserving
    # sustained continuity and ensuring an Air arrow cannot detach from its Tap.
    parent = {r['id']:r['id'] for r in records}
    def root(key):
        while parent[key] != key:
            parent[key] = parent[parent[key]]
            key = parent[key]
        return key
    def union(a, b):
        parent[root(a)] = root(b)
    for row in records:
        for other in row.get('predecessors', []):
            union(row['id'], other)
        for anchor in row.get('anchorCandidates', []):
            union(row['id'], anchor['id'])
    explicit = defaultdict(set)
    selected = []
    for row in records:
        matches = [a for a in areas if a['tick'] <= row['tick'] < a['endTick']
                   and a['lane'] <= row['lane'] and row['lane'] + row['width'] <= a['lane'] + a['width']]
        profiles = {a['profileId'] for a in matches}
        if len(profiles) > 1:
            raise ValueError(f"Conflicting SLA areas for {row['id']}")
        if profiles:
            profile = profiles.pop()
            explicit[root(row['id'])].add(profile)
            selected.append(dict(id=row['id'], profileId=profile))
    if any(len(values) > 1 for values in explicit.values()):
        raise ValueError('Connected note path has conflicting explicit SLA profiles')
    assignments = {r['id']:next(iter(explicit[root(r['id'])])) for r in records if explicit[root(r['id'])]}
    return dict(
        resolvedScrollEvents=flatten(0),
        scrollProfiles=[dict(profileId=profile, events=flatten(profile)) for profile in sorted(curves) if profile],
        scrollAssignments=assignments,
        scrollProfileResolution=dict(areaCount=len(areas), profileCount=len(curves), directAssignments=selected,
                                     assignedRecordCount=len(assignments), rule='HEAD_TIME_FULL_LANE_CONTAINMENT_AND_CONNECTED_PATHS'),
    )
