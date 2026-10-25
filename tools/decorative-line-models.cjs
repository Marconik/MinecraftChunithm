// Constant-color ALD glow, with the exact opacity profile and mesh used by Air.
const fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib');
const colors={gry:[160,160,160],aqa:[70,230,240],red:[245,65,65],blk:[25,25,25],
  def:[85,245,75],dgr:[25,115,55],grn:[65,230,75],pnk:[255,100,170],cyn:[45,195,235],
  blu:[55,100,255],orn:[255,155,40],yel:[255,235,60],ppl:[200,80,255],vlt:[110,75,255]};
function readRGBA(file){
  const png=fs.readFileSync(file),width=png.readUInt32BE(16),height=png.readUInt32BE(20),parts=[];
  if(png[24]!==8||png[25]!==6||png[28]!==0)throw new Error('Expected non-interlaced RGBA8 glow texture');
  for(let at=8;at<png.length;){const size=png.readUInt32BE(at);
    if(png.toString('ascii',at+4,at+8)==='IDAT')parts.push(png.subarray(at+8,at+8+size));at+=size+12;}
  const raw=zlib.inflateSync(Buffer.concat(parts)),stride=width*4,pixels=Buffer.alloc(width*height*4);
  if(raw.length!==(stride+1)*height)throw new Error('Unexpected PNG scanline length');
  function paeth(a,b,c){const p=a+b-c,pa=Math.abs(p-a),pb=Math.abs(p-b),pc=Math.abs(p-c);return pa<=pb&&pa<=pc?a:pb<=pc?b:c;}
  for(let y=0;y<height;y++){
    const filter=raw[y*(stride+1)];if(filter>4)throw new Error('Invalid PNG filter');
    for(let x=0;x<stride;x++){
      const at=y*stride+x,a=x>=4?pixels[at-4]:0,b=y?pixels[at-stride]:0,c=y&&x>=4?pixels[at-stride-4]:0;
      const predictor=[0,a,b,Math.floor((a+b)/2),paeth(a,b,c)][filter];
      pixels[at]=(raw[y*(stride+1)+x+1]+predictor)&255;
    }
  }
  return {width,height,pixels};
}
function crc32(bytes){let crc=0xffffffff;for(const b of bytes){crc^=b;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}return (crc^0xffffffff)>>>0;}
function chunk(type,body){const tag=Buffer.from(type),size=Buffer.alloc(4),crc=Buffer.alloc(4);
  size.writeUInt32BE(body.length);crc.writeUInt32BE(crc32(Buffer.concat([tag,body])));return Buffer.concat([size,tag,body,crc]);}
function tintedPng(image,rgb){
  const {width,height,pixels}=image,stride=width*4+1,raw=Buffer.alloc(stride*height);
  for(let y=0;y<height;y++)for(let x=0;x<width;x++){
    const at=y*stride+1+x*4;for(let c=0;c<3;c++)raw[at+c]=rgb[c];raw[at+3]=pixels[(y*width+x)*4+3];
  }
  const header=Buffer.alloc(13);header.writeUInt32BE(width,0);header.writeUInt32BE(height,4);header[8]=8;header[9]=6;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',header),
    chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
}
function generate(){
  const root=path.resolve(__dirname,'../src/main/resources/assets/minecraftchunithm');
  const image=readRGBA(path.join(root,'textures/block/air_action_line.png'));
  const air=JSON.parse(fs.readFileSync(path.join(root,'models/item/chart_air_body.json'),'utf8'));
  const write=(file,value)=>fs.writeFileSync(path.join(root,file),JSON.stringify(value,null,2)+'\n');
  for(const [color,rgb] of Object.entries(colors)){
    const name=`chart_effect_${color}_body`,sprite=`minecraftchunithm:block/effect_${color}`;
    const model=JSON.parse(JSON.stringify(air));
    model.textures={effect:{sprite,force_translucent:true},particle:sprite};
    for(const element of model.elements)for(const face of Object.values(element.faces))face.texture='#effect';
    write(`models/item/${name}.json`,model);
    write(`items/${name}.json`,{model:{type:'minecraft:model',model:`minecraftchunithm:item/${name}`}});
    fs.writeFileSync(path.join(root,`textures/block/effect_${color}.png`),tintedPng(image,rgb));
  }
  console.log('ALD: 14 solid-color translucent glow lines generated from the Air opacity profile.');
}
module.exports=generate;
if(require.main===module)generate();
