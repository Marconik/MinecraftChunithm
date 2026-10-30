"""Extract the supplied DDS nameplates and slider art as metadata-free GUI PNGs."""
from pathlib import Path
import argparse
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("texture_dir", type=Path)
    args = parser.parse_args()
    out = ROOT / "src/main/resources/assets/minecraftchunithm/textures/gui/audio"
    out.mkdir(parents=True, exist_ok=True)
    headphone = Image.open(args.texture_dir / "CHU_UI_Select_headphonebox_00.dds").convert("RGBA")
    voice = Image.open(args.texture_dir / "CHU_UI_Systemvoice_dummy.dds").convert("RGBA")
    assets = {
        # Keep the complete nameplates shown in the user's references, including
        # the Japanese headphone label and the CHUNITHM system-voice emblem.
        # Crop inside the outer white rim, above the grey slider panel. The
        # nameplate's own white lettering and Japanese underline stay intact.
        "headphone": headphone.crop((9, 10, 427, 339)),
        "system_voice": voice.crop((8, 4, 375, 254)),
        "rail": headphone.crop((48, 380, 380, 480)),
    }
    # Match the source ramp's inner polygon; leave the original white rim visible.
    fill = Image.new("RGBA", (332, 100))
    for x in range(14, 320):
        t = (x - 14) / 305
        color = headphone.getpixel((3 + round(t * 309), 752))
        for y in range(round(70 - 55 * t), 87):
            fill.putpixel((x, y), color[:3] + (255,))
    assets["fill"] = fill
    for name, asset in assets.items():
        clean = Image.new("RGBA", asset.size)
        clean.paste(asset)
        clean.save(out / (name + ".png"), optimize=True)
        print(name, clean.size)

if __name__ == "__main__":
    main()
