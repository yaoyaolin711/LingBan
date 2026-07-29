"""Build Android launcher icons from LingBan master artwork."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

# Default: latest user-provided flame/droplet mark
DEFAULT_SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets"
    r"\c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"16290efc8cc7b6f901f446c04d2de435_images_ChatGPT_Image_2026_7_29__"
    r"11_19_47-52de17a8-ffd5-4b9a-9d1b-fa76df01bfb4.png"
)
RES = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\app\src\main\res")
WORK = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\tools\icon_work")

# Deep purple matching the new mark (adaptive background)
BG = (28, 12, 48, 255)
FG_SIZE = 1024
# Larger safe content so 48dp home-screen still reads the droplet
SAFE = int(FG_SIZE * 0.78)


def make_round(src: Image.Image) -> Image.Image:
    size = src.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((1, 1, size - 2, size - 2), fill=255)
    out.putalpha(mask)
    return out


def extract_mark(im: Image.Image) -> Image.Image:
    """Crop away outer dead margin + baked squircle frame; keep glowing core."""
    w, h = im.size
    # Outer padding ~10%, then pull in past the neon frame (~another 4%)
    left = int(w * 0.12)
    top = int(h * 0.12)
    right = int(w * 0.88)
    bottom = int(h * 0.88)
    cropped = im.crop((left, top, right, bottom))
    # Mild center zoom so droplet dominates small sizes
    cw, ch = cropped.size
    inset = int(min(cw, ch) * 0.04)
    return cropped.crop((inset, inset, cw - inset, ch - inset))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    args = parser.parse_args()

    WORK.mkdir(parents=True, exist_ok=True)
    im = Image.open(args.src).convert("RGBA")
    print("src", args.src.name, im.size)

    core = extract_mark(im)
    core_resized = core.resize((SAFE, SAFE), Image.Resampling.LANCZOS)

    fg = Image.new("RGBA", (FG_SIZE, FG_SIZE), (0, 0, 0, 0))
    off = (FG_SIZE - SAFE) // 2
    # Fill safe tile with solid BG first so adaptive crop never shows holes
    tile = Image.new("RGBA", (SAFE, SAFE), BG)
    tile.paste(core_resized, (0, 0), core_resized)
    fg.paste(tile, (off, off))

    fg.save(WORK / "ic_launcher_foreground_1024.png")

    full = Image.new("RGBA", (FG_SIZE, FG_SIZE), BG)
    full.paste(fg, (0, 0), fg)
    full.save(WORK / "ic_launcher_full_1024.png")
    make_round(full).save(WORK / "ic_launcher_round_1024.png")

    brand = tile.resize((512, 512), Image.Resampling.LANCZOS)
    brand.save(WORK / "brand_logo.png")

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

    # Master for rebuilds (project-local)
    im.save(WORK / "lingban_icon_master.png")
    # Do not keep a second huge copy under drawable/

    full.resize((48, 48), Image.Resampling.LANCZOS).save(WORK / "preview_48.png")
    full.resize((96, 96), Image.Resampling.LANCZOS).save(WORK / "preview_96.png")
    print("done ->", WORK)


if __name__ == "__main__":
    main()
