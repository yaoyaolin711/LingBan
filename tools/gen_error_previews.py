"""Render mock UI screenshots for AppError friendly messaging."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"C:\Users\Administrator\Desktop\工作台\Agent移动端聊天框架\tools\error_previews")
OUT.mkdir(parents=True, exist_ok=True)

BG = (0xED, 0xEE, 0xF0, 255)
CARD = (255, 255, 255, 255)
TEXT = (0x1A, 0x1A, 0x1E, 255)
SECONDARY = (0x6B, 0x6B, 0x72, 255)
ACCENT = (0x4A, 0x5F, 0xD9, 255)
ERR_BG = (0xF8, 0xEE, 0xEE, 255)
ERR_BORDER = (0xD4, 0xA5, 0xA5, 255)
ERR_TEXT = (0x8F, 0x5A, 0x5A, 255)
USER_BG = (0xF3, 0xF4, 0xF6, 255)


def font(size: int):
    for name in ("msyh.ttc", "segoeui.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_rect(draw, xy, radius, fill=None, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def chat_error(path: Path, title: str, user_msg: str, friendly: str, debug: str | None = None):
    w, h = 420, 720
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    f_title = font(22)
    f_body = font(16)
    f_small = font(13)

    # top bar
    d.text((56, 28), title, fill=TEXT, font=f_title)
    d.text((56, 58), "在线", fill=SECONDARY, font=f_small)

    # user bubble
    ub = (120, 120, 390, 180)
    rounded_rect(d, ub, 16, fill=USER_BG)
    d.text((140, 140), user_msg, fill=TEXT, font=f_body)

    # error card
    ey0 = 200
    ey1 = 360 if debug else 310
    rounded_rect(d, (24, ey0, 320, ey1), 16, fill=ERR_BG, outline=ERR_BORDER, width=2)
    # wrap friendly text
    lines = []
    line = ""
    for ch in friendly:
        test = line + ch
        if f_body.getlength(test) > 260:
            lines.append(line)
            line = ch
        else:
            line = test
    if line:
        lines.append(line)
    y = ey0 + 18
    for ln in lines:
        d.text((40, y), ln, fill=ERR_TEXT, font=f_body)
        y += 24
    if debug:
        d.text((40, y + 4), debug[:48], fill=SECONDARY, font=f_small)
        y += 28
    d.text((40, ey1 - 40), "重试", fill=ACCENT, font=f_body)

    # input bar
    rounded_rect(d, (56, 640, 300, 690), 20, fill=CARD, outline=(0xE5, 0xE6, 0xEA), width=1)
    d.ellipse((320, 642, 368, 690), fill=ACCENT)

    img.save(path)
    print("wrote", path)


def settings_inline(path: Path):
    w, h = 420, 560
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    f_title = font(20)
    f_body = font(15)
    f_small = font(13)
    d.text((24, 24), "编辑 Provider", fill=TEXT, font=f_title)
    fields = [("名称", "My Model"), ("Base URL", "https://api.example.com/v1"), ("API Key", "sk-••••wrong"), ("Model", "gpt-4o-mini")]
    y = 70
    for label, value in fields:
        rounded_rect(d, (24, y, 396, y + 56), 12, fill=CARD, outline=ERR_BORDER if label == "API Key" else (0xE5, 0xE6, 0xEA), width=1)
        d.text((36, y + 8), label, fill=SECONDARY, font=f_small)
        d.text((36, y + 28), value, fill=TEXT, font=f_body)
        y += 68
    d.text((24, y + 4), "模型配置有误，请前往设置检查 API Key 是否正确", fill=ERR_TEXT, font=f_small)
    d.text((24, y + 28), "ProviderHttpException: HTTP 401: {\"error\"...}", fill=SECONDARY, font=f_small)
    d.text((300, y + 60), "测试连接", fill=ACCENT, font=f_body)
    img.save(path)
    print("wrote", path)


def snackbar_global(path: Path):
    w, h = 420, 200
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    f_body = font(15)
    rounded_rect(d, (24, 60, 396, 140), 12, fill=(0x32, 0x32, 0x36, 255))
    d.text((40, 88), "网络连接不可用，请检查网络后重试", fill=(255, 255, 255, 255), font=f_body)
    img.save(path)
    print("wrote", path)


if __name__ == "__main__":
    chat_error(
        OUT / "chat_invalid_api_key.png",
        title = "旅行顾问",
        user_msg="帮我规划周末行程",
        friendly="模型配置有误，请前往设置检查 API Key 是否正确",
        debug="ProviderHttpException: HTTP 401: invalid_api_key",
    )
    chat_error(
        OUT / "chat_network_unavailable.png",
        title="旅行顾问",
        user_msg="你好",
        friendly="网络连接不可用，请检查网络后重试",
        debug="UnknownHostException: Unable to resolve host",
    )
    chat_error(
        OUT / "chat_timeout.png",
        title="旅行顾问",
        user_msg="继续",
        friendly="请求超时了，可能是网络较慢，点击重试",
    )
    settings_inline(OUT / "settings_inline_api_key.png")
    snackbar_global(OUT / "snackbar_network.png")
