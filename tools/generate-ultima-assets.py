"""Build the ULTIMA panel background."""
import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/minecraftchunithm'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

# Keep the complete supplied design, including both pink corner treatments.
# It covers a 7 x 3 panel; existing content is drawn on top of this base.
with Image.open(root / 'tools/assets/song-ultima-background.png') as source:
    image = source.convert('RGBA').resize((1792, 768), Image.Resampling.LANCZOS)
    image.save(assets / 'textures/block/song_ultima_background.png')
name = 'song_ultima_background'
write(assets / f'models/block/{name}.json', {
    'parent':'minecraft:block/block','ambientocclusion':False,
    'textures':{'surface':f'minecraftchunithm:block/{name}','particle':'#surface'},
    'elements':[{'from':[0,0,0],'to':[16,16,.1],'shade':False,'light_emission':15,
                 'faces':{'south':{'uv':[0,0,16,16],'texture':'#surface'}}}],
})
write(assets / f'blockstates/{name}.json', {'variants':{'':{'model':f'minecraftchunithm:block/{name}'}}})

print('Generated ULTIMA background. Decoration assets use decorative-line-models.cjs.')
