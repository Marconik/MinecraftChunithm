// Native translucent item quads: reusable over any imported portrait/cover.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const root = path.resolve(__dirname, '../src/main/resources/assets/minecraftchunithm');
function write(file, data) {
  const dest = path.join(root, file);
  fs.mkdirSync(path.dirname(dest), {recursive:true});
  fs.writeFileSync(dest, data);
}
function json(file, data) { write(file, JSON.stringify(data, null, 2)+'\n'); }
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const b of bytes) {
    crc ^= b;
    for (let i=0;i<8;i++) crc=(crc>>>1)^((crc&1)?0xedb88320:0);
  }
  return (crc^0xffffffff)>>>0;
}
function chunk(type, body) {
  const tag=Buffer.from(type), size=Buffer.alloc(4), crc=Buffer.alloc(4);
  size.writeUInt32BE(body.length); crc.writeUInt32BE(crc32(Buffer.concat([tag,body])));
  return Buffer.concat([size,tag,body,crc]);
}
for (const name of ['portrait_polish','meter_polish']) {
  const size=128, stride=size*4+1, raw=Buffer.alloc(size*stride);
  for (let y=0;y<size;y++) for (let x=0;x<size;x++) {
    const u=x/(size-1), v=y/(size-1);
    // A broad reflected light band, a fine glint and a subtle top glaze.
    const diagonal=u+v*.58;
    const sheen=36*Math.exp(-Math.pow((diagonal-.58)/.17,2));
    const glint=23*Math.exp(-Math.pow((diagonal-.39)/.018,2));
    const glaze=name==='meter_polish'?26*Math.max(0,1-v*2.7):0;
    let alpha=Math.min(72,sheen+glint+glaze);
    // Small bright upper/left bevels and darker lower/right bevels suggest
    // a polished inset. Most of the image stays completely unobstructed.
    const dark=y>=size-2||x>=size-2;
    if(y<2||x<2)alpha=Math.max(alpha,100);
    if(dark)alpha=42;
    if(alpha<3)alpha=0;
    const at=y*stride+1+x*4;
    raw[at]=raw[at+1]=raw[at+2]=dark?0:255;
    raw[at+3]=Math.round(alpha);
  }
  const header=Buffer.alloc(13);
  header.writeUInt32BE(size,0);header.writeUInt32BE(size,4);header[8]=8;header[9]=6;
  write(`textures/item/finishes/${name}.png`,Buffer.concat([
    Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),
    chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]));
  json(`models/item/finishes/${name}.json`,{
    parent:'minecraft:block/block',ambientocclusion:false,gui_light:'front',
    textures:{surface:{sprite:`minecraftchunithm:item/finishes/${name}`,force_translucent:true},particle:'#surface'},
    elements:[{from:[0,0,8],to:[16,16,8],shade:false,light_emission:15,
      faces:{south:{uv:[0,0,16,16],texture:'#surface'}}}],
    display:{fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[1,1,1]}}
  });
  json(`items/finishes/${name}.json`,{model:{type:'minecraft:model',model:`minecraftchunithm:item/finishes/${name}`}});
}
console.log('Generated translucent portrait/cover and meter polish overlays.');
