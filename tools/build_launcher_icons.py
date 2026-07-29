"""Build Android launcher icons — centered rose-gold ring on flat purple."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

DEFAULT_SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets"
    r"\lingban_ring_centered.png"
)
RES = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\app\src\main\res")
WORK = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\tools\icon_work")

BG = (46, 23, 82, 255)
FG_SIZE = 1024
SAFE = int(FG_SIZE * 0.78)


def make_round(src: Image.Image) -> Image.Image:
    size = src.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((1, 1, size - 2, size - 2), fill=255)
    out.putalpha(mask)
    return out


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    args = parser.parse_args()

    WORK.mkdir(parents=True, exist_ok=True)
    im = Image.open(args.src).convert("RGBA")
    if im.size[0] != im.size[1]:
        side = max(im.size)
        canvas = Image.new("RGBA", (side, side), BG)
        canvas.paste(im, ((side - im.size[0]) // 2, (side - im.size[1]) // 2), im)
        im = canvas

    # Place art into safe zone, then auto-correct vertical mass to canvas center
    tile = Image.new("RGBA", (SAFE, SAFE), BG)
    inner = int(SAFE * 0.94)
    content = im.resize((inner, inner), Image.Resampling.LANCZOS)

    def mass_cy(img: Image.Image) -> float:
        rgb = img.convert("RGB")
        w, h = rgb.size
        ys: list[int] = []
        for y in range(h):
            for x in range(0, w, 2):
                r, g, b = rgb.getpixel((x, y))
                if abs(r - BG[0]) + abs(g - BG[1]) + abs(b - BG[2]) > 45:
                    ys.append(y)
        return (sum(ys) / len(ys)) if ys else h / 2

    # First paste centered
    off_x = (SAFE - inner) // 2
    off_y = (SAFE - inner) // 2
    trial = Image.new("RGBA", (SAFE, SAFE), BG)
    trial.paste(content, (off_x, off_y), content)
    cy = mass_cy(trial)
    # Shift so mass center lands on geometric center (+ tiny optical down bias)
    target_cy = SAFE / 2 + SAFE * 0.01
    adjust = int(round(target_cy - cy))
    off_y = max(0, min(off_y + adjust, SAFE - inner))
    tile.paste(content, (off_x, off_y), content)
    print(f"mass adjust dy={adjust} final_cy={mass_cy(tile):.1f} target={target_cy:.1f}")


    fg = Image.new("RGBA", (FG_SIZE, FG_SIZE), (0, 0, 0, 0))
    o = (FG_SIZE - SAFE) // 2
    fg.paste(tile, (o, o))

    full = Image.new("RGBA", (FG_SIZE, FG_SIZE), BG)
    full.paste(fg, (0, 0), fg)

    fg.save(WORK / "ic_launcher_foreground_1024.png")
    full.save(WORK / "ic_launcher_full_1024.png")
    make_round(full).save(WORK / "ic_launcher_round_1024.png")
    brand = tile.resize((512, 512), Image.Resampling.LANCZOS)
    brand.save(WORK / "brand_logo.png")
    full.resize((48, 48), Image.Resampling.LANCZOS).save(WORK / "preview_48.png")
    full.resize((96, 96), Image.Resampling.LANCZOS).save(WORK / "preview_96.png")

    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, px in sizes.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        scaled = full.resize((px, px), Image.Resampling.LANCZOS)
        scaled.save(d / "ic_launcher.png")
        make_round(scaled).save(d / "ic_launcher_round.png")
        print(folder, px, "ok")

    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    fg.save(RES / "drawable" / "ic_launcher_foreground.png")
    brand.save(RES / "drawable" / "brand_logo.png")

    (RES / "values" / "colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        '    <color name="ic_launcher_background">#2E1752</color>\n'
        "</resources>\n",
        encoding="utf-8",
    )
    print("done", args.src.name)


if __name__ == "__main__":
    main()
