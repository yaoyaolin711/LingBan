from PIL import Image
from pathlib import Path

src = Path(r"C:\Users\Administrator\.cursor\projects\c-Users-Administrator-Desktop-Agent\assets")
files = list(src.glob("*ce83649e*"))
print("found", files)
img_path = files[0]
img = Image.open(img_path).convert("RGBA")
print("size", img.size)

pixels = img.load()
w, h = img.size
min_x, min_y, max_x, max_y = w, h, 0, 0
for y in range(h):
    for x in range(w):
        r, g, b, a = pixels[x, y]
        if a > 10 and not (r > 245 and g > 245 and b > 245):
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)

pad = 8
crop = img.crop((max(0, min_x - pad), max(0, min_y - pad), min(w, max_x + pad + 1), min(h, max_y + pad + 1)))
cw, ch = crop.size
side = max(cw, ch)
sq = Image.new("RGBA", (side, side), (10, 18, 36, 255))
sq.paste(crop, ((side - cw) // 2, (side - ch) // 2), crop)

res = Path(r"C:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\app\src\main\res")


def make_fg(size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = sq.resize((int(size * 0.72), int(size * 0.72)), Image.Resampling.LANCZOS)
    off = (size - content.size[0]) // 2
    canvas.paste(content, (off, off), content)
    return canvas


def make_full(size: int) -> Image.Image:
    return sq.resize((size, size), Image.Resampling.LANCZOS)


sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in sizes.items():
    d = res / folder
    d.mkdir(parents=True, exist_ok=True)
    make_full(size).save(d / "ic_launcher.webp", "WEBP", quality=92)
    make_full(size).save(d / "ic_launcher_round.webp", "WEBP", quality=92)

drawable = res / "drawable"
drawable.mkdir(parents=True, exist_ok=True)
make_fg(432).save(drawable / "ic_launcher_foreground.webp", "WEBP", quality=95)
sq.resize((512, 512), Image.Resampling.LANCZOS).save(drawable / "brand_logo.webp", "WEBP", quality=95)
print("icons written")
