"""Extract the requested result-map frames into native world-display models."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parent.parent
atlas = Image.open(root / 'tools/assets/result-map-source.png').convert('RGBA')
assets = root / 'src/main/resources/assets/minecraftchunithm'
template = json.loads((assets / 'models/item/finishes/meter_polish.json').read_text())
# Atlas coordinates: yellow outlined large parallelogram and small gold bevel.
shapes = {
    'large': ((234, 20, 569, 161), [(317,21),(567,21),(482,159),(235,159)],
              [(325,38),(537,38),(473,143),(260,143)]),
    'small': ((1011, 205, 1142, 270), [(1047,207),(1139,207),(1104,267),(1013,267)],
              [(1049,219),(1111,219),(1096,246),(1034,246)]),
}
for size, (box, outside, inside) in shapes.items():
    x,y,_,_ = box
    outside = [(a-x,b-y) for a,b in outside]
    inside = [(a-x,b-y) for a,b in inside]
    art = atlas.crop(box)
    mask = Image.new('L', art.size)
    ImageDraw.Draw(mask).polygon(outside, fill=255)
    art.putalpha(mask)
    for state, color in {'empty':'#808080','blue':'#67c9ef','gold':'#f9db35'}.items():
        image = art.copy()
        ImageDraw.Draw(image).polygon(inside, fill=color)
        name = f'{size}_{state}'
        texture = assets / f'textures/item/progress_cells/{name}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        image.save(texture)
        block_name='progress_cell_'+name
        block_texture=assets/f'textures/block/progress_cells/{name}.png'
        block_texture.parent.mkdir(parents=True,exist_ok=True)
        image.resize((256,128) if size=='small' else (512,256),Image.Resampling.LANCZOS).save(block_texture)
        native={'parent':'minecraft:block/block','ambientocclusion':False,
                'textures':{'surface':f'minecraftchunithm:block/progress_cells/{name}','particle':'#surface'},
                'elements':[{'from':[0,0,0],'to':[16,16,.1],'shade':False,'light_emission':15,
                             'faces':{'south':{'uv':[0,0,16,16],'texture':'#surface'}}}]}
        for path,value in [(assets/f'models/block/{block_name}.json',native),
                           (assets/f'blockstates/{block_name}.json',{'variants':{'':{'model':f'minecraftchunithm:block/{block_name}'}}})]:
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(json.dumps(value,indent=2)+'\n')
        model = json.loads(json.dumps(template))
        model['textures']['surface']['sprite'] = f'minecraftchunithm:item/progress_cells/{name}'
        for folder, value in (
            ('models/item/progress_cells', model),
            ('items/progress_cells', {'model': {'type':'minecraft:model','model':f'minecraftchunithm:item/progress_cells/{name}'}}),
        ):
            path = assets / folder / f'{name}.json'
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value, indent=2)+'\n')
print('Generated six progress-cell appearances from the two supplied atlas frames.')
