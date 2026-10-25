// Native, alpha-blended crossed sheets for the supplied Air Hold line texture.
const fs=require('node:fs'),path=require('node:path');
function generate(){
  const root=path.resolve(__dirname,'../src/main/resources/assets/minecraftchunithm');
  const models=path.join(root,'models/item');
  const read=name=>JSON.parse(fs.readFileSync(path.join(models,name+'.json'),'utf8'));
  const write=(name,model)=>fs.writeFileSync(path.join(models,name+'.json'),JSON.stringify(model,null,2)+'\n');
  const sprite='minecraftchunithm:block/air_action_line';
  const face=(rotation=0)=>({uv:[0,0,16,16],texture:'#air_glow',...(rotation?{rotation}:{})});
  const sheets=(x,y,z0,z1,halfX,halfY)=>[
    {from:[x-halfX,y,z0],to:[x+halfX,y,z1],shade:false,light_emission:15,faces:{up:face(),down:face()}},
    {from:[x,y-halfY,z0],to:[x,y+halfY,z1],shade:false,light_emission:15,faces:{east:face(90),west:face(90)}}
  ];
  const glow={sprite,force_translucent:true};
  write('chart_air_body',{parent:'minecraft:block/block',ambientocclusion:false,
    textures:{air_glow:glow,particle:sprite},elements:sheets(8,8,0,16,8,8),display:{fixed:{scale:[1,1,1]}}});
  // Keep the tap at the base; extend only the upright direction marker.
  for(const file of fs.readdirSync(models)){
    const match=file.match(/^(?:air|chart_air_arrow)(?:_(up_left|up_right|down|down_left|down_right))?_(\d+)\.json$/);
    if(!match)continue;
    const model=read(file.slice(0,-5));
    const maximum=Math.max(...model.elements.map(e=>e.to[1]));
    if(maximum<(match[1]?.startsWith('down')?24:27)){
      for(const element of model.elements)if(element.from[1]>=11){
        element.from[1]=11+(element.from[1]-11)*17.5/13;
        element.to[1]=11+(element.to[1]-11)*17.5/13;
      }
      write(file.slice(0,-5),model);
    }
  }
  // The handheld linear/parabolic examples use the same luminous line at +1 block.
  for(const name of ['air_action_2','air_action_parabola_up_2','air_action_parabola_down_2']){
    const model=read(name),variant=name.includes('parabola_up')?'up':name.includes('parabola_down')?'down':'';
    const bars=model.elements.filter(e=>!Object.values(e.faces).every(f=>f.texture==='#air_green'||f.texture==='#air_glow'));
    const min=Math.min(...bars.map(e=>e.from[1]));
    if(min<24)for(const e of bars){e.from[1]+=4;e.to[1]+=4;}
    const line=[];
    for(let i=0;i<64;i++){
      const t=(i+.5)/64,u=variant==='up'?2*t-t*t:variant==='down'?t*t:t;
      line.push(...sheets(variant?16*u:8,24,-14+i*.3125,-14+(i+1)*.3125,4.8,2.4));
    }
    model.textures.air_glow=glow;model.elements=[...line,...bars];write(name,model);
  }
  require('./decorative-line-models.cjs')();
  console.log('Air Action: alpha-blended glow sheets, +1 block examples and elongated Air markers generated.');
}
module.exports=generate;
if(require.main===module)generate();
