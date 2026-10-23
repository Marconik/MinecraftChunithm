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
  for (const face of Object.values(rail.faces))
    assert.equal(model.textures[face.texture.slice(1)],'minecraft:block/gold_block');
  assert.equal(rail.shade,true,'Metal rails must respond to normal world lighting');
  assert.equal(rail.light_emission,undefined);
}
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
console.log('Model checks passed: 7 dividers / 8 regions; all 16 TAP widths have 0.5-block travel depth and centred white stripes; rails retain dimensions and use vanilla gold-block metal on all faces.');
