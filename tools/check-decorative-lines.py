"""Verify the actual decoration pixels and translucent geometry independently."""
import json
from pathlib import Path
from PIL import Image

assets = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/minecraftchunithm'
air = json.loads((assets / 'models/item/chart_air_body.json').read_text())
with Image.open(assets / 'textures/block/air_action_line.png') as image:
    alpha = image.convert('RGBA').getchannel('A')

models = list((assets / 'models/item').glob('chart_effect_*_body.json'))
expected_colors = {'gry', 'aqa', 'red', 'blk', 'def', 'dgr', 'grn', 'pnk', 'cyn', 'blu', 'orn', 'yel', 'ppl', 'vlt', 'lim'}
assert {path.stem.removeprefix('chart_effect_').removesuffix('_body') for path in models} == expected_colors
for path in models:
    model = json.loads(path.read_text())
    assert model['textures']['effect']['force_translucent'] is True
    expected = json.loads(json.dumps(air['elements']).replace('#air_glow', '#effect'))
    assert model['elements'] == expected, path
    assert all(not element['shade'] and element['light_emission'] == 15 for element in model['elements'])
    sprite = model['textures']['effect']['sprite']
    assert sprite.startswith('minecraftchunithm:block/'), 'Mixed-atlas material'
    with Image.open(assets / f'textures/{sprite.split(":",1)[1]}.png') as texture:
        texture = texture.convert('RGBA')
        assert texture.size == alpha.size
        assert texture.getchannel('A').tobytes() == alpha.tobytes(), path
        assert all(low == high for low, high in texture.convert('RGB').getextrema()), 'RGB gradient is not allowed'
        assert texture.getchannel('A').getextrema() == (0,204), 'Missing translucent glow'
        if path.stem == 'chart_effect_lim_body':
            assert texture.getpixel((0, 0))[:3] == (180, 255, 60), 'Wrong LIM colour'
print(f'All {len(models)} ALD colors: solid RGB, exact Air opacity, crossed sheets and fullbright materials passed.')
