const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const base = path.resolve(__dirname, '../src/main/resources/assets/minecraftchunithm/models');
const read = name => JSON.parse(fs.readFileSync(path.join(base, name), 'utf8'));
const left = read('block/track_left.json'), right = read('block/track_right.json');
for (const [model, x] of [[left,0],[right,8]]) {
  const rail = model.elements.find(e => e.faces.up.texture.startsWith('#rail_gold_'));
  assert.deepEqual(rail.from, [x,10,0]);
  assert.deepEqual(rail.to, [x+8,12,16]);
  assert.equal(Math.max(...model.elements.map(e=>e.to[1])), 12);
  assert.equal(Math.min(...model.elements.map(e=>e.from[1])), 0);
  assert.equal(rail.faces.west.texture,x===0?'#gold_outer':'#gold_inner');
  assert.equal(rail.faces.east.texture,x===0?'#gold_inner':'#gold_outer');
}
// Generated PNGs use filter 0: inspect actual texture colours without image hashes.
function pixels(name) {
  const png=fs.readFileSync(path.resolve(base,`../textures/block/${name}.png`));
  const data=[];
  for(let offset=8;offset<png.length;) {
    const length=png.readUInt32BE(offset),type=png.toString('ascii',offset+4,offset+8);
    if(type==='IDAT')data.push(png.subarray(offset+8,offset+8+length));
    offset+=length+12;
  }
  const raw=require('node:zlib').inflateSync(Buffer.concat(data));
  return (x,y)=>Array.from(raw.subarray(y*129+1+x*4,y*129+1+x*4+4));
}
const leftPixels=pixels('rail_gold_left'),rightPixels=pixels('rail_gold_right');
for(let x=0;x<32;x++)for(let y=0;y<32;y++) {
  assert.deepEqual(leftPixels(x,y),leftPixels(x,0),'Rail colour must not repeat along track');
  assert.deepEqual(rightPixels(x,y),rightPixels(x,0));
  assert.deepEqual(leftPixels(x,y),rightPixels(31-x,y),'Rails must mirror inner-to-outer');
}
assert.ok(leftPixels(31,0)[1]>leftPixels(0,0)[1],'Inner edge must be brighter');
for (const name of ['track','track_judgment']) {
  const model = read(`block/${name}.json`);
  assert.equal(Math.max(...model.elements.map(e=>e.to[1])), 10);
}
// Nine blocks minus two 8/16 rails leave exactly sixteen half-block lanes.
assert.equal(9 - 2 * (8/16), 8);
const row=[left,...Array.from({length:7},()=>read('block/track.json')),right];
const dividers=row.flatMap((m,index)=>m.elements
  .filter(e=>e.faces.up.texture==='#lane')
  .map(e=>index+(e.from[0]+e.to[0])/32));
assert.deepEqual(dividers,[1.5,2.5,3.5,4.5,5.5,6.5,7.5]);
for (let lanes=1; lanes<=16; lanes++) {
  const m=read(`item/tap_${lanes}.json`);
  const min=Math.min(...m.elements.map(e=>e.from[0]));
  const max=Math.max(...m.elements.map(e=>e.to[0]));
  const width=(max-min)/16*m.display.fixed.scale[0];
  assert.equal(width, lanes/2);
  const depth=(Math.max(...m.elements.map(e=>e.to[2]))-Math.min(...m.elements.map(e=>e.from[2])))/16;
  assert.equal(depth,0.5,'TAP travel depth must double from 0.25 to 0.5 blocks');
  const stripe=m.elements.find(e=>e.faces.up.texture==='#white' && e.from[1]>10);
  assert.ok(stripe,'White centre stripe missing');
  assert.equal((stripe.from[2]+stripe.to[2])/2,8);
  assert.ok(Math.abs((stripe.from[0]+stripe.to[0])/2-8)<1e-9);
  assert.ok(4.5-width/2 >= 0.5 && 4.5+width/2 <= 8.5);
  for (const e of m.elements) for (let axis=0;axis<3;axis++) {
    assert.ok(e.from[axis] >= -16 && e.to[axis] <= 32);
    assert.ok(e.from[axis] < e.to[axis]);
  }
}
console.log('Model checks passed: 7 dividers / 8 regions; all 16 TAP widths have 0.5-block travel depth and centred white stripes; rail dimensions and gradients preserved.');
