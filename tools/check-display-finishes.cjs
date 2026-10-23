const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const zlib=require('node:zlib');
const root=path.resolve(__dirname,'../src/main/resources/assets/minecraftchunithm');
for(const name of ['portrait_polish','meter_polish']) {
  const model=JSON.parse(fs.readFileSync(path.join(root,`models/item/finishes/${name}.json`),'utf8'));
  assert.equal(model.textures.surface.force_translucent,true,'Low-alpha sheen must be blended, not cut out');
  assert.deepEqual(Object.keys(model.elements[0].faces),['south'],'Coating must face the same way as the facade');
  assert.deepEqual(model.display.fixed.scale,[1,1,1]);
  const png=fs.readFileSync(path.join(root,`textures/item/finishes/${name}.png`));
  const width=png.readUInt32BE(16), height=png.readUInt32BE(20), compressed=[];
  for(let offset=8;offset<png.length;) {
    const length=png.readUInt32BE(offset),type=png.toString('ascii',offset+4,offset+8);
    if(type==='IDAT')compressed.push(png.subarray(offset+8,offset+8+length));
    offset+=length+12;
  }
  const raw=zlib.inflateSync(Buffer.concat(compressed));
  let clear=0,peak=0,bright=0,dark=0;
  for(let y=0;y<height;y++)for(let x=0;x<width;x++) {
    const at=y*(width*4+1)+1+x*4,alpha=raw[at+3];
    assert.equal(raw[y*(width*4+1)],0);
    assert.ok(alpha<=100,'Coating must not obscure imported artwork');
    if(!alpha)clear++;
    peak=Math.max(peak,alpha);
    if(alpha&&raw[at]===255)bright++;
    if(alpha&&raw[at]===0)dark++;
  }
  assert.ok(clear>width*height*.25,'Large areas must remain clear');
  assert.ok(peak>=90&&bright>0&&dark>0,'Beveled polish needs both highlights and edge shade');
}
console.log('Display finish checks passed: transparent clear areas, restrained highlights, inset shading, facade-facing quads.');
