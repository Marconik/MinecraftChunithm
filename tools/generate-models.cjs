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
function texture(name, top, bottom = top, axis = 'y') {
  const size = 32, raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const at = y * (size * 4 + 1) + 1 + x * 4;
    const blend = (axis === 'x' ? x : y) / (size-1);
    for (let c = 0; c < 3; c++) raw[at+c] = Math.round(top[c] + (bottom[c]-top[c])*blend);
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
texture('rail_gold_left', [213,149,29], [255,239,149], 'x');
texture('rail_gold_right', [255,239,149], [213,149,29], 'x');
texture('gold_inner', [255,239,149]);
texture('gold_outer', [213,149,29]);
texture('white', [255,255,247], [199,214,229]);
texture('tap_red', [255,156,171], [222,14,60]);
texture('tap_shadow', [111,17,49], [49,13,35]);
texture('highlight', [255,240,245]);
texture('beat_gray', [110,116,129]);
function box(from, to, tex, light = 0) {
  return { from, to, shade: light === 0, ...(light ? {light_emission: light} : {}),
    faces: Object.fromEntries(['up','down','north','south','east','west'].map(face =>
      [face, {uv:[0,0,16,16], texture:'#'+tex}])) };
}
const textureNames = ['deck','side','lane','gold','white','tap_red','tap_shadow','highlight','beat_gray',
  'rail_gold_left','rail_gold_right','gold_inner','gold_outer'];
const textures = Object.fromEntries(textureNames.map(n => [n,`${ns}:block/${n}`]));
const en = {}, zh = {};
const trackNames = {
  track:['Track · divider','轨道 · 区域分隔'],
  track_left:['Track · left edge','轨道 · 左边框'],
  track_right:['Track · right edge','轨道 · 右边框'],
  track_judgment:['Track · judgment line','轨道 · 判定线']
};
for (const [name, labels] of Object.entries(trackNames)) {
  const base = box([0,0,0],[16,9.875,16],'side');
  base.faces.up.texture = '#deck';
  const elements = [base];
  // Seven interior blocks each contribute one divider: eight visible regions.
  // The edge modules have no divider; half-lane note positioning stays unchanged.
  if (name === 'track_left' || name === 'track_right') {
    elements.push(box([0,9.875,0],[16,10,16],'deck'));
  } else {
    elements.push(box([7.9375,9.875,0],[8.0625,10,16],'lane'));
    for (const [left,right] of [[0,7.9375],[8.0625,16]])
      elements.push(box([left,9.875,0],[right,10,16],'deck'));
  }
  if (name === 'track_left' || name === 'track_right') {
    const isLeft = name === 'track_left';
    const x = isLeft ? 0 : 8;
    const rail = box([x,10,0],[x+8,12,16],isLeft?'rail_gold_left':'rail_gold_right',8);
    // U follows world X across the rail. Along-track faces use constant endpoint
    // colours, so adjacent blocks cannot restart a longitudinal gradient.
    rail.faces.north.uv = [16,0,0,16];
    rail.faces.west.texture = isLeft ? '#gold_outer' : '#gold_inner';
    rail.faces.east.texture = isLeft ? '#gold_inner' : '#gold_outer';
    elements.push(rail);
  }
  if (name === 'track_judgment') {
    // A flush stripe keeps the playable surface and collision height identical.
    const deckPieces = elements.filter(e => e.from[1] === 9.875);
    elements.splice(1);
    for (const piece of deckPieces) {
      elements.push({...piece, to:[piece.to[0],10,6.5]});
      elements.push({...piece, from:[piece.from[0],9.875,9.5]});
    }
    elements.push(box([0,9.875,6.5],[16,10,7.625],'gold',10));
    elements.push(box([0,9.875,7.625],[16,10,8.375],'white',15));
    elements.push(box([0,9.875,8.375],[16,10,9.5],'gold',10));
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
  // Double the footprint along travel (Z), keeping width and height unchanged.
  for (const element of elements) {
    element.from[2] = 8 + (element.from[2]-8)*2;
    element.to[2] = 8 + (element.to[2]-8)*2;
  }
  // White centre stripe runs across the note, inset from the rounded end caps.
  elements.push(box([left+inset(1.5),10.07,7.75],[right-inset(1.5),10.125,8.25],'white',15));
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
for (const name of ['beat_line','judgment_line']) {
  const gold = name === 'judgment_line';
  const depth = gold ? 1.5 : 0.5;
  json(`${assets}/models/item/${name}.json`, {
    parent:'minecraft:block/block',ambientocclusion:false,
    textures:{...textures,particle:gold?textures.gold:textures.beat_gray},
    elements:[box([-8,8,8-depth/2],[24,8.0625,8+depth/2],gold?'gold':'beat_gray',gold?15:5)],
    display:{fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[4,1,1]}}
  });
  json(`${assets}/items/${name}.json`,{model:{type:'minecraft:model',model:`${ns}:item/${name}`}});
  en[`item.${ns}.${name}`]=gold?'Judgment line':'Quarter beat line';
  zh[`item.${ns}.${name}`]=gold?'金色判定线':'四分音符节拍线';
}
json(`${assets}/lang/en_us.json`,en);
json(`${assets}/lang/zh_cn.json`,zh);
// Reuse the same command geometry, aligned to the block grid.
write(`data/${ns}/function/model_showcase.mcfunction`,
  `execute align xyz run function ${ns}:model_showcase_aligned\n`);
const commands = ['# Replaces a 9 x 24 footprint one block below the caller.', 'chunithm track 24'];
for(let lanes=1; lanes<=16; lanes++) commands.push(
  `summon minecraft:item_display ~4.5 ~-0.359375 ~${lanes+2} {Tags:["minecraftchunithm.tap_preview"],item:{id:"${ns}:tap_${lanes}",count:1},item_display:"fixed",brightness:{block:15,sky:15},width:9.0f,height:0.5f}`);
write(`data/${ns}/function/model_showcase_aligned.mcfunction`,commands.join('\n')+'\n');
console.log('Generated 9-block-wide track modules and 16 TAP widths.');
