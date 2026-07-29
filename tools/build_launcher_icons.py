"""Build Android launcher icons from LingBan master artwork."""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageStat

DEFAULT_SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets"
    r"\c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"16290efc8cc7b6f901f446c04d2de435_images_z-image-turbo_00058_"
    r"-bd76d2de-2372-497a-9aaa-239a24064a91.png"
)
RES = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\app\src\main\res")
WORK = Path(r"c:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\tools\icon_work")

FG_SIZE = 1024
SAFE = int(FG_SIZE * 0.76)


def make_round(src: Image.Image) -> Image.Image:
    size = src.size[0]
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0))
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((1, 1, size - 2, size - 2), fill=255)
    out.putalpha(mask)
    return out


def luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = rgb[:3]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def sample_bg(im: Image.Image) -> tuple[int, int, int, 255]:
    """Sample near center-edge of dark plate (avoid metal ring)."""
    w, h = im.size
    pts = [
        (int(w * 0.18), int(h * 0.18)),
        (int(w * 0.82), int(h * 0.18)),
        (int(w * 0.18), int(h * 0.82)),
        (int(w * 0.82), int(h * 0.82)),
        (int(w * 0.5), int(h * 0.12)),
        (int(w * 0.12), int(h * 0.5)),
    ]
    dark = [im.getpixel(p)[:3] for p in pts if luminance(im.getpixel(p)[:3]) < 90]
    if not dark:
        dark = [(32, 18, 58)]
    r = int(sum(c[0] for c in dark) / len(dark))
    g = int(sum(c[1] for c in dark) / len(dark))
    b = int(sum(c[2] for c in dark) / len(dark))
    return (r, g, b, 255)


def crop_dark_plate(im: Image.Image) -> Image.Image:
    """
    Drop light mockup margin / baked squircle outline.
    Keep the dark purple plate + rose-gold mark.
    """
    rgb = im.convert("RGB")
    w, h = rgb.size
    # Find bbox of sufficiently dark pixels (the purple plate)
    threshold = 110
    xs: list[int] = []
    ys: list[int] = []
    # Sample every 4px for speed
    for y in range(0, h, 4):
        for x in range(0, w, 4):
            if luminance(rgb.getpixel((x, y))) < threshold:
                xs.append(x)
                ys.append(y)
    if not xs:
        # fallback: center crop
        m = int(min(w, h) * 0.12)
        return im.crop((m, m, w - m, h - m))

    pad = 8
    left = max(0, min(xs) - pad)
    top = max(0, min(ys) - pad)
    right = min(w, max(xs) + pad)
    bottom = min(h, max(ys) + pad)
    # Make square
    side = max(right - left, bottom - top)
    cx = (left + right) // 2
    cy = (top + bottom) // 2
    half = side // 2
    left = max(0, cx - half)
    top = max(0, cy - half)
    right = min(w, left + side)
    bottom = min(h, top + side)
    left = max(0, right - side)
    top = max(0, bottom - side)
    cropped = im.crop((left, top, right, bottom))

    # Pull in a bit more to kill rounded-corner bevel / pin dots
    cw, ch = cropped.size
    inset = int(min(cw, ch) * 0.06)
    return cropped.crop((inset, inset, cw - inset, ch - inset))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    args = parser.parse_args()

    WORK.mkdir(parents=True, exist_ok=True)
    im = Image.open(args.src).convert("RGBA")
    # Normalize non-square masters
    if im.size[0] != im.size[1]:
        side = max(im.size)
        canvas = Image.new("RGBA", (side, side), im.getpixel((0, 0)))
        canvas.paste(im, ((side - im.size[0]) // 2, (side - im.size[1]) // 2))
        im = canvas

    print("src", args.src.name, im.size)
    core = crop_dark_plate(im)
    bg = sample_bg(core)
    print("bg", bg, "core", core.size)

    # Mild zoom so metal ring reads at 48dp
    cw, ch = core.size
    zoom = int(min(cw, ch) * 0.03)
    core = core.crop((zoom, zoom, cw - zoom, ch - zoom))
    core_resized = core.resize((SAFE, SAFE), Image.Resampling.LANCZOS)

    tile = Image.new("RGBA", (SAFE, SAFE), bg)
    tile.paste(core_resized, (0, 0), core_resized)

    fg = Image.new("RGBA", (FG_SIZE, FG_SIZE), (0, 0, 0, 0))
    off = (FG_SIZE - SAFE) // 2
    # Adaptive foreground: only the mark area; outer is transparent, OS uses bg color
    # Put full purple plate in safe zone for continuity under round masks
    fg.paste(tile, (off, off))

    fg.save(WORK / "ic_launcher_foreground_1024.png")

    full = Image.new("RGBA", (FG_SIZE, FG_SIZE), bg)
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

    # Write adaptive background color resource
    colors = RES / "values" / "colors.xml"
    hex_bg = f"#{bg[0]:02X}{bg[1]:02X}{bg[2]:02X}"
    colors.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f"    <!-- Adaptive icon background — sampled from mark -->\n"
        f'    <color name="ic_launcher_background">{hex_bg}</color>\n'
        "</resources>\n",
        encoding="utf-8",
    )
    print("ic_launcher_background", hex_bg)

    im.save(WORK / "lingban_icon_master.png")
    full.resize((48, 48), Image.Resampling.LANCZOS).save(WORK / "preview_48.png")
    full.resize((96, 96), Image.Resampling.LANCZOS).save(WORK / "preview_96.png")
    print("done ->", WORK)


if __name__ == "__main__":
    main()
