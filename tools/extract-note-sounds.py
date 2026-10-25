"""Export all TOUCH/AIR source waveforms as independent MP3 previews."""
import argparse
from collections import Counter, defaultdict
import csv
import json
from pathlib import Path
import re
import shutil
import subprocess
import wave

from cri_audio import CueSheet, read_afs2


def run(args, data=None, warnings=None):
    result = subprocess.run(args, input=data, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    if warnings is not None and result.stderr:
        warnings.append(result.stderr.decode('utf-8', errors='replace').strip())
    return result.stdout


def probe(path):
    return json.loads(run(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)]))


def write_preview(output, entries):
    data = json.dumps(entries, ensure_ascii=False).replace('<', '\\u003c')
    html = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>音符音效试听</title>
<style>body{font-family:"Microsoft YaHei",sans-serif;background:#eee;color:#252525;margin:0}
main{max-width:1000px;margin:auto;padding:30px}h1{margin:0 0 12px}p{line-height:1.8}
input{box-sizing:border-box;width:100%;padding:13px;font-size:16px;border:1px solid #aaa;border-radius:5px;margin:12px 0}
.card{background:white;border:1px solid #ccc;padding:18px;margin:12px 0;border-radius:6px}
h2{font-size:16px;overflow-wrap:anywhere;margin:0 0 8px}.meta{color:#666;font-size:13px;margin-bottom:12px}
audio{width:100%;height:40px}a{color:#564193}summary{cursor:pointer;margin-top:10px}
li{overflow-wrap:anywhere;font-size:13px;line-height:1.8}.head{display:flex;justify-content:space-between;gap:12px}
</style><main><h1>音符相关音效 · 原始音源试听</h1>
<p>包含 SE_TOUCH_*、SE_AIR_* 的全部音源。多条 Cue 可能共享一个文件，一个 Cue 也可能使用多个分层或变体。
这里逐段播放原始音源，不模拟街机的混音、变调、音量控制或无限循环；循环素材只播放文件本身的一轮。</p>
<input id="search" placeholder="搜索 Cue ID、TAP、HOLD、SLIDE、FLICK、AIR 或音效名称" aria-label="搜索音效">
<p id="count"></p><div id="items"></div></main><script>
const entries=__DATA__,items=document.getElementById('items'),search=document.getElementById('search');
function draw(){items.replaceChildren();const q=search.value.trim().toUpperCase();
 const shown=entries.filter(e=>(e.file+' '+e.aliases.map(a=>a.id+' '+a.name).join(' ')).toUpperCase().includes(q));
 document.getElementById('count').textContent=shown.length+' / '+entries.length+' 段独立音源';
 for(const e of shown){const card=document.createElement('section');card.className='card';
 const head=document.createElement('div');head.className='head';const title=document.createElement('h2');title.textContent=e.file;
 const link=document.createElement('a');link.href='mp3/'+e.file;link.textContent='MP3';link.download=e.file;head.append(title,link);
 const meta=document.createElement('div');meta.className='meta';meta.textContent=e.duration.toFixed(3)+' 秒 · '+e.channels+' 声道 · '+e.sampleRate+' Hz'+(e.loop?' · 循环素材':'');
 const audio=document.createElement('audio');audio.controls=true;audio.preload='none';audio.src='mp3/'+e.file;
 audio.addEventListener('play',()=>{for(const other of document.querySelectorAll('audio'))if(other!==audio)other.pause()});
 const details=document.createElement('details'),summary=document.createElement('summary'),list=document.createElement('ul');
 summary.textContent='对应 '+e.aliases.length+' 个 Cue';details.append(summary,list);
 for(const a of e.aliases){const li=document.createElement('li');li.textContent=a.id+' · '+a.name;list.append(li)}
 card.append(head,meta,audio,details);items.append(card)}
}search.addEventListener('input',draw);draw();</script></html>'''
    (output / '试听.html').write_text(html.replace('__DATA__', data), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=Path('E:/SDHD_2.50.00_20260518110527_0/data/sound'))
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[1] / 'build/note-sounds-preview')
    parser.add_argument('--hca-key', type=int, default=32931609366120192)
    parser.add_argument('--overwrite', action='store_true', help='Regenerate previews in an existing output directory')
    args = parser.parse_args()
    if not all(shutil.which(tool) for tool in ('ffmpeg', 'ffprobe')):
        parser.error('ffmpeg and ffprobe must be on PATH')
    if args.output.exists() and not args.overwrite:
        parser.error('Output already exists; choose a new directory to avoid overwriting previews')
    sheet = CueSheet((args.source / 'chu.acb').read_bytes())
    cues = sorted((c for c in sheet.cues() if c['name'].upper().startswith(('SE_TOUCH_', 'SE_AIR_'))), key=lambda c:c['CueId'])
    groups, cue_map = defaultdict(list), []
    for cue in cues:
        leaves = sheet.waveforms(cue)
        unique = {}
        for leaf in leaves:
            unique.setdefault(leaf['index'], leaf)
            leaf['paths'] = [candidate['path'] for candidate in leaves if candidate['index'] == leaf['index']]
        cue_map.append({'id':cue['CueId'], 'name':cue['name'].upper(), 'waveforms':[
            {'index':leaf['index'], 'paths':leaf['paths']} for leaf in unique.values()]})
        for leaf in unique.values():
            groups[leaf['index']].append(dict(id=cue['CueId'], name=cue['name'].upper(), waveform=leaf['waveform']))
    if not cues or not groups:
        raise ValueError('No note-related sounds found')
    banks = {'memory':read_afs2(sheet.header['AwbFile']), 'stream':read_afs2((args.source / 'chu.awb').read_bytes())}
    args.output.mkdir(parents=True, exist_ok=True)
    for folder in ['mp3', 'source']:
        (args.output / folder).mkdir(exist_ok=True)
    entries, index = [], []
    for wave_index, aliases in sorted(groups.items()):
        waveform = aliases[0]['waveform']
        bank_name = 'memory' if waveform['Streaming'] == 0 else 'stream'
        identifier = waveform['MemoryAwbId' if bank_name == 'memory' else 'StreamAwbId']
        bank, subkey = banks[bank_name]
        payload = bank[identifier]
        codec = 'hca' if bytes(x & 127 for x in payload[:4]) == b'HCA\0' else 'adx'
        if codec == 'adx':
            end = int.from_bytes(payload[2:4], 'big') + 4
            if payload[:2] != b'\x80\x00' or payload[end-6:end] != b'(c)CRI':
                raise ValueError('Unknown source codec')
        source = args.output / f'source/wave_{wave_index:04d}.{codec}'
        source.write_bytes(payload)
        primary = min(aliases, key=lambda a:('_ACTION' in a['name'], len(a['name']), a['id']))
        safe_name = re.sub('[^A-Z0-9_]', '_', primary['name'])
        filename = f"{primary['id']:04d}_{safe_name}__W{wave_index:04d}.mp3"
        target = args.output / 'mp3' / filename
        input_args = []
        if codec == 'hca':
            input_args = ['-hca_lowkey',str(args.hca_key & 0xffffffff),'-hca_highkey',str(args.hca_key >> 32),'-hca_subkey',str(subkey)]
        # Decode ADX to a continuous PCM stream first. Some ADX loop headers
        # produce irregular frame boundaries that LAME cannot consume directly.
        # Trim codec block padding to the sample count declared by the ACB.
        pcm = args.output / f'source/wave_{wave_index:04d}.wav'
        source_warnings = []
        # ADX's terminal control packet can be flagged by FFmpeg's demuxer;
        # preserve it and verify the complete decoded sample count below.
        # The generated MP3 is always decoded again with strict error handling.
        strict_source = ['-xerror'] if codec == 'hca' else []
        run(['ffmpeg','-nostdin','-y','-v','error',*strict_source,*input_args,'-i',str(source),'-map','0:a:0',
             '-af',f"atrim=end_sample={waveform['NumSamples']},asetpts=PTS-STARTPTS",'-map_metadata','-1','-c:a','pcm_s16le',str(pcm)],warnings=source_warnings)
        with wave.open(str(pcm), 'rb') as decoded:
            if decoded.getnframes() != waveform['NumSamples']:
                raise ValueError('PCM sample count does not match ACB metadata')
        # No loop expansion, normalization, pitch changes or channel downmixing.
        run(['ffmpeg','-nostdin','-y','-v','error','-xerror','-i',str(pcm),'-map','0:a:0',
             '-map_metadata','-1','-c:a','libmp3lame','-q:a','2','-write_xing','1',str(target)])
        decoded_mp3 = run(['ffmpeg','-nostdin','-v','error','-xerror','-i',str(target),'-f','s16le','-'])
        decoded_samples = len(decoded_mp3) // (waveform['NumChannels'] * 2)
        # MP3 frames and LAME/Xing gapless fields can round by a few samples.
        # Reject a duration error larger than one MPEG-1 Layer III frame.
        if abs(decoded_samples - waveform['NumSamples']) > 1152:
            raise ValueError('MP3 decoded duration differs by more than one frame')
        info = probe(target)
        stream = info['streams'][0]
        if stream['codec_name'] != 'mp3' or stream['channels'] != waveform['NumChannels'] or int(stream['sample_rate']) != waveform['SamplingRate']:
            raise ValueError('MP3 format or channel/rate mismatch')
        entry = dict(file=filename, waveform=wave_index, bank=bank_name, awbId=identifier, sourceCodec=codec,
                     duration=waveform['NumSamples']/waveform['SamplingRate'], encodedDuration=float(info['format']['duration']),
                     channels=stream['channels'], sampleRate=int(stream['sample_rate']), loop=bool(waveform['LoopFlag']),
                     sourceSamples=waveform['NumSamples'],decodedMp3Samples=decoded_samples,
                     mp3PaddingDifferenceSamples=decoded_samples-waveform['NumSamples'],
                     sourceDecoderMessages=source_warnings,
                     fullDecodeValidated=True, aliases=[dict(id=a['id'],name=a['name']) for a in aliases])
        entries.append(entry)
        for alias in aliases:
            index.append(dict(CueId=alias['id'], CueName=alias['name'], Waveform=wave_index, SourceBank=bank_name, AwbId=identifier,
                              SourceCodec=codec.upper(), Channels=entry['channels'], SampleRate=entry['sampleRate'],
                              DurationSeconds=round(entry['duration'],6), Loop=entry['loop'], MP3='mp3/'+filename))
        print(f'[{len(entries)}/{len(groups)}] {filename}', flush=True)
    with (args.output / '音效索引.csv').open('w', encoding='utf-8-sig', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=list(index[0]))
        writer.writeheader()
        writer.writerows(sorted(index, key=lambda r:(r['CueId'],r['Waveform'])))
    report = dict(source=str(args.source), cueCount=len(cues), uniqueWaveforms=len(groups), cueWavePairs=len(index),
                  codecs=dict(Counter(e['sourceCodec'] for e in entries)), entries=entries, cueMappings=cue_map,
                  previewOnly='Source layers/variants; Atom mixing, selectors, gain, pitch, actions and loop repetition are not emulated.')
    (args.output / 'extraction.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (args.output / '说明.txt').write_text(
        f"音符音效试听\n\n共 {len(cues)} 个 Cue，对应 {len(groups)} 段不同音源。\n"
        "打开 试听.html 可以搜索名称或 Cue ID 并播放；mp3 文件夹内是独立 MP3。\n"
        "音效索引.csv 保存所有 Cue 与文件的对应关系（UTF-8 BOM，可用 Excel 打开）。\n"
        "相同音源只导出一次；一个 Cue 可由多段音源分层或按设定选择。\n"
        "这里是原始音源试听，不模拟街机的混音、变调、音量控制、选择器或无限循环。\n"
        "source 文件夹保留提取的 ADX/HCA；原始 E 盘容器不变。\n"
        "映射参考：https://github.com/vgmstream/vgmstream/blob/master/src/meta/acb.c\n",
        encoding='utf-8-sig')
    write_preview(args.output, entries)
    print(json.dumps({key:report[key] for key in ['cueCount','uniqueWaveforms','cueWavePairs','codecs']}))
    print('Output:', args.output)


if __name__ == '__main__':
    main()
