"""Verify Melodiniq source fidelity, original off-track coordinates and mine models."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/minecraftchunithm'
SONGS = ROOT / 'src/main/resources/data/minecraftchunithm/songs/2999'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


for difficulty, suffix, expected, mines in [('master', '03', 3333, 0), ('ultima', '04', 6166, 814)]:
    chart = read(SONGS / f'{difficulty}.json')
    analysis = read(ROOT / f'build/chart-analysis/2999_{suffix}.analysis.json')
    source = {row['id']: row for row in analysis['records']}
    assert all(analysis['validation'].values())
    assert len(chart['notes']) == len(source) == expected
    assert sum(note['command'] == 'MNE' for note in chart['notes']) == mines
    outside = 0
    for note in chart['notes']:
        row = source[note['id']]
        assert note['sourceFields'] == row['fields']
        assert note['timeMs'] == row['timeMs']
        assert note['width'] == row['width']
        assert note['scrollProfile'] == analysis['scrollAssignments'].get(note['id'], 0)
        assert note['lane'] == row['lane']
        assert note.get('sourceLane', note['lane']) == row['lane']
        if 'endTimeMs' in row:
            assert note['endTimeMs'] == row['endTimeMs']
            assert note['endWidth'] == row['endWidth']
            assert note['endLane'] == row['endLane']
            assert note.get('sourceEndLane', note['endLane']) == row['endLane']
        flagged = row['lane'] < 0 or row['lane'] + row['width'] > 16
        flagged |= row.get('endLane', row['lane']) < 0 or row.get('endLane', row['lane']) + row.get('endWidth', row['width']) > 16
        assert bool(note.get('sourceOutOfBounds')) == flagged
        outside += flagged
    assert chart['outOfBoundsPolicy']['recordCount'] == outside
    assert chart['outOfBoundsPolicy']['mode'] == 'PRESERVE_SOURCE_COORDINATES'
    assert outside == (2559 if difficulty == 'ultima' else 0)
    print(f'{difficulty}: {expected} records, {mines} mines, {outside} original off-track paths rendered without clamping.')

for width in range(1, 17):
    model = read(ASSETS / f'models/item/mine_{width}.json')
    core = model['elements'][0]
    assert core['to'][1] - core['from'][1] == core['to'][2] - core['from'][2] == 3.5
    assert all(e['light_emission'] == 15 and not e['shade'] for e in model['elements'])
    assert model['textures']['mine_glow']['force_translucent']
    assert model['display']['fixed']['scale'][0] == width / 4
    assert read(ASSETS / f'items/mine_{width}.json')['model']['model'].endswith(f'/mine_{width}')
print('All 16 mine widths have square emissive cores, bounded widths and translucent halos.')
