#!/usr/bin/env python3
"""Генератор фонов локаций (SVG, 1920x1080, многослойные).

Каждая локация состоит из нескольких слоёв для параллакса и смены времени суток.
Координаты интерактивных объектов дублируются в data/locations/*.json.
"""
import json
import math
import os
import random

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "assets", "bg")
W, H = 1920, 1080
OL = "#3a2a55"  # контур


def save(path, body, defs="", w=W, h=H, vb=None):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    vb = vb or f"0 0 {w} {h}"
    with open(full, "w", encoding="utf-8") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="{vb}">'
                f'<defs>{defs}</defs>{body}</svg>')


def lg(id_, stops, x2=0, y2=1, x1=0, y1=0):
    out = ""
    for st in stops:
        a = f' stop-opacity="{st[2]}"' if len(st) > 2 else ""
        out += f'<stop offset="{st[0]}" stop-color="{st[1]}"{a}/>'
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{out}</linearGradient>'


def rg(id_, stops, cx=0.5, cy=0.5, r=0.5):
    s = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>' for o, c, a in stops)
    return f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}">{s}</radialGradient>'


def rect(x, y, w, h, fill, rx=0, stroke=OL, sw=3, extra=""):
    st = f'stroke="{stroke}" stroke-width="{sw}"' if stroke else ""
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" {st} {extra}/>'


def star4(x, y, r, fill="#fff6c9", op=1.0):
    k = r * 0.28
    return (f'<path d="M{x} {y-r} L{x+k} {y-k} L{x+r} {y} L{x+k} {y+k} L{x} {y+r} L{x-k} {y+k} L{x-r} {y} L{x-k} {y-k} Z" '
            f'fill="{fill}" opacity="{op}"/>')


# =====================================================================
#                               КОМНАТА
# =====================================================================
WALLS = {
    "lavender": ("#d3c2ee", "#b9a3dd", "#c6b3e6", "#a58bd0"),
    "night": ("#4d4f8e", "#3a3b72", "#45468a", "#2f3064"),
    "peach": ("#f5d3c9", "#e8b6ad", "#f0c6bc", "#d79d97"),
    "mint": ("#cfe8e0", "#a9cfc6", "#c0ddd5", "#8fbab1"),
}


def room_wall(variant):
    top, bot, pat, base = WALLS[variant]
    defs = lg("wall", [(0, top), (1, bot)]) + lg("floor", [(0, "#8d6f99"), (1, "#5e4569")]) + \
        lg("rug", [(0, "#efe4ff"), (1, "#cdb8f0")]) + \
        rg("vign", [(0, "#000", 0), (0.7, "#000", 0), (1, "#1a1030", 0.35)], r=0.75)
    b = f'<rect width="{W}" height="790" fill="url(#wall)"/>'
    # узор обоев: вертикальные полосы + звёздочки
    for x in range(0, W, 64):
        b += f'<rect x="{x}" y="0" width="22" height="780" fill="{pat}" opacity="0.35"/>'
    rnd = random.Random(7)
    for i in range(70):
        b += star4(rnd.randint(10, W - 10), rnd.randint(40, 740), rnd.choice([5, 6, 8]), "#ffffff", 0.25)
    # карниз под потолком
    b += rect(0, 0, W, 34, base, stroke=None) + rect(0, 30, W, 8, "#ffffff", stroke=None, extra='opacity="0.35"')
    # пол
    b += f'<rect y="780" width="{W}" height="300" fill="url(#floor)"/>'
    vx, vy = 960, 300
    for i in range(-16, 17):
        x0 = 960 + i * 150
        x1 = vx + (x0 - vx) * (1080 - vy) / (780 - vy)
        b += f'<path d="M{x0 * 0.5 + 480} 780 L{x1} 1080" stroke="#4a3556" stroke-width="2" opacity="0.5"/>'
    for y in (820, 880, 960, 1060):
        b += f'<path d="M0 {y} L{W} {y}" stroke="#4a3556" stroke-width="1.5" opacity="0.25"/>'
    # плинтус
    b += rect(0, 770, W, 22, "#f3ecfb", stroke=OL, sw=2)
    # ковёр
    b += (f'<path d="M560 880 L1360 880 L1520 1060 L400 1060 Z" fill="url(#rug)" stroke="{OL}" stroke-width="3"/>'
          f'<path d="M600 900 L1320 900 L1460 1040 L460 1040 Z" fill="none" stroke="#b49ae6" stroke-width="4" stroke-dasharray="14 10"/>')
    for i in range(7):
        b += star4(700 + i * 90, 970 + (i % 2) * 20, 12, "#b49ae6", 0.8)
    b += f'<rect width="{W}" height="{H}" fill="url(#vign)"/>'
    save(f"room/wall_{variant}.svg", b, defs)


SKIES = {
    "morning": [(0, "#9ec9f2"), (0.6, "#f6d3e6"), (1, "#ffe7c4")],
    "day": [(0, "#6fb2f0"), (0.7, "#b4dcff"), (1, "#e2f1ff")],
    "evening": [(0, "#4a3f8f"), (0.45, "#c46c9e"), (0.8, "#f6a37a"), (1, "#ffd3a1")],
    "night": [(0, "#0b0c2a"), (0.6, "#1d1d52"), (1, "#3a2f6e")],
}

WIN = (400, 110, 380, 360)  # окно x,y,w,h


def window_sky(phase):
    x, y, w, h = WIN
    defs = lg("sky", SKIES[phase]) + rg("sun", [(0, "#fff8e0", 1), (0.3, "#ffe9b0", 0.8), (1, "#ffe9b0", 0)])
    b = f'<rect x="{x-12}" y="{y-12}" width="{w+24}" height="{h+24}" fill="url(#sky)"/>'
    rnd = random.Random(3)
    if phase == "night":
        for i in range(60):
            b += f'<circle cx="{rnd.uniform(x, x+w)}" cy="{rnd.uniform(y, y+h*0.75)}" r="{rnd.choice([0.8, 1.2, 1.8])}" fill="#fff" opacity="{rnd.uniform(0.4, 1)}"/>'
        b += '<circle cx="680" cy="190" r="34" fill="#fff6dc"/><circle cx="694" cy="182" r="30" fill="#1b1b4e" opacity="0.0"/>'
        b += rg("moon", [(0, "#fff6dc", 0.5), (1, "#fff6dc", 0)]).replace("radialGradient", "radialGradient")
    elif phase == "evening":
        b += f'<circle cx="520" cy="400" r="120" fill="url(#sun)"/><circle cx="520" cy="400" r="34" fill="#fff1c9"/>'
    elif phase == "morning":
        b += f'<circle cx="700" cy="330" r="130" fill="url(#sun)"/><circle cx="700" cy="330" r="30" fill="#fffbe8"/>'
    else:
        b += f'<circle cx="660" cy="170" r="140" fill="url(#sun)"/><circle cx="660" cy="170" r="32" fill="#fffef4"/>'
    # облака
    if phase != "night":
        cc = {"morning": "#fff4fa", "day": "#ffffff", "evening": "#f7b8c8"}[phase]
        for cx, cy, s in [(470, 200, 1.0), (640, 260, 0.8), (560, 150, 0.6)]:
            b += (f'<g opacity="0.85" transform="translate({cx} {cy}) scale({s})">'
                  f'<ellipse cx="0" cy="0" rx="60" ry="20" fill="{cc}"/><ellipse cx="-25" cy="-12" rx="30" ry="22" fill="{cc}"/>'
                  f'<ellipse cx="20" cy="-16" rx="34" ry="26" fill="{cc}"/></g>')
    # дальний город в окне
    city = {"morning": "#8f88b8", "day": "#7c86b8", "evening": "#4b3a6e", "night": "#16143a"}[phase]
    lit = phase in ("evening", "night")
    rnd = random.Random(11)
    bx = x - 10
    while bx < x + w + 10:
        bw = rnd.randint(26, 60)
        bh = rnd.randint(40, 140)
        b += f'<rect x="{bx}" y="{y+h-bh}" width="{bw}" height="{bh+20}" fill="{city}"/>'
        if lit:
            for wy in range(y + h - bh + 10, y + h, 16):
                for wx in range(bx + 6, bx + bw - 6, 12):
                    if rnd.random() < 0.35:
                        b += f'<rect x="{wx}" y="{wy}" width="5" height="7" fill="#ffd98a" opacity="0.85"/>'
        bx += bw + rnd.randint(0, 8)
    save(f"room/sky_{phase}.svg", b, defs)


def room_furniture():
    defs = (lg("wood", [(0, "#efe7f8"), (1, "#d7c9ec")]) + lg("wood2", [(0, "#a58ab8"), (1, "#7d6392")]) +
            lg("curt", [(0, "#c7a8f0"), (1, "#9d7fd6")], x2=1, y2=0) + lg("blanket", [(0, "#b9a0ee"), (1, "#8f72d1")]) +
            lg("screen", [(0, "#3b2f7a"), (1, "#7a5fd0")]) + lg("shelf", [(0, "#8b6f9e"), (1, "#6a5180")]) +
            rg("glow", [(0, "#fff0c8", 0.9), (1, "#fff0c8", 0)]))
    b = ""
    # ------------- окно (стекло — прозрачное, через него виден слой неба)
    x, y, w, h = WIN
    fr = 18
    b += (f'<path fill-rule="evenodd" fill="#f6f1fc" stroke="{OL}" stroke-width="3" '
          f'd="M{x-fr} {y-fr} h{w+2*fr} v{h+2*fr} h{-(w+2*fr)} Z M{x} {y} v{h} h{w} v{-h} Z"/>')
    b += rect(x + w / 2 - 7, y, 14, h, "#f6f1fc", stroke=OL, sw=2) + rect(x, y + h * 0.45 - 6, w, 12, "#f6f1fc", stroke=OL, sw=2)
    b += f'<path d="M{x+20} {y+20} L{x+80} {y+20} L{x+20} {y+120} Z" fill="#ffffff" opacity="0.18"/>'
    b += rect(x - 40, y + h + fr - 4, w + 80, 22, "#f6f1fc", rx=4, stroke=OL, sw=3)
    # шторы
    b += (f'<path d="M{x-70} 70 C{x-60} 200 {x-80} 330 {x-40} 470 C{x-30} 520 {x+10} 560 {x+20} 600 L{x-90} 600 C{x-110} 420 {x-100} 200 {x-110} 70 Z" '
          f'fill="url(#curt)" stroke="{OL}" stroke-width="3" opacity="0.95"/>')
    b += (f'<path d="M{x+w+70} 70 C{x+w+60} 200 {x+w+80} 330 {x+w+40} 470 C{x+w+30} 520 {x+w-10} 560 {x+w-20} 600 L{x+w+90} 600 C{x+w+110} 420 {x+w+100} 200 {x+w+110} 70 Z" '
          f'fill="url(#curt)" stroke="{OL}" stroke-width="3" opacity="0.95"/>')
    for cx in (x - 70, x + w + 70):
        for k in range(3):
            b += f'<path d="M{cx-20+k*20} 90 C{cx-26+k*20} 260 {cx-14+k*20} 400 {cx-4+k*20} 520" fill="none" stroke="#8a6cc4" stroke-width="2" opacity="0.6"/>'
    b += rect(x - 130, 62, w + 260, 12, "#e9def8", rx=6, stroke=OL, sw=2)
    # ------------- шкаф
    b += rect(30, 170, 220, 620, "url(#wood)", rx=8)
    b += rect(42, 186, 96, 520, "#f4eefb", rx=4, stroke=OL, sw=2) + rect(142, 186, 96, 520, "#f4eefb", rx=4, stroke=OL, sw=2)
    b += rect(42, 716, 196, 60, "#efe6f9", rx=4, stroke=OL, sw=2)
    b += '<circle cx="128" cy="450" r="6" fill="#b49ae6" stroke="#3a2a55" stroke-width="2"/><circle cx="152" cy="450" r="6" fill="#b49ae6" stroke="#3a2a55" stroke-width="2"/>'
    b += rect(120, 742, 40, 8, "#b49ae6", rx=4, stroke=OL, sw=1.5)
    b += rect(50, 120, 90, 50, "#f2c9d9", rx=4) + rect(140, 132, 70, 38, "#c9e1f2", rx=4)
    b += '<path d="M62 145 h66 M150 151 h50" stroke="#3a2a55" stroke-width="2" opacity="0.4"/>'
    # ------------- кровать
    b += (f'<path d="M260 700 L260 600 C260 560 300 550 340 550 L640 550 C680 550 720 560 720 600 L720 700 Z" fill="url(#wood2)" stroke="{OL}" stroke-width="3"/>')
    b += f'<path d="M290 600 C330 580 650 580 690 600" fill="none" stroke="#c9b4e0" stroke-width="4" opacity="0.6"/>'
    b += rect(250, 680, 480, 70, "#f7f3fd", rx=16)  # матрас
    b += f'<path d="M300 650 C300 620 330 612 380 614 C430 616 450 628 448 656 C446 684 420 690 380 690 C330 690 300 680 300 650 Z" fill="#ffffff" stroke="{OL}" stroke-width="3"/>'
    b += f'<path d="M470 652 C470 622 500 614 548 616 C598 618 616 630 614 658 C612 686 588 692 548 692 C500 692 470 682 470 652 Z" fill="#f3e9ff" stroke="{OL}" stroke-width="3"/>'
    b += (f'<path d="M240 720 C300 700 680 700 740 720 L760 860 C700 880 280 880 220 860 Z" fill="url(#blanket)" stroke="{OL}" stroke-width="3"/>'
          f'<path d="M244 724 C300 706 680 706 736 724 L738 748 C680 730 300 730 242 748 Z" fill="#f6f1fd" stroke="{OL}" stroke-width="2"/>')
    for sx, sy in [(330, 800), (450, 780), (580, 810), (680, 790), (400, 840), (620, 846)]:
        b += star4(sx, sy, 10, "#fff6c9", 0.9)
    b += rect(236, 850, 12, 40, "#6a5180", rx=3) + rect(732, 850, 12, 40, "#6a5180", rx=3)
    # ------------- тумба с проигрывателем
    b += rect(760, 650, 160, 140, "url(#wood2)", rx=6)
    b += rect(772, 700, 136, 80, "#5f4774", rx=4, stroke=OL, sw=2)
    for i in range(8):
        col = ["#f2a9c4", "#9ec5f2", "#f6d58a", "#b49ae6"][i % 4]
        b += rect(780 + i * 15, 708, 11, 66, col, rx=2, stroke=OL, sw=1.2)
    b += rect(768, 612, 144, 40, "#efe6f9", rx=6)  # корпус проигрывателя
    b += f'<path d="M885 620 L870 640" stroke="#3a2a55" stroke-width="4" stroke-linecap="round"/>'
    # ------------- стена с постером и фото (место будущей двери)
    b += rect(1150, 230, 150, 210, "#22224f", rx=4)
    for sx, sy, r in [(1180, 270, 3), (1210, 300, 4), (1250, 280, 3), (1270, 340, 4), (1230, 380, 3), (1190, 360, 3), (1260, 410, 2.5)]:
        b += f'<circle cx="{sx}" cy="{sy}" r="{r}" fill="#fff6c9"/>'
    b += '<path d="M1180 270 L1210 300 L1250 280 L1270 340 L1230 380 L1190 360 L1210 300" fill="none" stroke="#b49ae6" stroke-width="1.5" opacity="0.8"/>'
    b += '<path d="M1158 238 h134 v194 h-134 Z" fill="none" stroke="#f6e7b4" stroke-width="2" opacity="0.6"/>'
    b += rect(1160, 600, 70, 56, "#fff", rx=3) + rect(1166, 606, 58, 44, "#f2b5c9", rx=2, stroke=None)
    b += '<path d="M1170 640 l14 -16 l10 10 l12 -14 l12 20 Z" fill="#fff" opacity="0.7"/>'
    # ------------- книжный шкаф
    b += rect(1320, 150, 200, 640, "url(#shelf)", rx=6)
    rnd = random.Random(5)
    for i, sy in enumerate(range(170, 780, 122)):
        b += rect(1332, sy, 176, 104, "#4f3b63", rx=3, stroke=OL, sw=2)
        bx = 1338
        while bx < 1490:
            bw = rnd.randint(12, 22)
            bh = rnd.randint(70, 98)
            if rnd.random() < 0.12 and bx < 1460:
                # предмет вместо книги
                b += f'<circle cx="{bx+16}" cy="{sy+80}" r="16" fill="#9ec5f2" stroke="{OL}" stroke-width="2"/>'
                bx += 36
                continue
            col = rnd.choice(["#f2a9c4", "#9ec5f2", "#f6d58a", "#b49ae6", "#e7e0f7", "#8fd1c0", "#f0b38a"])
            tilt = rnd.choice([0, 0, 0, -6]) if bx > 1350 else 0
            b += f'<rect x="{bx}" y="{sy+104-bh}" width="{bw}" height="{bh}" rx="2" fill="{col}" stroke="{OL}" stroke-width="1.6" transform="rotate({tilt} {bx} {sy+104})"/>'
            b += f'<rect x="{bx+3}" y="{sy+104-bh+10}" width="{bw-6}" height="4" fill="#fff" opacity="0.5"/>'
            bx += bw + 2
    b += rect(1310, 140, 220, 16, "#7d6392", rx=4)
    # ------------- стол, компьютер, лампа
    b += rect(1540, 600, 360, 24, "#efe6f9", rx=6)
    b += rect(1556, 624, 18, 166, "#d7c9ec", rx=3) + rect(1866, 624, 18, 166, "#d7c9ec", rx=3)
    b += rect(1760, 630, 100, 120, "#e3d8f3", rx=4, stroke=OL, sw=2)
    b += '<circle cx="1810" cy="660" r="5" fill="#b49ae6"/><circle cx="1810" cy="716" r="5" fill="#b49ae6"/>'
    b += rect(1600, 400, 240, 160, "#2a2346", rx=12)  # монитор
    b += rect(1612, 412, 216, 132, "url(#screen)", rx=6, stroke=None)
    b += rect(1700, 560, 40, 30, "#d7c9ec", rx=2) + rect(1670, 588, 100, 12, "#d7c9ec", rx=4)
    b += rect(1620, 578, 90, 16, "#f6f1fc", rx=4, stroke=OL, sw=2)
    b += f'<path d="M1850 600 C1850 560 1840 520 1810 500" fill="none" stroke="{OL}" stroke-width="6"/>'
    b += f'<path d="M1780 470 L1840 470 L1830 510 L1790 510 Z" fill="#f6d58a" stroke="{OL}" stroke-width="3"/>'
    b += rect(1830, 590, 46, 12, "#d7c9ec", rx=4)
    b += f'<path d="M1574 566 C1574 590 1600 590 1600 566 Z" fill="#f2a9c4" stroke="{OL}" stroke-width="2"/>'  # кружка
    # стул (спинка)
    b += (f'<path d="M1650 640 C1650 620 1700 612 1730 612 C1760 612 1800 620 1800 640 L1792 760 L1658 760 Z" fill="#b49ae6" stroke="{OL}" stroke-width="3"/>'
          f'<path d="M1720 760 L1720 840 M1680 860 L1760 860" stroke="{OL}" stroke-width="8" stroke-linecap="round"/>')
    save("room/furniture.svg", b, defs)

    # ------------- передний план: пуф-диванчик, растение, подвесная лампа
    defs2 = lg("pouf", [(0, "#cbb2f2"), (1, "#8d6fd0")]) + lg("leaf", [(0, "#4f8f7a"), (1, "#2c5a52")]) + lg("pot", [(0, "#f2d9c9"), (1, "#d6b29f")])
    f = (f'<path d="M20 1080 C10 960 60 880 200 870 C340 860 440 890 460 960 C480 1020 470 1060 460 1080 Z" fill="url(#pouf)" stroke="{OL}" stroke-width="4"/>'
         f'<path d="M80 930 C120 900 200 896 250 920 C230 960 140 970 80 930 Z" fill="#f3e9ff" stroke="{OL}" stroke-width="3"/>'
         f'<path d="M260 900 C300 880 380 890 400 920 C370 950 300 950 260 900 Z" fill="#f2b5c9" stroke="{OL}" stroke-width="3"/>'
         f'<path d="M150 1000 C220 980 320 990 380 1010" fill="none" stroke="#7a5cc0" stroke-width="4" opacity="0.6"/>')
    f += rect(1790, 940, 120, 140, "url(#pot)", rx=10, stroke=OL, sw=4)
    rnd = random.Random(9)
    for i in range(8):
        a = -math.pi / 2 + rnd.uniform(-1.1, 1.1)
        L = rnd.uniform(160, 300)
        bx, by = 1850, 950
        ex, ey = bx + math.cos(a) * L, by + math.sin(a) * L
        nx, ny = -math.sin(a) * 60, math.cos(a) * 60
        f += (f'<path d="M{bx} {by} Q{(bx+ex)/2+nx} {(by+ey)/2+ny} {ex} {ey} Q{(bx+ex)/2-nx} {(by+ey)/2-ny} {bx} {by} Z" '
              f'fill="url(#leaf)" stroke="#1d3b36" stroke-width="3"/>'
              f'<path d="M{bx} {by} L{ex} {ey}" stroke="#7fbfa6" stroke-width="2" opacity="0.6"/>')
    f += f'<path d="M960 0 L960 90" stroke="{OL}" stroke-width="3"/>'
    f += f'<ellipse cx="960" cy="130" rx="62" ry="50" fill="#fff4dc" stroke="{OL}" stroke-width="3"/>'
    f += '<path d="M902 120 C940 132 980 132 1018 120 M906 146 C940 156 980 156 1014 146" fill="none" stroke="#e6cfa6" stroke-width="2"/>'
    save("room/fore.svg", f, defs2)


def room_extras():
    """Отдельные спрайты: пластинка, исчезающее фото, таинственная дверь."""
    vinyl = ('<circle cx="60" cy="60" r="56" fill="#1e1a2e" stroke="#3a2a55" stroke-width="3"/>'
             '<circle cx="60" cy="60" r="44" fill="none" stroke="#3b3550" stroke-width="2"/>'
             '<circle cx="60" cy="60" r="32" fill="none" stroke="#3b3550" stroke-width="2"/>'
             '<circle cx="60" cy="60" r="18" fill="#c9a6ff"/><circle cx="60" cy="60" r="3" fill="#1e1a2e"/>'
             '<path d="M30 30 A42 42 0 0 1 60 18" stroke="#fff" stroke-width="3" fill="none" opacity="0.3"/>')
    save("room/vinyl.svg", vinyl, w=120, h=120)
    photo = (rect(4, 4, 92, 74, "#fff", rx=3) + rect(10, 10, 80, 56, "#7cc3e8", rx=2, stroke=None) +
             '<rect x="10" y="44" width="80" height="22" fill="#f2d39c"/><circle cx="70" cy="26" r="9" fill="#fff6c9"/>'
             '<path d="M10 44 Q30 38 50 44 T90 44" fill="none" stroke="#fff" stroke-width="2"/>')
    save("room/photo_sea.svg", photo, w=100, h=82)
    defs = (lg("door", [(0, "#2b1f5e"), (1, "#14102e")]) + rg("dglow", [(0, "#d9c2ff", 0.85), (0.5, "#9c7bff", 0.35), (1, "#9c7bff", 0)]))
    d = (f'<ellipse cx="110" cy="290" rx="150" ry="320" fill="url(#dglow)"/>'
         f'<path d="M20 560 L20 120 C20 40 200 40 200 120 L200 560 Z" fill="url(#door)" stroke="#e8dcff" stroke-width="6"/>'
         f'<path d="M36 548 L36 126 C36 64 184 64 184 126 L184 548 Z" fill="none" stroke="#9c7bff" stroke-width="3"/>')
    rnd = random.Random(21)
    for i in range(40):
        d += f'<circle cx="{rnd.uniform(44, 176)}" cy="{rnd.uniform(110, 540)}" r="{rnd.choice([1, 1.5, 2.2])}" fill="#fff" opacity="{rnd.uniform(0.4, 1)}"/>'
    d += star4(110, 200, 22, "#fff6c9") + '<circle cx="168" cy="340" r="9" fill="#f6d58a" stroke="#3a2a55" stroke-width="2"/>'
    save("room/door.svg", d, w=220, h=600, vb="0 0 220 600")


# =====================================================================
#                              НОЧНОЙ ГОРОД
# =====================================================================

def city():
    defs = lg("csky", [(0, "#0d0b2b"), (0.55, "#2a1f5c"), (1, "#6a3f7e")]) + rg("moonglow", [(0, "#fff3d6", 0.6), (1, "#fff3d6", 0)])
    b = f'<rect width="{W}" height="{H}" fill="url(#csky)"/>'
    b += '<circle cx="1500" cy="170" r="160" fill="url(#moonglow)"/><circle cx="1500" cy="170" r="56" fill="#fff3d6"/>'
    b += '<circle cx="1480" cy="160" r="10" fill="#efe2bf"/><circle cx="1520" cy="190" r="7" fill="#efe2bf"/>'
    for cx, cy, s in [(1300, 210, 1.4), (1650, 250, 1.1), (400, 160, 1.6), (800, 240, 1.2)]:
        b += (f'<g opacity="0.55" transform="translate({cx} {cy}) scale({s})"><ellipse rx="120" ry="18" fill="#3f2f6e"/>'
              f'<ellipse cx="-40" cy="-10" rx="60" ry="20" fill="#3f2f6e"/><ellipse cx="40" cy="-14" rx="70" ry="22" fill="#3f2f6e"/></g>')
    save("city/sky.svg", b, defs)
    # дальние дома
    rnd = random.Random(4)
    b = ""
    x = -20
    while x < W + 20:
        w = rnd.randint(60, 140)
        h = rnd.randint(220, 520)
        y = 760 - h
        b += f'<rect x="{x}" y="{y}" width="{w}" height="{h+40}" fill="#241c4a"/>'
        if rnd.random() < 0.3:
            b += f'<rect x="{x+w/2-3}" y="{y-40}" width="6" height="40" fill="#241c4a"/><circle cx="{x+w/2}" cy="{y-42}" r="4" fill="#ff7aa8"/>'
        for wy in range(y + 16, 760, 26):
            for wx in range(x + 10, x + w - 12, 20):
                if rnd.random() < 0.28:
                    col = rnd.choice(["#ffd98a", "#ffc0e0", "#b9a6ff"])
                    b += f'<rect x="{wx}" y="{wy}" width="9" height="12" fill="{col}" opacity="0.7"/>'
        x += w + rnd.randint(-10, 10)
    save("city/far.svg", b)
    # средний план: улица и здания
    defs = (lg("bld1", [(0, "#3d2f6b"), (1, "#271d4c")]) + lg("bld2", [(0, "#4a3570"), (1, "#2c2050")]) +
            lg("road", [(0, "#2a2246"), (1, "#151127")]) + lg("cafe_win", [(0, "#ffd9a0"), (1, "#f7a8b8")]) +
            rg("warm", [(0, "#ffcf8a", 0.55), (1, "#ffcf8a", 0)]) + lg("walk", [(0, "#3a3060"), (1, "#2a2248")]))
    b = ""
    # кафе слева
    b += rect(60, 300, 640, 520, "url(#bld1)", stroke=OL)
    b += rect(40, 280, 680, 30, "#5a4588", rx=4)
    for wx in range(100, 680, 120):
        b += rect(wx, 330, 80, 100, "#3a2c66", rx=4, stroke=OL, sw=2) + f'<rect x="{wx+6}" y="{336}" width="68" height="88" fill="#ffd98a" opacity="{rnd.choice([0.0, 0.5, 0.7])}"/>'
    b += rect(100, 560, 300, 230, "url(#cafe_win)", rx=8, stroke=OL)  # витрина кафе
    b += '<path d="M100 640 h300 M250 560 v230" stroke="#3a2a55" stroke-width="4"/>'
    b += rect(440, 560, 120, 240, "#2c2050", rx=6, stroke=OL) + '<circle cx="540" cy="690" r="6" fill="#f6d58a"/>'
    b += rect(430, 540, 140, 14, "#5a4588", rx=4)
    # навес
    for i in range(10):
        col = "#f2a9c4" if i % 2 == 0 else "#f6f1fc"
        b += f'<path d="M{80+i*34} 500 L{114+i*34} 500 L{110+i*34} 548 C{104+i*34} 556 {90+i*34} 556 {84+i*34} 548 Z" fill="{col}" stroke="{OL}" stroke-width="2"/>'
    b += '<ellipse cx="250" cy="680" rx="260" ry="200" fill="url(#warm)"/>'
    # столик со стульями у кафе
    b += rect(150, 820, 120, 12, "#f6f1fc", rx=4, stroke=OL, sw=2) + '<path d="M210 832 V900 M180 900 H240" stroke="#3a2a55" stroke-width="6"/>'
    # дом в центре (с автобусной остановкой)
    b += rect(760, 220, 420, 600, "url(#bld2)", stroke=OL)
    for wy in range(260, 600, 90):
        for wx in range(800, 1140, 90):
            b += rect(wx, wy, 56, 60, "#2a1f4e", rx=3, stroke=OL, sw=2)
            if rnd.random() < 0.5:
                b += f'<rect x="{wx+4}" y="{wy+4}" width="48" height="52" fill="{rnd.choice(["#ffd98a", "#c9b4ff", "#ffc0e0"])}" opacity="0.65"/>'
    # остановка
    b += rect(860, 640, 260, 14, "#8f7ac0", rx=4) + '<path d="M876 654 V860 M1104 654 V860" stroke="#3a2a55" stroke-width="8"/>'
    b += rect(888, 664, 100, 120, "#9ad0ff", rx=4, stroke=OL, sw=2, extra='opacity="0.35"')
    b += rect(1010, 670, 80, 100, "#f6f1fc", rx=4, stroke=OL, sw=2)  # расписание
    for i in range(6):
        b += f'<rect x="1020" y="{684+i*13}" width="{60 - (i%3)*12}" height="5" fill="#8f7ac0"/>'
    b += rect(900, 800, 200, 14, "#6a5ba0", rx=4, stroke=OL, sw=2)  # скамейка
    # телефонная будка
    b += rect(1240, 560, 120, 300, "#7c3d74", rx=10, stroke=OL)
    b += rect(1256, 600, 88, 200, "#ffe6b8", rx=4, stroke=OL, sw=2, extra='opacity="0.6"')
    b += rect(1252, 572, 96, 22, "#f6d58a", rx=4, stroke=OL, sw=2)
    b += '<path d="M1290 690 h20 v40 h-20 Z" fill="#3a2a55"/>'
    # доска объявлений / афиши
    b += rect(1420, 520, 220, 280, "#5c4a3e", rx=6)
    for px, py, col in [(1436, 540, "#f2a9c4"), (1534, 548, "#9ec5f2"), (1440, 660, "#f6d58a"), (1540, 670, "#e7e0f7")]:
        b += rect(px, py, 86, 104, col, rx=3, stroke=OL, sw=2)
        b += f'<circle cx="{px+43}" cy="{py+40}" r="18" fill="#ffffff" opacity="0.8"/>'  # «пустые лица»
        b += f'<rect x="{px+14}" y="{py+70}" width="58" height="6" fill="#3a2a55" opacity="0.5"/>'
    b += '<path d="M1530 520 V900" stroke="#3a2a55" stroke-width="10"/>'
    # дом справа
    b += rect(1680, 260, 300, 560, "url(#bld1)", stroke=OL)
    for wy in range(300, 760, 100):
        b += rect(1720, wy, 70, 70, "#2a1f4e", rx=3, stroke=OL, sw=2) + rect(1830, wy, 70, 70, "#2a1f4e", rx=3, stroke=OL, sw=2)
        b += f'<rect x="1724" y="{wy+4}" width="62" height="62" fill="#ffd98a" opacity="{rnd.choice([0, 0.6])}"/>'
    # тротуар и дорога
    b += f'<path d="M0 820 H{W} V870 H0 Z" fill="url(#walk)" stroke="{OL}" stroke-width="3"/>'
    b += f'<rect y="870" width="{W}" height="210" fill="url(#road)"/>'
    for x in range(40, W, 260):
        b += f'<rect x="{x}" y="970" width="140" height="10" rx="5" fill="#f6f1fc" opacity="0.35"/>'
    # отражения неона на мокром асфальте
    for x, col in [(250, "#ffb3d1"), (1300, "#f6d58a"), (960, "#b9a6ff"), (1530, "#9ec5f2")]:
        for k in range(6):
            b += f'<rect x="{x - 60 + k*6}" y="{884 + k*28}" width="{120 - k*12}" height="5" rx="2.5" fill="{col}" opacity="{0.35 - k*0.05}"/>'
    save("city/street.svg", b, defs)
    # передний план: фонарь, лужа, кот
    defs = rg("lamp", [(0, "#ffe3a8", 0.7), (1, "#ffe3a8", 0)])
    f = '<ellipse cx="1760" cy="160" rx="200" ry="200" fill="url(#lamp)"/>'
    f += f'<path d="M1780 1080 V180 C1780 140 1760 130 1730 130" fill="none" stroke="#1d1734" stroke-width="16"/>'
    f += f'<path d="M1700 120 L1760 120 L1750 160 L1710 160 Z" fill="#ffe3a8" stroke="#1d1734" stroke-width="5"/>'
    f += '<ellipse cx="620" cy="1020" rx="170" ry="26" fill="#6c5fb0" opacity="0.45"/><ellipse cx="620" cy="1020" rx="120" ry="14" fill="#c9b4ff" opacity="0.25"/>'
    # кот у кафе
    f += ('<g transform="translate(330 860)"><path d="M0 0 C-4 -30 10 -50 30 -52 L36 -70 L46 -54 L58 -70 L60 -50 C76 -44 80 -20 76 0 Z" fill="#1b1530"/>'
          '<circle cx="40" cy="-38" r="4" fill="#ffe28a"/><circle cx="56" cy="-38" r="4" fill="#ffe28a"/>'
          '<path d="M76 -4 C100 -10 104 -40 90 -50" fill="none" stroke="#1b1530" stroke-width="8" stroke-linecap="round"/></g>')
    save("city/fore.svg", f, defs)
    # ДОП: рассветное небо для концовки
    defs = lg("dawn", [(0, "#7fa8e8"), (0.5, "#f5b3c8"), (1, "#ffe0a8")]) + rg("dsun", [(0, "#fff7dc", 1), (0.25, "#ffe7b0", 0.8), (1, "#ffe7b0", 0)])
    b = f'<rect width="{W}" height="{H}" fill="url(#dawn)"/><circle cx="960" cy="720" r="420" fill="url(#dsun)"/><circle cx="960" cy="720" r="80" fill="#fff8e6"/>'
    for cx, cy, s in [(300, 200, 1.4), (1500, 260, 1.2), (900, 140, 1.0)]:
        b += (f'<g opacity="0.8" transform="translate({cx} {cy}) scale({s})"><ellipse rx="120" ry="18" fill="#fff0f4"/>'
              f'<ellipse cx="-40" cy="-10" rx="60" ry="20" fill="#fff0f4"/><ellipse cx="40" cy="-14" rx="70" ry="22" fill="#fff0f4"/></g>')
    save("city/sky_dawn.svg", b, defs)


# =====================================================================
#                              КРЫША
# =====================================================================

def rooftop():
    defs = lg("rsky", [(0, "#070820"), (0.5, "#16164a"), (0.85, "#3a2a6e"), (1, "#6b3f86")]) + \
        lg("milky", [(0, "#b9a6ff", 0), (0.5, "#d8c9ff", 0.22), (1, "#b9a6ff", 0)], x2=1, y2=0)
    b = f'<rect width="{W}" height="{H}" fill="url(#rsky)"/>'
    b += '<path d="M-100 700 C400 400 1200 300 2100 -50 L2100 120 C1300 450 500 560 -100 860 Z" fill="url(#milky)"/>'
    save("rooftop/sky.svg", b, defs)
    # город внизу
    rnd = random.Random(8)
    b = ""
    x = -20
    while x < W + 20:
        w = rnd.randint(40, 110)
        h = rnd.randint(60, 220)
        b += f'<rect x="{x}" y="{760-h}" width="{w}" height="{h+60}" fill="#1a1440"/>'
        for wy in range(760 - h + 10, 760, 18):
            for wx in range(x + 6, x + w - 8, 14):
                if rnd.random() < 0.3:
                    b += f'<rect x="{wx}" y="{wy}" width="6" height="8" fill="{rnd.choice(["#ffd98a", "#ffc0e0", "#b9a6ff"])}" opacity="0.75"/>'
        x += w + rnd.randint(0, 12)
    save("rooftop/city.svg", b)
    defs = (lg("floor", [(0, "#4a3b6e"), (1, "#2a2046")]) + lg("tank", [(0, "#8a7ab8"), (1, "#5a4a88")], x2=1, y2=0) +
            rg("bulb", [(0, "#ffe7b0", 0.9), (1, "#ffe7b0", 0)]))
    b = f'<path d="M0 760 H{W} V{H} H0 Z" fill="url(#floor)"/>'
    for x in range(0, W, 120):
        b += f'<path d="M{x} 760 L{x - (x-960)*0.4} {H}" stroke="#221a3c" stroke-width="2" opacity="0.6"/>'
    # ограждение
    b += f'<rect x="0" y="700" width="{W}" height="10" fill="#8f7ac0" stroke="{OL}" stroke-width="2"/>'
    for x in range(20, W, 60):
        b += f'<rect x="{x}" y="706" width="6" height="58" fill="#6a5ba0"/>'
    b += f'<rect x="0" y="740" width="{W}" height="6" fill="#6a5ba0"/>'
    # водонапорный бак слева
    b += rect(80, 260, 260, 360, "url(#tank)", rx=20)
    b += '<path d="M60 270 C120 200 300 200 360 270 Z" fill="#6a5ba0" stroke="#3a2a55" stroke-width="3"/>'
    b += '<path d="M120 620 L100 780 M300 620 L320 780 M210 620 V780" stroke="#3a2a55" stroke-width="12"/>'
    for y in (330, 420, 510):
        b += f'<path d="M80 {y} H340" stroke="#4a3b78" stroke-width="4"/>'
    # рисунки мелом на баке
    b += '<path d="M150 450 l20 -30 l20 30 Z M220 400 a20 20 0 1 0 1 0" fill="none" stroke="#f6f1fc" stroke-width="3" opacity="0.7"/>'
    b += star4(270, 470, 16, "#f6f1fc", 0.7)
    # антенна
    b += '<path d="M1600 760 L1640 240 L1680 760" fill="none" stroke="#3a2f66" stroke-width="8"/>'
    b += '<path d="M1612 600 H1668 M1620 480 H1660 M1628 360 H1652" stroke="#3a2f66" stroke-width="6"/>'
    b += '<circle cx="1640" cy="234" r="10" fill="#ff7aa8"/>'
    # скамейка
    b += rect(760, 820, 380, 22, "#a58bd0", rx=6) + rect(760, 770, 380, 18, "#a58bd0", rx=6)
    b += '<path d="M790 842 V900 M1110 842 V900 M780 788 V820 M1120 788 V820" stroke="#3a2a55" stroke-width="10"/>'
    # телескоп
    b += f'<path d="M1300 900 L1350 760 L1400 900 M1350 760 V910" stroke="{OL}" stroke-width="7" fill="none"/>'
    b += f'<g transform="rotate(-32 1350 750)">{rect(1270, 730, 200, 40, "#f6f1fc", rx=12)}{rect(1450, 724, 34, 52, "#c9b4ff", rx=6)}</g>'
    # дверь на лестницу
    b += rect(470, 420, 200, 360, "#3a2f66", rx=6) + rect(500, 470, 140, 300, "#2a2050", rx=4, stroke=OL, sw=2)
    b += '<circle cx="620" cy="620" r="7" fill="#f6d58a"/>' + rect(460, 400, 220, 30, "#5a4a88", rx=4)
    # гирлянда
    pts = []
    for i in range(0, 41):
        x = 360 + i * 30
        y = 330 + 60 * math.sin(i / 40 * math.pi)
        pts.append((x, y))
    b += '<path d="M' + " L".join(f"{x} {y}" for x, y in pts) + '" fill="none" stroke="#2a2046" stroke-width="3"/>'
    for i, (x, y) in enumerate(pts[::2]):
        col = ["#ffe7b0", "#ffc0e0", "#c9b4ff"][i % 3]
        b += f'<circle cx="{x}" cy="{y+8}" r="22" fill="url(#bulb)"/><circle cx="{x}" cy="{y+8}" r="6" fill="{col}"/>'
    # цветы в ящике
    b += rect(1460, 700, 120, 60, "#7c5a4a", rx=4)
    for i in range(6):
        b += f'<circle cx="{1474+i*20}" cy="{690 - (i%2)*10}" r="9" fill="{["#f2a9c4", "#f6d58a", "#c9b4ff"][i%3]}" stroke="#3a2a55" stroke-width="2"/>'
    # тетрадь на скамейке
    b += f'<g transform="rotate(-8 980 812)">{rect(960, 800, 60, 16, "#f2a9c4", rx=2, stroke=OL, sw=2)}</g>'
    save("rooftop/main.svg", b, defs)


# =====================================================================
#                              БИБЛИОТЕКА
# =====================================================================

def library():
    defs = (lg("lwall", [(0, "#1d1640"), (1, "#2e2258")]) + lg("arch", [(0, "#3a5ea8"), (1, "#1a2a5e")]) +
            lg("ray", [(0, "#cfe0ff", 0.35), (1, "#cfe0ff", 0)]) + lg("lfloor", [(0, "#3b2b55"), (1, "#1f1633")]))
    b = f'<rect width="{W}" height="{H}" fill="url(#lwall)"/>'
    # высокие арочные окна
    for x in (560, 1160):
        b += (f'<path d="M{x} 600 V220 C{x} 120 {x+200} 120 {x+200} 220 V600 Z" fill="url(#arch)" stroke="#6c5aa8" stroke-width="8"/>'
              f'<path d="M{x+100} 140 V600 M{x} 360 H{x+200}" stroke="#6c5aa8" stroke-width="6"/>')
        b += f'<path d="M{x} 600 L{x+200} 600 L{x+420} 1080 L{x-160} 1080 Z" fill="url(#ray)"/>'
        rnd = random.Random(x)
        for i in range(14):
            b += f'<circle cx="{x + rnd.uniform(10, 190)}" cy="{rnd.uniform(160, 590)}" r="{rnd.choice([1, 1.5, 2])}" fill="#fff" opacity="0.8"/>'
    # дальние полки
    for x in range(0, W, 240):
        if 520 < x < 1400:
            continue
        b += rect(x + 10, 120, 220, 640, "#2a1f4a", stroke="#1a1230", sw=3)
        rnd = random.Random(x)
        for sy in range(140, 740, 80):
            bx = x + 18
            b += f'<rect x="{x+12}" y="{sy+66}" width="216" height="6" fill="#4a3870"/>'
            while bx < x + 220:
                bw = rnd.randint(10, 18)
                bh = rnd.randint(48, 64)
                b += f'<rect x="{bx}" y="{sy+66-bh}" width="{bw}" height="{bh}" fill="{rnd.choice(["#5a4a8a", "#6a3f6e", "#3f5a8a", "#7a6a4a", "#4a7a6a"])}"/>'
                bx += bw + 1
    b += f'<path d="M0 760 H{W} V{H} H0 Z" fill="url(#lfloor)"/>'
    for x in range(0, W, 160):
        b += f'<path d="M{x} 760 L{x - (x-960)*0.5} {H}" stroke="#150f24" stroke-width="2" opacity="0.6"/>'
    save("library/back.svg", b, defs)
    defs = (lg("wood", [(0, "#6a4a5a"), (1, "#3f2a3a")]) + rg("candle", [(0, "#ffd98a", 0.85), (1, "#ffd98a", 0)]) +
            lg("adoor", [(0, "#4a3a7a"), (1, "#241a48")]) + rg("rune", [(0, "#b9a6ff", 0.8), (1, "#b9a6ff", 0)]))
    b = ""
    # левый большой стеллаж с лестницей
    b += rect(30, 140, 360, 660, "url(#wood)", rx=6)
    rnd = random.Random(1)
    for sy in range(170, 780, 100):
        b += rect(44, sy, 332, 84, "#2a1a2a", rx=2, stroke=OL, sw=2)
        bx = 50
        while bx < 368:
            bw = rnd.randint(12, 22)
            bh = rnd.randint(56, 80)
            col = rnd.choice(["#8a5a7a", "#5a6aa8", "#a88a5a", "#6aa88a", "#a85a6a", "#7a6ab8"])
            b += f'<rect x="{bx}" y="{sy+84-bh}" width="{bw}" height="{bh}" fill="{col}" stroke="{OL}" stroke-width="1.2"/>'
            b += f'<rect x="{bx+2}" y="{sy+84-bh+8}" width="{bw-4}" height="3" fill="#f6d58a" opacity="0.6"/>'
            bx += bw + 1
    b += '<path d="M330 120 L420 800 M380 120 L470 800" stroke="#a88a6a" stroke-width="8"/>'
    for i in range(10):
        y = 160 + i * 64
        b += f'<path d="M{335 + i*12} {y} L{385 + i*12} {y}" stroke="#a88a6a" stroke-width="6"/>'
    # картотека (каталог)
    b += rect(520, 600, 220, 200, "url(#wood)", rx=6)
    for r_ in range(3):
        for c_ in range(3):
            b += rect(534 + c_ * 68, 614 + r_ * 60, 58, 48, "#8a6a5a", rx=3, stroke=OL, sw=2)
            b += f'<rect x="{552 + c_*68}" y="{632 + r_*60}" width="22" height="8" fill="#f6e7b4"/>'
    # стол для чтения со свечой и раскрытой книгой
    b += rect(860, 700, 380, 26, "#7a5a4a", rx=6) + '<path d="M890 726 V860 M1210 726 V860" stroke="#3f2a3a" stroke-width="14"/>'
    b += '<path d="M940 700 C980 680 1040 680 1050 696 C1060 680 1120 680 1160 700 L1160 706 L940 706 Z" fill="#f6efe0" stroke="#3a2a55" stroke-width="3"/>'
    b += '<path d="M960 692 h70 M960 686 h60 M1070 692 h70 M1070 686 h55" stroke="#8a7a6a" stroke-width="2"/>'
    b += '<ellipse cx="1180" cy="620" rx="90" ry="90" fill="url(#candle)"/>'
    b += rect(1170, 640, 20, 60, "#f6efe0", rx=3, stroke=OL, sw=2) + '<path d="M1180 640 C1172 626 1180 612 1180 606 C1186 614 1190 626 1180 640 Z" fill="#ffd27a"/>'
    # глобус
    b += '<circle cx="1300" cy="640" r="44" fill="#4a7aa8" stroke="#3a2a55" stroke-width="4"/><path d="M1270 620 C1290 610 1300 640 1320 630 M1280 660 C1300 650 1310 670 1330 660" stroke="#8ad0a8" stroke-width="6" fill="none"/>'
    b += '<path d="M1300 684 V780 M1270 780 H1330" stroke="#a88a6a" stroke-width="8"/>'
    # дверь архива с кодовым замком
    b += rect(1440, 220, 300, 580, "url(#adoor)", rx=10)
    b += '<path d="M1440 360 C1440 240 1740 240 1740 360" fill="none" stroke="#b9a6ff" stroke-width="5"/>'
    b += rect(1462, 380, 256, 400, "#2a1f50", rx=6, stroke="#6c5aa8", sw=4)
    b += '<ellipse cx="1590" cy="520" rx="120" ry="120" fill="url(#rune)"/>'
    b += '<circle cx="1590" cy="520" r="70" fill="#3a2f66" stroke="#d9c2ff" stroke-width="5"/>'
    for i in range(4):
        a = i * math.pi / 2
        b += star4(1590 + math.cos(a) * 46, 520 + math.sin(a) * 46, 12, "#d9c2ff", 0.9)
    b += star4(1590, 520, 20, "#fff6c9")
    # стопки книг на полу
    for bx, by, n in [(80, 900, 5), (1780, 880, 6), (700, 880, 3)]:
        for i in range(n):
            col = ["#8a5a7a", "#5a6aa8", "#a88a5a", "#6aa88a"][i % 4]
            b += f'<rect x="{bx + (i%2)*6}" y="{by - i*18}" width="110" height="16" rx="3" fill="{col}" stroke="{OL}" stroke-width="2"/>'
    save("library/main.svg", b, defs)
    # летающая книга (отдельный спрайт)
    book = ('<path d="M10 40 C30 20 60 18 80 30 C100 18 130 20 150 40 L150 50 C130 34 100 34 80 46 C60 34 30 34 10 50 Z" fill="#f6efe0" stroke="#3a2a55" stroke-width="3"/>'
            '<path d="M8 46 C30 30 60 30 80 44 C100 30 130 30 152 46 L152 56 C130 42 100 42 80 54 C60 42 30 42 8 56 Z" fill="#7a6ab8" stroke="#3a2a55" stroke-width="3"/>'
            '<path d="M24 36 h40 M96 36 h40" stroke="#8a7a6a" stroke-width="2"/>')
    save("library/book.svg", book, w=160, h=70)


# =====================================================================
#                              ПОЕЗД
# =====================================================================

def train():
    defs = lg("space", [(0, "#060418"), (0.5, "#1a0f3c"), (1, "#3a1a5c")]) + \
        rg("neb1", [(0, "#c46cd8", 0.45), (1, "#c46cd8", 0)]) + rg("neb2", [(0, "#6c8cff", 0.4), (1, "#6c8cff", 0)])
    b = f'<rect width="{W*2}" height="{H}" fill="url(#space)"/>'
    rnd = random.Random(12)
    for i in range(8):
        cx, cy = rnd.uniform(0, W * 2), rnd.uniform(100, 700)
        b += f'<ellipse cx="{cx}" cy="{cy}" rx="{rnd.uniform(200, 420)}" ry="{rnd.uniform(90, 200)}" fill="url(#neb{1 + i%2})"/>'
    for i in range(260):
        b += f'<circle cx="{rnd.uniform(0, W*2)}" cy="{rnd.uniform(0, H)}" r="{rnd.choice([0.8, 1.2, 1.6, 2.4])}" fill="#fff" opacity="{rnd.uniform(0.3, 1)}"/>'
    # планета
    b += '<circle cx="1400" cy="300" r="90" fill="#7a5ac8"/><path d="M1290 330 C1350 270 1470 260 1520 280" stroke="#d9c2ff" stroke-width="10" fill="none" opacity="0.7"/>'
    save("train/space.svg", b, defs, w=W * 2, h=H)
    defs = (lg("wall", [(0, "#4a2f5e"), (1, "#2a1a3c")]) + lg("seat", [(0, "#9a4a6a"), (1, "#6a2a4a")]) +
            rg("lampg", [(0, "#ffe3a8", 0.75), (1, "#ffe3a8", 0)]) + lg("floor", [(0, "#3a2440"), (1, "#1a1020")]) +
            lg("wood", [(0, "#8a5a4a"), (1, "#5a3a2a")]))
    # стена с прорезанными окнами (evenodd)
    holes = ""
    wins = [(150, 180, 360, 320), (620, 180, 360, 320), (1090, 180, 360, 320), (1560, 180, 300, 320)]
    for x, y, w, h in wins:
        holes += f" M{x+20} {y} H{x+w-20} Q{x+w} {y} {x+w} {y+20} V{y+h-20} Q{x+w} {y+h} {x+w-20} {y+h} H{x+20} Q{x} {y+h} {x} {y+h-20} V{y+20} Q{x} {y} {x+20} {y} Z"
    b = f'<path fill-rule="evenodd" d="M0 0 H{W} V760 H0 Z{holes}" fill="url(#wall)"/>'
    for x, y, w, h in wins:
        b += f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="20" fill="none" stroke="#c9a46a" stroke-width="10"/>'
        b += f'<path d="M{x+w/2} {y} V{y+h}" stroke="#c9a46a" stroke-width="6"/>'
    b += f'<rect y="0" width="{W}" height="60" fill="#2a1a3c"/><rect y="60" width="{W}" height="8" fill="#c9a46a"/>'
    # багажные полки
    b += f'<rect x="100" y="110" width="{W-200}" height="14" fill="#c9a46a" stroke="{OL}" stroke-width="2"/>'
    for x, col in [(200, "#7a5ac8"), (420, "#c87a5a"), (800, "#5a8ac8"), (1240, "#c8a85a"), (1680, "#8ac87a")]:
        b += rect(x, 70, 120, 42, col, rx=6) + f'<rect x="{x+50}" y="64" width="20" height="8" fill="#3a2a55"/>'
    # светильники
    for x in (380, 850, 1320, 1760):
        b += f'<ellipse cx="{x}" cy="80" rx="140" ry="110" fill="url(#lampg)"/><path d="M{x-24} 70 h48 l-8 22 h-32 Z" fill="#ffe3a8" stroke="{OL}" stroke-width="2"/>'
    b += f'<path d="M0 760 H{W} V{H} H0 Z" fill="url(#floor)"/>'
    b += f'<rect y="756" width="{W}" height="10" fill="#c9a46a"/>'
    b += '<path d="M0 900 H1920" stroke="#6a2a4a" stroke-width="80" opacity="0.6"/>'
    # сиденья (спинки)
    for x in (120, 590, 1060, 1530):
        b += rect(x, 520, 380, 260, "url(#seat)", rx=30)
        b += f'<path d="M{x+20} 560 H{x+360}" stroke="#c87a9a" stroke-width="4" opacity="0.6"/>'
        b += rect(x + 10, 740, 360, 60, "#7a3a5a", rx=16)
    # столик
    b += rect(900, 660, 160, 20, "url(#wood)", rx=6) + '<path d="M980 680 V780" stroke="#5a3a2a" stroke-width="10"/>'
    b += '<path d="M940 660 C940 640 960 636 964 660 Z" fill="#f6efe0" stroke="#3a2a55" stroke-width="2"/>'  # чашка
    b += '<path d="M1000 662 L1040 662 L1036 650 L1004 650 Z" fill="#f6d58a" stroke="#3a2a55" stroke-width="2"/>'  # билет
    # дверь в следующий вагон (справа)
    b += rect(1860, 200, 60, 560, "#3a2440", stroke=OL)
    save("train/interior.svg", b, defs)
    # Проводник (NPC)
    defs = (lg("coat", [(0, "#2a2a5e"), (1, "#14143a")]) + rg("lant", [(0, "#ffe3a8", 0.9), (1, "#ffe3a8", 0)]))
    c = ('<ellipse cx="300" cy="1180" rx="160" ry="24" fill="#000" opacity="0.3"/>'
         '<path d="M200 380 C190 600 160 900 150 1170 L450 1170 C440 900 410 600 400 380 C380 340 220 340 200 380 Z" fill="url(#coat)" stroke="#0a0a20" stroke-width="4"/>'
         '<path d="M300 380 V1160" stroke="#c9a46a" stroke-width="4"/>'
         + "".join(f'<circle cx="{x}" cy="{y}" r="7" fill="#c9a46a"/>' for x, y in [(280, 480), (280, 580), (280, 680), (320, 480), (320, 580), (320, 680)]) +
         '<path d="M230 260 C220 170 380 170 370 260 C380 330 340 370 300 372 C260 370 220 330 230 260 Z" fill="#1a1a3c" stroke="#0a0a20" stroke-width="4"/>'
         '<ellipse cx="275" cy="270" rx="12" ry="7" fill="#ffe3a8"/><ellipse cx="325" cy="270" rx="12" ry="7" fill="#ffe3a8"/>'
         '<path d="M190 200 L410 200 L390 170 C380 120 220 120 210 170 Z" fill="#2a2a5e" stroke="#0a0a20" stroke-width="4"/>'
         '<rect x="186" y="196" width="228" height="16" rx="6" fill="#14143a"/><rect x="284" y="150" width="32" height="26" rx="4" fill="#c9a46a"/>'
         '<path d="M400 420 C460 520 470 640 440 700" fill="none" stroke="#14143a" stroke-width="40" stroke-linecap="round"/>'
         '<ellipse cx="450" cy="780" rx="110" ry="110" fill="url(#lant)"/>'
         '<path d="M440 700 V730" stroke="#c9a46a" stroke-width="5"/><rect x="420" y="730" width="60" height="90" rx="10" fill="#ffe8b0" stroke="#c9a46a" stroke-width="6"/>'
         '<path d="M450 750 C440 770 450 790 450 800 C460 790 462 770 450 750 Z" fill="#ffb84a"/>')
    save("train/conductor.svg", c, defs, w=600, h=1220)
    # силуэты пассажиров — забытых персонажей
    pas = {
        "kite_girl": ('<path d="M60 380 C50 300 60 200 100 180 C140 200 150 300 140 380 Z" fill="#3a2f6a"/>'
                      '<circle cx="100" cy="150" r="40" fill="#3a2f6a"/><path d="M70 130 C60 100 140 100 130 130" fill="#2a1f5a"/>'
                      '<path d="M130 220 L210 60" stroke="#c9b4ff" stroke-width="2"/><path d="M210 20 L250 60 L210 110 L170 60 Z" fill="#f2a9c4" stroke="#3a2a55" stroke-width="3"/>'
                      '<circle cx="88" cy="150" r="4" fill="#ffe3a8"/><circle cx="112" cy="150" r="4" fill="#ffe3a8"/>'),
        "knight": ('<path d="M40 400 L50 220 C60 180 160 180 170 220 L180 400 Z" fill="#4a4a6e"/>'
                   '<path d="M60 160 C60 100 160 100 160 160 L160 200 L60 200 Z" fill="#6a6a8e"/><rect x="70" y="150" width="80" height="10" fill="#1a1a2e"/>'
                   '<path d="M110 100 C120 70 140 60 150 70 C130 80 120 90 116 104 Z" fill="#c84a6a"/>'
                   '<path d="M190 120 L200 400" stroke="#9a9aba" stroke-width="10"/><rect x="176" y="250" width="40" height="10" fill="#c9a46a"/>'),
        "robot": ('<rect x="50" y="200" width="140" height="180" rx="20" fill="#5a6a8e"/>'
                  '<rect x="70" y="100" width="100" height="90" rx="16" fill="#6a7a9e"/><circle cx="100" cy="145" r="12" fill="#9af0ff"/><circle cx="140" cy="145" r="12" fill="#9af0ff"/>'
                  '<path d="M120 100 V70" stroke="#6a7a9e" stroke-width="6"/><circle cx="120" cy="64" r="8" fill="#ff7aa8"/>'
                  '<rect x="85" y="240" width="70" height="46" rx="4" fill="#f6efe0"/><path d="M85 240 L120 266 L155 240" fill="none" stroke="#3a2a55" stroke-width="3"/>'),
    }
    for k, v in pas.items():
        save(f"train/{k}.svg", v, w=260, h=420)


# =====================================================================
#                        ПОСЛЕДНИЙ ВАГОН / ГРАНИЦА
# =====================================================================

def edge():
    defs = rg("wg", [(0, "#ffffff", 1), (0.6, "#e9e0ff", 1), (1, "#b9a6e6", 1)], r=0.8)
    b = f'<rect width="{W}" height="{H}" fill="url(#wg)"/>'
    rnd = random.Random(30)
    for i in range(30):
        x, y = rnd.uniform(0, W), rnd.uniform(0, H)
        b += f'<rect x="{x}" y="{y}" width="{rnd.uniform(40, 220)}" height="{rnd.choice([4, 6, 10])}" rx="3" fill="#9c7bff" opacity="{rnd.uniform(0.05, 0.2)}"/>'
    save("edge/white.svg", b, defs)


# =====================================================================
#                        ГЛАВНОЕ МЕНЮ
# =====================================================================

def menu():
    defs = lg("msky", [(0, "#090a26"), (0.45, "#221a52"), (0.8, "#5a3478"), (1, "#a8577e")]) + \
        rg("mmoon", [(0, "#fff3d6", 0.55), (1, "#fff3d6", 0)])
    b = f'<rect width="{W}" height="{H}" fill="url(#msky)"/>'
    b += '<circle cx="1420" cy="300" r="260" fill="url(#mmoon)"/><circle cx="1420" cy="300" r="110" fill="#fff3d6"/>'
    b += '<circle cx="1385" cy="270" r="18" fill="#efe2bf"/><circle cx="1450" cy="340" r="12" fill="#efe2bf"/><circle cx="1460" cy="250" r="8" fill="#efe2bf"/>'
    save("menu/sky.svg", b, defs)
    rnd = random.Random(2)
    for layer, (col, hmin, hmax, base, lit) in {"far": ("#2a1f55", 180, 420, 900, 0.18), "near": ("#150f30", 120, 320, 1000, 0.3)}.items():
        b = ""
        x = -20
        while x < W + 20:
            w = rnd.randint(50, 130)
            h = rnd.randint(hmin, hmax)
            b += f'<rect x="{x}" y="{base-h}" width="{w}" height="{h+200}" fill="{col}"/>'
            for wy in range(base - h + 12, base, 22):
                for wx in range(x + 8, x + w - 10, 16):
                    if rnd.random() < lit:
                        b += f'<rect x="{wx}" y="{wy}" width="7" height="10" fill="{rnd.choice(["#ffd98a", "#ffc0e0", "#b9a6ff"])}" opacity="0.8"/>'
            x += w + rnd.randint(0, 6)
        save(f"menu/{layer}.svg", b)


def main():
    for v in WALLS:
        room_wall(v)
    for p in SKIES:
        window_sky(p)
    room_furniture()
    room_extras()
    city()
    rooftop()
    library()
    train()
    edge()
    menu()
    print("ok")


if __name__ == "__main__":
    main()
