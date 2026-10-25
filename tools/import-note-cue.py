"""Import SE_TOUCH_TAP_CRITICAL waveform 50 as a spatial mono Vorbis resource."""
import argparse
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=ROOT / 'build/note-sounds-preview/source/wave_0050.wav')
    parser.add_argument('--duration', type=float, default=0.2, help='Target cue duration in seconds (pitch preserved)')
    args = parser.parse_args()
    if not 0.02 <= args.duration <= 5:
        parser.error('Duration must be between 0.02 and 5 seconds')
    source = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-of', 'json', str(args.input)]))
    factor = float(source['streams'][0]['duration']) / args.duration
    tempo = []
    # Keep each atempo stage within 0.5–2 to avoid its high-ratio sample skipping.
    while factor > 2:
        tempo.append('atempo=2')
        factor /= 2
    while factor < 0.5:
        tempo.append('atempo=0.5')
        factor /= 0.5
    tempo.append(f'atempo={factor:.12f}')
    tempo.extend(['apad', f'atrim=duration={args.duration}',
                  f'afade=t=out:st={args.duration - 0.005:.9f}:d=0.005'])
    assets = ROOT / 'src/main/resources/assets/minecraftchunithm'
    output = assets / 'sounds/note/tap_critical.ogg'
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(args.input), '-map', '0:a:0',
                    '-map_metadata', '-1', '-af', ','.join(tempo), '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', str(output)], check=True)
    probe = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-of', 'json', str(output)]))
    stream = probe['streams'][0]
    if stream['codec_name'] != 'vorbis' or stream['channels'] != 1:
        raise ValueError('Note cue must be mono Vorbis for spatial sound')
    if abs(float(stream['duration']) - args.duration) > 0.001:
        raise ValueError('Note cue duration differs from the requested duration')
    subprocess.run(['ffmpeg', '-v', 'error', '-xerror', '-i', str(output), '-f', 'null', '-'], check=True)
    path = assets / 'sounds.json'
    sounds = json.loads(path.read_text(encoding='utf-8'))
    sounds.pop('note.answer', None)
    sounds['note.tap_critical'] = {'sounds': [{'name': 'minecraftchunithm:note/tap_critical', 'stream': True, 'attenuation_distance': 64}]}
    path.write_text(json.dumps(sounds, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (assets / 'sounds/note/answer.ogg').unlink(missing_ok=True)
    print(f'Imported {output}: mono {stream["sample_rate"]} Hz, {stream["duration"]} seconds')


if __name__ == '__main__':
    main()
