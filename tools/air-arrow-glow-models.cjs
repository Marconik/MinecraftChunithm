// Soft silhouette halos for existing native Air direction-marker meshes.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const root=path.resolve(__dirname,'../src/main/resources/assets/minecraftchunithm');
const pattern=/^(?:air|chart_air_arrow)(?:_(up_left|up_right|down|down_left|down_right))?_(\d+)\.json$/;
const size=128,bounds={left:-8,right:24,bottom:10,top:30};
function crc32(bytes){let crc=0xffffffff;for(const byte of bytes){crc^=byte;for(let bit=0;bit<8;bit++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
function chunk(type,data){const tag=Buffer.from(type),length=Buffer.alloc(4),crc=Buffer.alloc(4);length.writeUInt32BE(data.length);crc.writeUInt32BE(crc32(Buffer.concat([tag,data])));return Buffer.concat([length,tag,data,crc]);}
function png(pixels){
  const header=Buffer.alloc(13);header.writeUInt32BE(size);header.writeUInt32BE(size,4);header[8]=8;header[9]=6;
  const raw=Buffer.alloc((size*4+1)*size);
  for(let y=0;y<size;y++)pixels.copy(raw,y*(size*4+1)+1,y*size*4,(y+1)*size*4);
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
}
const isHalo=e=>Object.values(e.faces).some(f=>f.texture==='#air_arrow_glow');
function halo(elements,down){
  const mask=new Float64Array(size*size),horizontal=new Float64Array(size*size),pixels=Buffer.alloc(size*size*4);
  const arrow=elements.filter(e=>e.from[1]>=11&&!isHalo(e));
  if(!arrow.length)throw new Error('Air marker has no direction mesh');
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    const px=bounds.left+(x+.5)/size*(bounds.right-bounds.left),py=bounds.top-(y+.5)/size*(bounds.top-bounds.bottom);
    if(arrow.some(e=>px>=e.from[0]&&px<=e.to[0]&&py>=e.from[1]&&py<=e.to[1]))mask[y*size+x]=1;
  }
  // Separable Gaussian blur, transparent outside the original silhouette.
  const radius=12,sigma=4,weights=Array.from({length:radius*2+1},(_,i)=>Math.exp(-.5*((i-radius)/sigma)**2));
  const total=weights.reduce((a,b)=>a+b,0);for(let i=0;i<weights.length;i++)weights[i]/=total;
  for(let y=0;y<size;y++)for(let x=0;x<size;x++)for(let d=-radius;d<=radius;d++)
    if(x+d>=0&&x+d<size)horizontal[y*size+x]+=mask[y*size+x+d]*weights[d+radius];
  const rgb=down?[235,80,255]:[90,255,75];
  for(let y=0;y<size;y++)for(let x=0;x<size;x++){
    let blurred=0;for(let d=-radius;d<=radius;d++)if(y+d>=0&&y+d<size)blurred+=horizontal[(y+d)*size+x]*weights[d+radius];
    const at=(y*size+x)*4;for(let c=0;c<3;c++)pixels[at+c]=rgb[c];
    pixels[at+3]=mask[y*size+x]?0:Math.min(150,Math.round(blurred*255));
  }
  if(!pixels.some((value,i)=>i%4===3&&value>0))throw new Error('Empty Air halo');
  return png(pixels);
}
function generate(){
  const sprites=new Set();let count=0;
  for(const file of fs.readdirSync(path.join(root,'models/item'))){
    const match=file.match(pattern);if(!match)continue;
    const variant=match[1]||'up',name=`air_arrow_glow_${variant}`;
    const target=path.join(root,'models/item',file),model=JSON.parse(fs.readFileSync(target,'utf8'));
    model.elements=model.elements.filter(e=>!isHalo(e));
    if(!sprites.has(variant)){
      fs.writeFileSync(path.join(root,`textures/block/${name}.png`),halo(model.elements,variant.startsWith('down')));
      sprites.add(variant);
    }
    // Both sides use the same upright texture coordinates. The core is left
    // intact; only the diffuse, alpha-blended silhouette extends around it.
    model.textures.air_arrow_glow={sprite:`minecraftchunithm:block/${name}`,force_translucent:true};
    for(const [side,z] of [['north',7.8],['south',8.2]])model.elements.push({
      from:[bounds.left,bounds.bottom,z],to:[bounds.right,bounds.top,z],shade:false,light_emission:15,
      faces:{[side]:{uv:side==='north'?[16,0,0,16]:[0,0,16,16],texture:'#air_arrow_glow'}}
    });
    fs.writeFileSync(target,JSON.stringify(model,null,2)+'\n');count++;
  }
  if(count!==192||sprites.size!==6)throw new Error(`Incomplete Air glow variants: ${count} models, ${sprites.size} textures`);
  console.log(`Air arrows: ${count} models now have emissive, translucent green/purple silhouette halos.`);
}
module.exports=generate;
if(require.main===module)generate();
