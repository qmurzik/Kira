#!/usr/bin/env python3
"""Рисует иконку TwinFTP 1024×1024 (графитовый фон, две фиолетовые панели, стрелки обмена)."""
import sys
from PIL import Image, ImageDraw, ImageFilter

S = 4  # суперсэмплинг для сглаживания
W = 1024 * S
out = sys.argv[1] if len(sys.argv) > 1 else "AppIcon-1024.png"

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

img = Image.new("RGB", (W, W))
px = img.load()
top, bottom = (44, 42, 54), (18, 17, 24)
for y in range(W):
    c = lerp(top, bottom, y / W)
    for x in range(W):
        px[x, y] = c

# мягкое фиолетовое свечение
glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse([W * 0.12, W * 0.18, W * 0.88, W * 0.94], fill=(124, 87, 230, 90))
glow = glow.filter(ImageFilter.GaussianBlur(W * 0.09))
img = Image.alpha_composite(img.convert("RGBA"), glow)

d = ImageDraw.Draw(img)
r = int(W * 0.06)
# две панели
left = [W * 0.17, W * 0.22, W * 0.47, W * 0.78]
right = [W * 0.53, W * 0.22, W * 0.83, W * 0.78]
d.rounded_rectangle(left, r, fill=(52, 49, 66), outline=(150, 120, 255), width=int(W * 0.012))
d.rounded_rectangle(right, r, fill=(124, 87, 230), outline=(176, 150, 255), width=int(W * 0.012))
# строки «файлов»
for i in range(4):
    y = W * (0.31 + i * 0.1)
    d.rounded_rectangle([W * 0.22, y, W * (0.42 - (0.06 if i % 2 else 0)), y + W * 0.035], W * 0.017, fill=(120, 112, 150))
    d.rounded_rectangle([W * 0.58, y, W * (0.78 - (0.08 if i % 2 == 0 else 0)), y + W * 0.035], W * 0.017, fill=(236, 230, 255))

# стрелки обмена в центре
cx = W * 0.5
def arrow(y, direction, color):
    w, h = W * 0.2, W * 0.07
    x0, x1 = cx - w / 2, cx + w / 2
    shaft = h * 0.36
    if direction > 0:
        head = x1 - h * 0.9
        d.rounded_rectangle([x0, y - shaft / 2, head + 2, y + shaft / 2], shaft / 2, fill=color)
        d.polygon([(head, y - h / 2 - h * 0.15), (x1, y), (head, y + h / 2 + h * 0.15)], fill=color)
    else:
        head = x0 + h * 0.9
        d.rounded_rectangle([head - 2, y - shaft / 2, x1, y + shaft / 2], shaft / 2, fill=color)
        d.polygon([(head, y - h / 2 - h * 0.15), (x0, y), (head, y + h / 2 + h * 0.15)], fill=color)

# подложка под стрелки
d.rounded_rectangle([cx - W * 0.13, W * 0.4, cx + W * 0.13, W * 0.6], W * 0.05, fill=(24, 23, 31), outline=(176, 150, 255), width=int(W * 0.008))
arrow(W * 0.46, 1, (255, 255, 255))
arrow(W * 0.54, -1, (190, 170, 255))

img = img.convert("RGB").resize((1024, 1024), Image.LANCZOS)
img.save(out, "PNG")
print("saved", out)
