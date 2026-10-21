// Native cuboid meshes: no external image or model libraries required.
module.exports=function({box,texture,textures,json,write,assets,ns,en,zh}) {
  const fs=require('node:fs'),path=require('node:path');
  // Retire the previous width-dependent action resources, including stale files.
  for(const folder of ['models/item','items']) {
    const directory=path.resolve(__dirname,`../src/main/resources/${assets}/${folder}`);
    for(const file of fs.readdirSync(directory))if(/^air_action(?:_parabola_(?:up|down))?_\d+\.json$/.test(file))
      fs.unlinkSync(path.join(directory,file));
  }
  const colors={ex_gold:[[255,250,140],[255,181,24]],hold_yellow:[[255,229,0],[177,83,58]],
    slide_cyan:[[31,242,255],[31,121,182]],slide_blue:[[120,169,255],[54,61,217]],
    flick_blue:[[113,224,255],[34,120,218]],air_green:[[151,255,69],[12,190,38]],
    action_purple:[[255,139,244],[194,29,212]]};
  for(const [name,[a,b]] of Object.entries(colors)) {texture(name,a,b);textures[name]=`${ns}:block/${name}`;}
  // Across-ribbon gradient, constant along the travel direction.
  for(let band=0;band<16;band++) {
    const brightness=1-Math.abs((band+.5)/16*2-1);
    const color=[Math.round(12+20*brightness),Math.round(45+190*brightness),Math.round(100+155*brightness)];
    texture(`slide_band_${band}`,color);textures[`slide_band_${band}`]=`${ns}:block/slide_band_${band}`;
  }
  const kinds={ex_tap:'EX-Tap',hold:'Hold',slide:'Slide',flick:'Flick',air:'Air',air_action:'Air-Action'};
  const variants=Object.entries(kinds).map(([kind,label])=>({kind,label,variant:''}));
  for(const kind of ['slide','air_action'])for(const variant of ['parabola_up','parabola_down'])
    variants.push({kind,label:kinds[kind],variant});
  for(const variant of ['up_left','up_right','down','down_left','down_right'])variants.push({kind:'air',label:'Air',variant});
  for(const {kind,label,variant} of variants)for(let lanes=1;lanes<=16;lanes++) {
    if(kind==='air_action' && lanes!==2)continue;
    const tap=JSON.parse(require('node:fs').readFileSync(require('node:path').resolve(__dirname,
      `../src/main/resources/${assets}/models/item/tap_${lanes}.json`),'utf8'));
    const scale=lanes/4, inset=n=>n/scale, l=-8,r=24;
    let elements=[];
    const bar=(z,color,y=8)=>[
      box([l,y,z-2],[r,y+1.5,z+2],'white',12),
      box([l+inset(.75),y+1.5,z-1.5],[r-inset(.75),y+2,z+1.5],color,12)
    ];
    if(kind==='ex_tap') {
      elements=tap.elements.map(e=>({...e,faces:Object.fromEntries(Object.entries(e.faces).map(([f,v])=>
        [f,{...v,texture:v.texture==='#tap_red'?'#ex_gold':v.texture}]))}));
    }
    if(kind==='hold') {
      elements=[box([l+inset(.5),8.1,-16],[r-inset(.5),8.3,8],'hold_yellow',10),
        box([l+inset(1),8.31,-16],[r-inset(1),8.4,8],'ex_gold',12),
        ...bar(10,'ex_gold'),...bar(-14,'ex_gold')];
    }
    if(kind==='slide') {
      // Two-node sampled ribbon. Curved examples use a narrower ribbon to keep
      // the complete path inside the item's footprint, including wide variants.
      const progress=t=>variant==='parabola_up'?2*t-t*t:variant==='parabola_down'?t*t:t;
      for(let i=0;i<64;i++) {
        const z=-16+i*.375, t=(i+.5)/64;
        const centre=variant?16*progress(t):8, half=variant?7:16;
        for(let band=0;band<16;band++)elements.push(box(
          [centre-half+half*2*band/16,8.1,z],
          [centre-half+half*2*(band+1)/16,8.3,z+.375],`slide_band_${band}`,10));
        elements.push(box([centre-.2,8.31,z],[centre+.2,8.4,z+.375],'slide_cyan',15));
      }
      if(!variant)elements.push(...bar(10,'slide_blue'),...bar(-14,'slide_blue'));
      else for(const [centre,z] of [[0,-15.75],[16,7.75]]) {
        elements.push(box([centre-7,8.4,z-.2],[centre+7,9,z+.2],'white',12));
        elements.push(box([centre-6.5,9,z-.15],[centre+6.5,9.4,z+.15],'slide_blue',12));
      }
    }
    if(kind==='flick') {
      elements=bar(8,'flick_blue');
      for(let i=0;i<8;i++) {
        const x=l+2+i*3.5, d=Math.abs(i-3.5)*.38;
        elements.push(box([x,10.05,7.7-d],[x+2,10.15,8.3+d],'highlight',15));
      }
    }
    if(kind==='air') {
      elements=tap.elements.map(e=>({...e,faces:Object.fromEntries(Object.entries(e.faces).map(([f,v])=>
        [f,{...v,texture:v.texture==='#tap_red'?'#ex_gold':v.texture}]))}));
      const down=variant.startsWith('down'), side=variant.endsWith('left')?-1:variant.endsWith('right')?1:0;
      // A thin upright sheet, not a row of thick floating blocks. The silhouette
      // has a broad arrowhead, upright shoulders and a V-shaped lower cut-out.
      // Reflection creates the down marker; shear produces the diagonal variants.
      const shape=[[-1,.18],[-1,.72],[0,1],[1,.72],[1,.18],[0,.46]];
      const transform=([x,y])=>{
        if(down)y=1-y;
        return [(side?.72: .94)*x+side*.22*(2*y-1),y];
      };
      const outer=shape.map(transform);
      const inner=shape.map(([x,y])=>transform([x*.91,.57+(y-.57)*.83]));
      function span(polygon,x) {
        const hits=[];
        for(let i=0;i<polygon.length;i++) {
          const a=polygon[i],b=polygon[(i+1)%polygon.length];
          if((a[0]<=x&&x<b[0])||(b[0]<=x&&x<a[0]))
            hits.push(a[1]+(b[1]-a[1])*(x-a[0])/(b[0]-a[0]));
        }
        return hits.length>=2?[Math.min(...hits),Math.max(...hits)]:null;
      }
      for(let i=0;i<128;i++) {
        const x=-1+(i+.5)/64, from=l+i*.25,to=from+.25;
        for(const [polygon,color,front,back] of [
          [outer,'white',7.9375,8.0625],
          [inner,down?'action_purple':'air_green',7.925,8.075]]) {
          const range=span(polygon,x);
          if(range && range[1]>range[0])elements.push(box(
            [from,11+range[0]*13,front],[to,11+range[1]*13,back],color,15));
        }
      }
    }
    if(kind==='air_action') {
      for(let i=0;i<64;i++) {
        const t=(i+.5)/64, u=variant==='parabola_up'?2*t-t*t:variant==='parabola_down'?t*t:t;
        const centre=variant?16*u:8, z=-14+i*.3125;
        // FIXED X scale is .5. Width 2.4*.5 and height 1.2 model units
        // produce a square .075-block section: 3x edge length, 9x area.
        elements.push(box([centre-1.2,20,z],[centre+1.2,21.2,z+.3125],'air_green',15));
      }
      const ends=variant?[[0,-14],[16,6]]:[[8,-14],[8,6]];
      // 32 model units * .5 / 16 = 1 block, independent of green-line thickness.
      for(const [centre,z] of ends) {
        elements.push(box([centre-16,20,z-2],[centre+16,21.5,z+2],'white',12));
        elements.push(box([centre-14.5,21.5,z-1.5],[centre+14.5,22,z+1.5],'action_purple',15));
      }
    }
    const id=`${kind}${variant?'_'+variant:''}_${lanes}`;
    json(`${assets}/models/item/${id}.json`,{...tap,textures:{...textures,particle:
      textures[kind==='air'?'air_green':kind==='air_action'?'action_purple':kind==='slide'?'slide_cyan':
      kind==='flick'?'flick_blue':'ex_gold']},elements});
    json(`${assets}/items/${id}.json`,{model:{type:'minecraft:model',model:`${ns}:item/${id}`}});
    en[`item.${ns}.${id}`]=`${label} ${variant||'linear/up'} · ${lanes} lanes`;
    zh[`item.${ns}.${id}`]=`${label} ${variant||'直线/上抬'} · ${lanes} 分区`;
  }
  write(`data/${ns}/function/note_showcase.mcfunction`,`execute align xyz run function ${ns}:note_showcase_aligned\n`);
  const commands=['# Replaces a 9 x 96 footprint; 6 types, each in 5 representative widths.','chunithm track 96'];
  let row=0;
  for(const kind of Object.keys(kinds))for(const lanes of kind==='air_action'?[2]:[1,2,4,8,16]) {
    commands.push(`summon minecraft:item_display ~4.5 ~-0.359375 ~${row++*3+2} {Tags:["minecraftchunithm.tap_preview"],item:{id:"${ns}:${kind}_${lanes}",count:1},item_display:"fixed",brightness:{block:15,sky:15},width:9.0f,height:3.0f}`);
  }
  write(`data/${ns}/function/note_showcase_aligned.mcfunction`,commands.join('\n')+'\n');
  const samples=['chunithm track 48'];
  variants.filter(v=>['slide','air_action','air'].includes(v.kind)).forEach((v,i)=>{
    const id=`${v.kind}${v.variant?'_'+v.variant:''}_${v.kind==='air_action'?2:4}`;
    samples.push(`summon minecraft:item_display ~4.5 ~-0.359375 ~${i*3+2} {Tags:["minecraftchunithm.tap_preview"],item:{id:"${ns}:${id}",count:1},item_display:"fixed",brightness:{block:15,sky:15},width:9.0f,height:3.0f}`);
  });
  write(`data/${ns}/function/path_showcase.mcfunction`,`execute align xyz run function ${ns}:path_showcase_aligned\n`);
  write(`data/${ns}/function/path_showcase_aligned.mcfunction`,samples.join('\n')+'\n');
  const airSamples=['chunithm track 32'];
  const airIds=['air_4','air_down_4','air_up_left_4','air_down_left_4','air_up_right_4','air_down_right_4',
    'air_action_2','air_action_parabola_up_2','air_action_parabola_down_2'];
  airIds.forEach((id,i)=>airSamples.push(
    `summon minecraft:item_display ~${i%2?6.5:2.5} ~-0.359375 ~${Math.floor(i/2)*4+2} {Tags:["minecraftchunithm.tap_preview"],item:{id:"${ns}:${id}",count:1},item_display:"fixed",brightness:{block:15,sky:15},width:4.0f,height:3.0f}`));
  write(`data/${ns}/function/air_showcase.mcfunction`,`execute align xyz run function ${ns}:air_showcase_aligned\n`);
  write(`data/${ns}/function/air_showcase_aligned.mcfunction`,airSamples.join('\n')+'\n');
};
