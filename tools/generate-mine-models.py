"""Deterministic blue/violet emissive mine geometry, including a soft translucent halo."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/minecraftchunithm'


def write(path, value):
    (ROOT / path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def box(start, end, texture):
    return dict(from_=start, to=end, shade=False, light_emission=15,
                faces={side: dict(uv=[0, 0, 16, 16], texture='#' + texture)
                       for side in ('up', 'down', 'north', 'south', 'east', 'west')})


def generate():
    for name, rgb in dict(mine_blue=(58, 149, 255), mine_violet=(124, 75, 255),
                          mine_light=(164, 218, 255)).items():
        Image.new('RGBA', (16, 16), (*rgb, 255)).save(ROOT / f'textures/block/{name}.png')
    image = Image.new('RGBA', (64, 64))
    for y in range(64):
        for x in range(64):
            edge = max(abs((x + .5) / 32 - 1), abs((y + .5) / 32 - 1))
            image.putpixel((x, y), (102, 100, 255, round(65 * max(0, 1 - edge) ** 1.8)))
    image.save(ROOT / 'textures/block/mine_glow.png')
    for width in range(1, 17):
        base = json.loads((ROOT / f'models/item/tap_{width}.json').read_text(encoding='utf-8'))
        # FIXED X scale is width/4. Side cross-section: 3.5 x 3.5 pixels,
        # compared with Tap's 2.125-pixel height. Halo stays in its lane footprint.
        inset = .7 / (width / 4)
        elements = [box([-8, 8, 6.25], [24, 11.5, 9.75], 'mine_violet'),
                    box([-8 + inset, 8.35, 6.24], [24 - inset, 11.2, 9.76], 'mine_blue'),
                    box([-8 + inset, 11.51, 6.5], [24 - inset, 11.58, 7], 'mine_light'),
                    box([-8 + inset, 11.51, 9], [24 - inset, 11.58, 9.5], 'mine_light'),
                    box([-8, 7.75, 4], [24, 13.75, 12], 'mine_glow')]
        for element in elements:
            element['from'] = element.pop('from_')
        base['elements'] = elements
        base['textures'] = {name: f'minecraftchunithm:block/{name}'
                            for name in ('mine_blue', 'mine_violet', 'mine_light')}
        base['textures']['mine_glow'] = dict(sprite='minecraftchunithm:block/mine_glow', force_translucent=True)
        base['textures']['particle'] = 'minecraftchunithm:block/mine_blue'
        name = f'mine_{width}'
        write(f'models/item/{name}.json', base)
        write(f'items/{name}.json', dict(model=dict(type='minecraft:model', model=f'minecraftchunithm:item/{name}')))
    for language, label in [('en_us', 'Mine'), ('zh_cn', '地雷键')]:
        path = f'lang/{language}.json'
        values = json.loads((ROOT / path).read_text(encoding='utf-8'))
        for width in range(1, 17):
            values[f'item.minecraftchunithm.mine_{width}'] = f'{label} {width}'
        write(path, values)
    print('Generated 16 emissive mine widths with square blue/violet cores and translucent halos.')


if __name__ == '__main__':
    generate()
