// Deterministic native Minecraft JSON geometry and procedural palette textures.
// Run with Node.js: node tools/generate-models.cjs
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const root = path.resolve(__dirname, '../src/main/resources');
const ns = 'minecraftchunithm';
const assets = `assets/${ns}`;
function write(file, data) {
  const dest = path.join(root, file);
  fs.mkdirSync(path.dirname(dest), { recursive: true });
  fs.writeFileSync(dest, data);
}
function json(file, data) { write(file, JSON.stringify(data, null, 2) + '\n'); }
function crc32(bytes) {
  let crc = 0xffffffff;
  for (const b of bytes) {
    crc ^= b;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, body) {
  const tag = Buffer.from(type), size = Buffer.alloc(4), crc = Buffer.alloc(4);
  size.writeUInt32BE(body.length); crc.writeUInt32BE(crc32(Buffer.concat([tag, body])));
  return Buffer.concat([size, tag, body, crc]);
}
function texture(name, top, bottom = top) {
  const size = 32, raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const at = y * (size * 4 + 1) + 1 + x * 4;
    for (let c = 0; c < 3; c++) raw[at+c] = Math.round(top[c] + (bottom[c]-top[c])*y/(size-1));
    raw[at+3] = 255;
  }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(size, 0); ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6;
  write(`${assets}/textures/block/${name}.png`, Buffer.concat([
    Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]));
}
texture('deck', [17,19,29]);
texture('side', [38,40,53], [15,16,24]);
texture('lane', [130,105,46]);
texture('gold', [255,239,149], [213,149,29]);
texture('white', [255,255,247], [199,214,229]);
texture('tap_red', [255,156,171], [222,14,60]);
texture('tap_shadow', [111,17,49], [49,13,35]);
texture('highlight', [255,240,245]);
function box(from, to, tex, light = 0) {
  return { from, to, shade: light === 0, ...(light ? {light_emission: light} : {}),
    faces: Object.fromEntries(['up','down','north','south','east','west'].map(face =>
      [face, {uv:[0,0,16,16], texture:'#'+tex}])) };
}
const textureNames = ['deck','side','lane','gold','white','tap_red','tap_shadow','highlight'];
const textures = Object.fromEntries(textureNames.map(n => [n,`${ns}:block/${n}`]));
const en = {}, zh = {};
const trackNames = {
  track:['Track · two lanes','轨道 · 双分区'],
  track_left:['Track · left edge','轨道 · 左边框'],
  track_right:['Track · right edge','轨道 · 右边框'],
  track_judgment:['Track · judgment line','轨道 · 判定线']
};
for (const [name, labels] of Object.entries(trackNames)) {
  const base = box([0,0,0],[16,15.875,16],'side');
  base.faces.up.texture = '#deck';
  const elements = [base];
  // Boundary lines are split across neighbouring blocks; the centre line is exactly half a block.
  for (const [left,right] of [[0,0.0625],[7.9375,8.0625],[15.9375,16]])
    elements.push(box([left,15.875,0],[right,16,16],'lane'));
  // Fill the remaining deck to the same level as the collision surface.
  for (const [left,right] of [[0.0625,7.9375],[8.0625,15.9375]])
    elements.push(box([left,15.875,0],[right,16,16],'deck'));
  if (name === 'track_left' || name === 'track_right') {
    const x = name === 'track_left' ? 0 : 15.5;
    elements.push(box([x,16.005,0],[x+0.5,16.125,16],'gold', 8));
  }
  if (name === 'track_judgment') {
    elements.push(box([0,16.005,6.5],[16,16.125,9.5],'gold', 10));
    elements.push(box([0,16.13,7.625],[16,16.1875,8.375],'white', 15));
  }
  json(`${assets}/models/block/${name}.json`, {
    parent:'minecraft:block/block', ambientocclusion:false,
    textures:{...textures, particle:textures.deck}, elements
  });
  json(`${assets}/blockstates/${name}.json`, {variants:Object.fromEntries(
    ['north','east','south','west'].map((direction,index) =>
      [`facing=${direction}`,{model:`${ns}:block/${name}`,y:index*90}]))});
  json(`${assets}/items/${name}.json`,{model:{type:'minecraft:model',model:`${ns}:block/${name}`}});
  en[`block.${ns}.${name}`]=labels[0]; zh[`block.${ns}.${name}`]=labels[1];
}
for (let lanes = 1; lanes <= 16; lanes++) {
  // Native element coordinates must stay in [-16,32]. A 32-unit mesh with FIXED
  // context X scaling supports the full 0.5–8 block range without invalid geometry.
  const scale = lanes / 4;
  const inset = units => units / scale;
  const left = -8, right = 24;
  const elements = [
    box([left,8,6.25],[right,8.5,9.75],'tap_shadow'),
    box([left+inset(0.25),8.5,6],[right-inset(0.25),9.5,10],'white',12),
    box([left,8.5,6.5],[right,9.25,9.5],'white',12),
    box([left+inset(0.75),9.5,6.5],[right-inset(0.75),10,9.5],'tap_red',12),
    box([left+inset(1),10.01,6.625],[right-inset(1),10.0625,7],'highlight',15)
  ];
  const model = {parent:'minecraft:block/block',ambientocclusion:false,gui_light:'front',
    textures:{...textures,particle:textures.tap_red},elements,display:{
      fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[scale,1,1]},
      gui:{rotation:[35,20,0],translation:[0,0,0],scale:[0.42,1.2,1.2]},
      ground:{rotation:[0,0,0],translation:[0,2,0],scale:[0.2,0.5,0.5]},
      firstperson_righthand:{rotation:[0,0,0],translation:[0,2,0],scale:[0.3,0.8,0.8]},
      thirdperson_righthand:{rotation:[75,0,0],translation:[0,2,0],scale:[0.25,0.6,0.6]}
    }};
  json(`${assets}/models/item/tap_${lanes}.json`,model);
  json(`${assets}/items/tap_${lanes}.json`,{model:{type:'minecraft:model',model:`${ns}:item/tap_${lanes}`}});
  en[`item.${ns}.tap_${lanes}`]=`TAP · ${lanes} lanes (${lanes/2} blocks)`;
  zh[`item.${ns}.tap_${lanes}`]=`TAP · ${lanes} 分区（${lanes/2} 格）`;
}
json(`${assets}/lang/en_us.json`,en);
json(`${assets}/lang/zh_cn.json`,zh);
// Manual preview function: deliberately invoked by the player in an empty area.
const commands = [
  '# Places an 8 x 24 track at the caller position; replaces blocks in that footprint.',
  `fill ~ ~-1 ~ ~7 ~-1 ~23 ${ns}:track[facing=north]`,
  `fill ~ ~-1 ~ ~ ~-1 ~23 ${ns}:track_left[facing=north]`,
  `fill ~7 ~-1 ~ ~7 ~-1 ~23 ${ns}:track_right[facing=north]`,
  `fill ~ ~-1 ~22 ~7 ~-1 ~22 ${ns}:track_judgment[facing=north]`
];
for(let lanes=1; lanes<=16; lanes++) commands.push(
  `summon minecraft:item_display ~4 ~0.03125 ~${lanes+2} {Tags:["minecraftchunithm.tap_preview"],item:{id:"${ns}:tap_${lanes}",count:1},item_display:"fixed",brightness:{block:15,sky:15},width:9.0f,height:0.5f}`);
write(`data/${ns}/function/model_showcase.mcfunction`,commands.join('\n')+'\n');
console.log('Generated 4 track block models, 16 TAP models, textures, translations and showcase function.');
