"""
Generate Android Adaptive Icon assets from the provided logo.
- background: solid #1A1B2E
- foreground: extracted chat-bubble on transparent canvas, content within 66/108 safe zone
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SRC = Path(
    r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets"
    r"\c__Users_Administrator_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"16290efc8cc7b6f901f446c04d2de435_images_ce83649e7d3e7491338af65d3a653862-4f397a68-447c-44e1-ae39-709bb14cedd5.png"
)
RES = Path(r"C:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\app\src\main\res")
PREVIEW_DIR = Path(r"C:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\tools\icon_previews")
BG = (0x1A, 0x1B, 0x2E, 255)

# Adaptive icon: 108dp canvas, safe content ~66dp => scale 66/108
SAFE_RATIO = 66 / 108


def find_logo_bbox(img: Image.Image) -> tuple[int, int, int, int]:
    pixels = img.load()
    w, h = img.size
    min_x, min_y, max_x, max_y = w, h, 0, 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if a < 8:
                continue
            # skip near-white page background
            if r > 240 and g > 240 and b > 240:
                continue
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)
    pad = 4
    return (
        max(0, min_x - pad),
        max(0, min_y - pad),
        min(w, max_x + pad + 1),
        min(h, max_y + pad + 1),
    )


def is_dark_backdrop(r: int, g: int, b: int) -> bool:
    """True for the indigo circle / faint circuit (not the colorful bubble)."""
    brightness = (r + g + b) / 3.0
    maxc = max(r, g, b)
    minc = min(r, g, b)
    sat = (maxc - minc) / max(maxc, 1)
    # Near #1A1B2E and slightly lighter circuit traces
    if brightness < 70 and sat < 0.42:
        return True
    if brightness < 48:
        return True
    # Faint blue-ish circuit on dark field
    if brightness < 90 and b >= g and b >= r and sat < 0.55 and maxc < 120:
        return True
    return False


def is_fringe_or_paper(r: int, g: int, b: int) -> bool:
    """Outer paper / anti-aliased circle fringe — must not enter foreground."""
    brightness = (r + g + b) / 3.0
    maxc = max(r, g, b)
    minc = min(r, g, b)
    sat = (maxc - minc) / max(maxc, 1)
    if r > 200 and g > 200 and b > 200:
        return True
    if brightness > 170 and sat < 0.18:
        return True
    if brightness > 140 and sat < 0.12:
        return True
    return False


def extract_bubble(cropped: Image.Image) -> Image.Image:
    """Remove dark circular backdrop; keep gradient bubble + cyan nodes."""
    w, h = cropped.size
    cx, cy = w / 2.0, h / 2.0
    # Shrink slightly to drop anti-aliased white fringe on circle edge
    radius = min(w, h) / 2.0 - max(6, int(min(w, h) * 0.02))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    src = cropped.load()
    dst = out.load()
    kept = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 8:
                continue
            if (x - cx) ** 2 + (y - cy) ** 2 > radius * radius:
                continue
            if is_fringe_or_paper(r, g, b):
                continue
            if is_dark_backdrop(r, g, b):
                continue
            dst[x, y] = (r, g, b, 255)
            kept += 1
    print(f"extracted pixels: {kept}")
    alpha = out.getchannel("A").filter(ImageFilter.GaussianBlur(0.45))
    out.putalpha(alpha)
    return out


def content_bbox(img: Image.Image) -> tuple[int, int, int, int]:
    pixels = img.load()
    w, h = img.size
    min_x, min_y, max_x, max_y = w, h, 0, 0
    found = False
    for y in range(h):
        for x in range(w):
            if pixels[x, y][3] > 20:
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if not found:
        return 0, 0, w, h
    return min_x, min_y, max_x + 1, max_y + 1


def place_in_safe_zone(fg_content: Image.Image, size: int) -> Image.Image:
    """108-unit canvas; content fits in centered SAFE_RATIO box."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    box = max(1, int(size * SAFE_RATIO))
    # Trim transparent margins then fit into safe box
    bx0, by0, bx1, by1 = content_bbox(fg_content)
    trimmed = fg_content.crop((bx0, by0, bx1, by1))
    tw, th = trimmed.size
    scale = min(box / tw, box / th)
    nw, nh = max(1, int(tw * scale)), max(1, int(th * scale))
    scaled = trimmed.resize((nw, nh), Image.Resampling.LANCZOS)
    ox = (size - nw) // 2
    oy = (size - nh) // 2
    canvas.paste(scaled, (ox, oy), scaled)
    return canvas


def compose_legacy(fg: Image.Image, size: int) -> Image.Image:
    bg = Image.new("RGBA", (size, size), BG)
    layer = fg.resize((size, size), Image.Resampling.LANCZOS) if fg.size != (size, size) else fg
    bg.alpha_composite(layer)
    return bg.convert("RGBA")


def circular_brand(fg_bubble: Image.Image, size: int = 512) -> Image.Image:
    """In-app logo: dark circle + bubble (no outer white)."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((0, 0, size - 1, size - 1), fill=BG[:3])
    # Bubble slightly inset so it matches adaptive safe feel
    inset = int(size * 0.12)
    area = size - inset * 2
    bx0, by0, bx1, by1 = content_bbox(fg_bubble)
    trimmed = fg_bubble.crop((bx0, by0, bx1, by1))
    tw, th = trimmed.size
    scale = min(area / tw, area / th)
    nw, nh = max(1, int(tw * scale)), max(1, int(th * scale))
    scaled = trimmed.resize((nw, nh), Image.Resampling.LANCZOS)
    canvas.paste(scaled, ((size - nw) // 2, (size - nh) // 2), scaled)
    return canvas


def mask_preview(composed: Image.Image, shape: str, out: Path) -> None:
    size = 512
    base = composed.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    elif shape == "squircle":
        r = int(size * 0.22)
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=255)
    else:  # square
        draw.rectangle((0, 0, size - 1, size - 1), fill=255)
    page = Image.new("RGBA", (size + 80, size + 80), (237, 238, 240, 255))
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon.paste(base, (0, 0))
    icon.putalpha(mask)
    page.paste(icon, (40, 40), icon)
    page.save(out, "PNG")


def main() -> None:
    if not SRC.exists():
        # fallback glob
        assets = SRC.parent if SRC.parent.exists() else Path(
            r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets"
        )
        matches = list(assets.glob("*ce83649e*"))
        if not matches:
            raise SystemExit(f"source image not found: {SRC}")
        img_path = matches[0]
    else:
        img_path = SRC

    print("source:", img_path)
    raw = Image.open(img_path).convert("RGBA")
    bbox = find_logo_bbox(raw)
    print("logo bbox:", bbox)
    cropped = raw.crop(bbox)

    bubble = extract_bubble(cropped)
    bx0, by0, bx1, by1 = content_bbox(bubble)
    coverage = (bx1 - bx0) * (by1 - by0) / max(1, cropped.size[0] * cropped.size[1])
    print(f"content coverage vs crop: {coverage:.3f}")

    # Fallback: if extraction too sparse, use full emblem scaled into safe zone
    if coverage < 0.05:
        print("extraction sparse → fallback to full emblem in safe zone")
        # Make outer white transparent, keep dark circle + bubble
        w, h = cropped.size
        px = cropped.load()
        full = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        fp = full.load()
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if r > 240 and g > 240 and b > 240:
                    continue
                fp[x, y] = (r, g, b, 255)
        bubble = full

    # Master foreground @ xxxhdpi adaptive (108 * 4 = 432)
    fg_master = place_in_safe_zone(bubble, 432)

    drawable = RES / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    fg_master.save(drawable / "ic_launcher_foreground.png", "PNG")
    # Remove old webp foreground if present to avoid resource conflict
    old_fg = drawable / "ic_launcher_foreground.webp"
    if old_fg.exists():
        old_fg.unlink()
        print("removed", old_fg)

    brand = circular_brand(bubble, 512)
    brand.save(drawable / "brand_logo.png", "PNG")
    old_brand = drawable / "brand_logo.webp"
    if old_brand.exists():
        old_brand.unlink()
        print("removed", old_brand)

    # Also keep a transparent bubble-only asset for Fit usage if needed
    bubble_only = place_in_safe_zone(bubble, 512)
    # brand is circular emblem — good for in-app circular clips

    legacy_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in legacy_sizes.items():
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        icon = compose_legacy(fg_master, size)
        # Prefer PNG for quality; delete old webp
        for name in ("ic_launcher", "ic_launcher_round"):
            webp = d / f"{name}.webp"
            if webp.exists():
                webp.unlink()
            icon.save(d / f"{name}.png", "PNG")

    # Previews
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    composed_512 = compose_legacy(fg_master, 512)
    mask_preview(composed_512, "circle", PREVIEW_DIR / "launcher_circle.png")
    mask_preview(composed_512, "squircle", PREVIEW_DIR / "launcher_squircle.png")
    mask_preview(composed_512, "square", PREVIEW_DIR / "launcher_square.png")
    brand.save(PREVIEW_DIR / "brand_logo_preview.png", "PNG")
    fg_master.resize((512, 512), Image.Resampling.LANCZOS).save(
        PREVIEW_DIR / "foreground_layer.png", "PNG"
    )
    # Show safe zone guide
    guide = Image.new("RGBA", (512, 512), BG)
    guide.alpha_composite(fg_master.resize((512, 512), Image.Resampling.LANCZOS))
    gdraw = ImageDraw.Draw(guide)
    safe = int(512 * SAFE_RATIO)
    off = (512 - safe) // 2
    gdraw.rectangle((off, off, off + safe, off + safe), outline=(255, 255, 0, 180), width=2)
    mask = int(512 * 72 / 108)
    moff = (512 - mask) // 2
    gdraw.ellipse((moff, moff, moff + mask, moff + mask), outline=(0, 200, 255, 160), width=2)
    guide.save(PREVIEW_DIR / "safe_zone_guide.png", "PNG")

    print("done. previews in", PREVIEW_DIR)


if __name__ == "__main__":
    main()
