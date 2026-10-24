"""Generate real rear recesses and supplied artwork for both side panels."""
import json
from pathlib import Path
from PIL import Image

root=Path(__file__).resolve().parent.parent
assets=root/'src/main/resources/assets/minecraftchunithm'
def save(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n')
textures=json.loads((assets/'models/block/song_panel.json').read_text())['textures']
def box(x0,y0,z0,x1,y1,z1):
    if x0>=x1 or y0>=y1 or z0>=z1:return []
    return [{'from':[x0,y0,z0],'to':[x1,y1,z1],
             'faces':{f:{'texture':'#'+f,'uv':[0,0,16,16]} for f in ('north','south','east','west','up','down')}}]
for panel,art,width in [('name_panel','player',1.75),('song_panel','song',2.1)]:
    source=Image.open(root/f'tools/assets/{art}-rear-logo.png')
    path=assets/f'textures/block/{art}_control_logo.png'
    source.save(path)
    save(assets/f'blockstates/{art}_control_logo.json',{'variants':{'':{'model':f'minecraftchunithm:block/{art}_control_logo'}}})
    save(assets/f'models/block/{art}_control_logo.json',{
        'textures':{'particle':f'minecraftchunithm:block/{art}_control_logo','logo':f'minecraftchunithm:block/{art}_control_logo'},
        'elements':[{'from':[0,0,0],'to':[16,16,.1],'shade':False,'faces':{'north':{'uv':[0,0,16,16],'texture':'#logo'}}}]})
    variants={}
    for col in range(7):
        x0=max(0,(3.5-width/2-col)*16);x1=min(16,(3.5+width/2-col)*16)
        model=f'{panel}_terminal_{col}'
        if x0<x1:
            elements=box(0,0,2,16,16,16)+box(0,0,0,x0,16,2)+box(x1,0,0,16,16,2)
            save(assets/f'models/block/{model}.json',{'parent':'minecraft:block/block','ambientocclusion':False,'textures':textures,'elements':elements})
        else:model=panel
        for facing,angle in [('south',0),('west',90),('north',180),('east',270)]:
            for terminal in (False,True):
                variants[f'facing={facing},column={col},terminal={str(terminal).lower()}']={
                    'model':f'minecraftchunithm:block/{model if terminal else panel}','y':angle}
    save(assets/f'blockstates/{panel}.json',{'variants':variants})
print('Generated side-panel rear artwork and three-column, one-block-high inset terminals.')
