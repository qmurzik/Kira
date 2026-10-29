#!/usr/bin/env python3
"""Иконки интерфейса, предметы декора, иллюстрации воспоминаний (SVG)."""
import math
import os
import random

ROOT = os.path.join(os.path.dirname(__file__), "..")
OL = "#3a2a55"


def save(path, w, h, body, defs=""):
    full = os.path.join(ROOT, "assets", path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}"><defs>{defs}</defs>{body}</svg>')


def star4(x, y, r, fill, op=1.0):
    k = r * 0.3
    return (f'<path d="M{x} {y-r} L{x+k} {y-k} L{x+r} {y} L{x+k} {y+k} L{x} {y+r} L{x-k} {y+k} L{x-r} {y} L{x-k} {y-k} Z" fill="{fill}" opacity="{op}"/>')


def star5(cx, cy, r, fill, stroke="none", sw=0):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r if i % 2 == 0 else r * 0.45
        pts.append(f"{cx + math.cos(a)*rr:.1f},{cy + math.sin(a)*rr:.1f}")
    return f'<polygon points="{" ".join(pts)}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" stroke-linejoin="round"/>'


HEART = "M32 54 C10 40 4 28 8 18 C12 8 26 8 32 20 C38 8 52 8 56 18 C60 28 54 40 32 54 Z"
MOON = "M40 8 A24 24 0 1 0 56 44 A19 19 0 1 1 40 8 Z"


def icons():
    W = "#f1ebff"
    A = "#c9a6ff"
    ic = {
        "heart": f'<path d="{HEART}" fill="#ff9ecf" stroke="#fff" stroke-width="3"/>',
        "star": star5(32, 33, 26, "#ffe08a", "#fff6d0", 2),
        "sparkle": star4(28, 30, 22, "#fff3c0") + star4(50, 14, 9, "#ffd1ea") + star4(50, 50, 7, "#c9a6ff"),
        "shirt": f'<path d="M20 10 L8 20 L14 30 L20 26 L20 56 L44 56 L44 26 L50 30 L56 20 L44 10 C40 16 24 16 20 10 Z" fill="{A}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>',
        "map": f'<path d="M8 14 L24 8 L40 14 L56 8 L56 50 L40 56 L24 50 L8 56 Z" fill="{A}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/><path d="M24 8 V50 M40 14 V56" stroke="{W}" stroke-width="3"/><circle cx="46" cy="28" r="5" fill="#ff9ecf"/>',
        "book": f'<path d="M8 14 C18 8 28 10 32 16 C36 10 46 8 56 14 L56 52 C46 46 36 48 32 54 C28 48 18 46 8 52 Z" fill="{A}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/><path d="M32 16 V54" stroke="{W}" stroke-width="3"/>',
        "gear": "".join(f'<rect x="28" y="4" width="8" height="14" rx="3" fill="{A}" transform="rotate({a} 32 32)"/>' for a in range(0, 360, 45)) + f'<circle cx="32" cy="32" r="17" fill="{A}" stroke="{W}" stroke-width="3"/><circle cx="32" cy="32" r="6" fill="#2a2046"/>',
        "chat": f'<path d="M8 14 C8 10 12 8 16 8 L48 8 C52 8 56 10 56 14 L56 38 C56 42 52 44 48 44 L26 44 L14 56 L16 44 C12 44 8 42 8 38 Z" fill="{A}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/><circle cx="22" cy="26" r="4" fill="#2a2046"/><circle cx="32" cy="26" r="4" fill="#2a2046"/><circle cx="42" cy="26" r="4" fill="#2a2046"/>',
        "moon": f'<path d="{MOON}" fill="#ffe7a8" stroke="#fff6d0" stroke-width="3"/>',
        "cup": f'<path d="M12 22 H46 V40 C46 50 38 56 29 56 C20 56 12 50 12 40 Z" fill="{A}" stroke="{W}" stroke-width="3"/><path d="M46 26 C58 26 58 42 46 42" fill="none" stroke="{W}" stroke-width="3"/><path d="M22 16 C18 10 26 8 22 2 M32 16 C28 10 36 8 32 2" stroke="{W}" stroke-width="3" fill="none" stroke-linecap="round"/>',
        "puzzle": f'<path d="M10 14 H26 C24 6 36 6 34 14 H50 V30 C58 28 58 40 50 38 V54 H34 C36 46 24 46 26 54 H10 V38 C18 40 18 28 10 30 Z" fill="{A}" stroke="{W}" stroke-width="3" stroke-linejoin="round"/>',
        "music": f'<path d="M24 46 V12 L52 6 V40" fill="none" stroke="{W}" stroke-width="4"/><ellipse cx="18" cy="46" rx="8" ry="6" fill="{A}"/><ellipse cx="46" cy="40" rx="8" ry="6" fill="{A}"/>',
        "save": f'<rect x="8" y="8" width="48" height="48" rx="6" fill="{A}" stroke="{W}" stroke-width="3"/><rect x="18" y="8" width="26" height="16" fill="{W}"/><rect x="16" y="34" width="32" height="18" rx="3" fill="#2a2046"/>',
        "locked": f'<rect x="14" y="28" width="36" height="28" rx="6" fill="{A}"/><path d="M22 28 V20 C22 8 42 8 42 20 V28" fill="none" stroke="{A}" stroke-width="6"/><circle cx="32" cy="42" r="4" fill="#2a2046"/>',
        "key": f'<circle cx="20" cy="32" r="12" fill="none" stroke="#ffe08a" stroke-width="6"/><path d="M32 32 H58 M50 32 V42 M42 32 V40" stroke="#ffe08a" stroke-width="6" stroke-linecap="round"/>',
        "grabber": f'<circle cx="32" cy="32" r="14" fill="#ffffff" stroke="#c9a6ff" stroke-width="5"/>',
        "check_off": f'<rect x="8" y="16" width="48" height="32" rx="16" fill="#2a2046" stroke="#8a78c0" stroke-width="3"/><circle cx="24" cy="32" r="11" fill="#8a78c0"/>',
        "check_on": f'<rect x="8" y="16" width="48" height="32" rx="16" fill="#b49aff" stroke="#fff" stroke-width="3"/><circle cx="40" cy="32" r="11" fill="#fff"/>',
        "sym_moon": f'<path d="{MOON}" fill="#ffe7a8"/>',
        "sym_star": star5(32, 34, 26, "#fff3c0"),
        "sym_cat": '<path d="M12 56 C8 36 14 22 20 18 L18 4 L28 14 C30 13 34 13 36 14 L46 4 L44 18 C50 22 56 36 52 56 Z" fill="#c9a6ff"/><circle cx="25" cy="30" r="3" fill="#2a2046"/><circle cx="39" cy="30" r="3" fill="#2a2046"/>',
        "sym_key": '<circle cx="20" cy="32" r="12" fill="none" stroke="#ffd38a" stroke-width="7"/><path d="M32 32 H58 M50 32 V44 M42 32 V42" stroke="#ffd38a" stroke-width="7" stroke-linecap="round"/>',
        "sym_heart": f'<path d="{HEART}" fill="#ff9ecf"/>',
        "sym_note": '<path d="M24 46 V12 L52 6 V40" fill="none" stroke="#9fe0ff" stroke-width="5"/><ellipse cx="18" cy="46" rx="9" ry="7" fill="#9fe0ff"/><ellipse cx="46" cy="40" rx="9" ry="7" fill="#9fe0ff"/>',
    }
    for k, v in ic.items():
        save(f"ui/{k}.svg", 64, 64, v)
    save("ui/heart_small.svg", 32, 32, f'<g transform="scale(0.5)"><path d="{HEART}" fill="#ff9ecf"/></g>')
    # ингредиенты
    ing = {
        "flour": '<path d="M16 20 L48 20 L52 56 L12 56 Z" fill="#f6efe0" stroke="#3a2a55" stroke-width="3"/><path d="M20 20 C20 10 44 10 44 20" fill="#fff" stroke="#3a2a55" stroke-width="3"/><path d="M22 38 h20" stroke="#c9a46a" stroke-width="3"/>',
        "milk": '<path d="M20 14 L44 14 L44 22 L50 30 L50 58 L14 58 L14 30 L20 22 Z" fill="#fff" stroke="#3a2a55" stroke-width="3"/><rect x="14" y="36" width="36" height="12" fill="#9fc8ff"/>',
        "egg": '<ellipse cx="32" cy="36" rx="18" ry="22" fill="#fff4e0" stroke="#3a2a55" stroke-width="3"/>',
        "salt_bag": '<path d="M18 16 L46 16 L50 58 L14 58 Z" fill="#e8f0ff" stroke="#3a2a55" stroke-width="3"/><circle cx="26" cy="40" r="2" fill="#3a2a55"/><circle cx="34" cy="36" r="2" fill="#3a2a55"/><circle cx="38" cy="44" r="2" fill="#3a2a55"/>',
        "pepper": '<path d="M30 16 C46 16 50 40 34 56 C26 60 24 50 28 40 C30 30 22 20 30 16 Z" fill="#e24a4a" stroke="#3a2a55" stroke-width="3"/><path d="M30 16 C30 10 36 8 38 6" stroke="#3a8a4a" stroke-width="4" fill="none"/>',
        "pickle": '<ellipse cx="32" cy="34" rx="12" ry="24" fill="#6aaa4a" stroke="#3a2a55" stroke-width="3" transform="rotate(25 32 34)"/>',
        "berries": "".join(f'<circle cx="{x}" cy="{y}" r="9" fill="#5a5ac8" stroke="#3a2a55" stroke-width="2"/>' for x, y in [(24, 38), (40, 38), (32, 26), (32, 48)]),
        "ketchup": '<path d="M24 20 L40 20 L46 58 L18 58 Z" fill="#e23a3a" stroke="#3a2a55" stroke-width="3"/><rect x="28" y="8" width="8" height="12" fill="#fff" stroke="#3a2a55" stroke-width="2"/>',
        "fish": '<path d="M10 32 C20 16 40 16 48 32 C40 48 20 48 10 32 Z M48 32 L58 22 L58 42 Z" fill="#8ab8e8" stroke="#3a2a55" stroke-width="3"/><circle cx="20" cy="30" r="3" fill="#3a2a55"/>',
        "honey_jar": '<rect x="16" y="22" width="32" height="34" rx="8" fill="#f2b84a" stroke="#3a2a55" stroke-width="3"/><rect x="14" y="14" width="36" height="10" rx="3" fill="#c97a4a" stroke="#3a2a55" stroke-width="2"/>',
        "cocoa_powder": '<rect x="16" y="16" width="32" height="42" rx="4" fill="#7a4a3a" stroke="#3a2a55" stroke-width="3"/><circle cx="32" cy="38" r="8" fill="#f2d39c"/>',
        "sugar": "".join(f'<rect x="{x}" y="{y}" width="14" height="14" rx="2" fill="#fff" stroke="#3a2a55" stroke-width="2"/>' for x, y in [(16, 36), (32, 36), (24, 22)]),
        "marshmallow": "".join(f'<rect x="{x}" y="{y}" width="18" height="16" rx="6" fill="{c}" stroke="#3a2a55" stroke-width="2"/>' for x, y, c in [(12, 34, "#ffd1ea"), (32, 34, "#fff"), (22, 18, "#ffe8f4")]),
        "rice": '<path d="M10 30 H54 C54 48 44 56 32 56 C20 56 10 48 10 30 Z" fill="#c9a6ff" stroke="#3a2a55" stroke-width="3"/><path d="M12 30 C14 18 50 18 52 30 Z" fill="#fff" stroke="#3a2a55" stroke-width="2"/>',
        "nori": '<rect x="14" y="12" width="36" height="42" rx="3" fill="#2a4a3a" stroke="#3a2a55" stroke-width="3"/><path d="M20 20 h24 M20 30 h24" stroke="#3a6a4a" stroke-width="2"/>',
    }
    for k, v in ing.items():
        save(f"ui/ing_{k}.svg", 64, 64, v)


def decor():
    d = {}
    # гирлянда на всю ширину потолка
    g = '<path d="M0 10 '
    pts = []
    for i in range(0, 61):
        x = i * 1400 / 60
        y = 10 + 34 * math.sin(i / 60 * math.pi)
        pts.append((x, y))
    g = '<path d="M' + " L".join(f"{x:.0f} {y:.0f}" for x, y in pts) + '" fill="none" stroke="#2a2046" stroke-width="3"/>'
    for i in range(13):
        x = 40 + i * 110
        y = 10 + 34 * math.sin((x / 1400) * math.pi)
        col = ["#ffe7b0", "#ffc0e0", "#c9b4ff"][i % 3]
        g += star4(x, y + 18, 13, col) + f'<circle cx="{x}" cy="{y+18}" r="4" fill="#fff"/>'
    save("decor/garland.svg", 1400, 70, g)
    save("decor/cat_plush.svg", 120, 110,
         '<ellipse cx="60" cy="78" rx="44" ry="28" fill="#f2c9a8" stroke="#3a2a55" stroke-width="3"/>'
         '<circle cx="60" cy="48" r="32" fill="#f2c9a8" stroke="#3a2a55" stroke-width="3"/>'
         '<path d="M34 30 L30 6 L50 20 Z M86 30 L90 6 L70 20 Z" fill="#f2c9a8" stroke="#3a2a55" stroke-width="3" stroke-linejoin="round"/>'
         '<path d="M48 44 l6 6 M48 50 l6 -6 M66 44 l6 6 M66 50 l6 -6" stroke="#3a2a55" stroke-width="3" stroke-linecap="round"/>'
         '<path d="M54 60 Q60 66 66 60" fill="none" stroke="#3a2a55" stroke-width="2.5"/><ellipse cx="42" cy="58" rx="6" ry="3" fill="#ff9ecf" opacity="0.7"/><ellipse cx="78" cy="58" rx="6" ry="3" fill="#ff9ecf" opacity="0.7"/>')
    save("decor/moon_lamp.svg", 100, 120,
         f'<rect x="30" y="96" width="40" height="20" rx="6" fill="#d7c9ec" stroke="{OL}" stroke-width="3"/>'
         f'<circle cx="50" cy="52" r="42" fill="#fff6dc" stroke="{OL}" stroke-width="3"/><circle cx="36" cy="40" r="7" fill="#efe2bf"/><circle cx="62" cy="64" r="9" fill="#efe2bf"/><circle cx="64" cy="34" r="4" fill="#efe2bf"/>')
    save("decor/poster_whale.svg", 200, 140,
         f'<rect x="4" y="4" width="192" height="132" rx="6" fill="#bfd8ff" stroke="{OL}" stroke-width="4"/>'
         '<ellipse cx="60" cy="100" rx="40" ry="10" fill="#fff" opacity="0.8"/><ellipse cx="150" cy="40" rx="30" ry="8" fill="#fff" opacity="0.8"/>'
         '<path d="M40 76 C50 46 120 40 150 64 C160 56 172 50 180 54 C176 62 170 70 160 74 C140 96 70 100 40 76 Z" fill="#5a6ac8" stroke="#3a2a55" stroke-width="3"/>'
         '<circle cx="70" cy="70" r="4" fill="#fff"/><path d="M80 86 C100 92 120 90 140 82" fill="none" stroke="#c9d6ff" stroke-width="3"/>' + star4(30, 30, 9, "#fff6c9") + star4(176, 112, 7, "#fff6c9"))
    save("decor/cactus.svg", 80, 110,
         f'<path d="M14 74 H66 L60 106 H20 Z" fill="#e8a07a" stroke="{OL}" stroke-width="3"/>'
         f'<path d="M30 74 V30 C30 14 50 14 50 30 V74 Z" fill="#6aaa7a" stroke="{OL}" stroke-width="3"/>'
         f'<path d="M30 54 H20 V40 C20 34 26 34 26 40 V48 H30" fill="#6aaa7a" stroke="{OL}" stroke-width="3"/>'
         f'<circle cx="40" cy="18" r="6" fill="#ff9ecf" stroke="{OL}" stroke-width="2"/>')
    save("decor/dreamcatcher.svg", 110, 150,
         f'<circle cx="55" cy="45" r="38" fill="none" stroke="#c9a46a" stroke-width="5"/>'
         + "".join(f'<path d="M55 45 L{55 + 36*math.cos(a):.0f} {45 + 36*math.sin(a):.0f}" stroke="#e8dcff" stroke-width="1.5"/>' for a in [i * math.pi / 4 for i in range(8)]) +
         '<path d="M30 80 V130 M55 84 V146 M80 80 V130" stroke="#e8dcff" stroke-width="2"/>'
         '<ellipse cx="30" cy="132" rx="7" ry="14" fill="#c9a6ff"/><ellipse cx="55" cy="146" rx="7" ry="14" fill="#ff9ecf"/><ellipse cx="80" cy="132" rx="7" ry="14" fill="#9fd6ff"/>')
    save("decor/star_jar.svg", 90, 110,
         f'<rect x="12" y="24" width="66" height="82" rx="14" fill="#cfe6ff" fill-opacity="0.55" stroke="{OL}" stroke-width="3"/>'
         f'<rect x="18" y="10" width="54" height="16" rx="4" fill="#c9a46a" stroke="{OL}" stroke-width="3"/>' +
         "".join(star4(x, y, r, c) for x, y, r, c in [(34, 60, 10, "#fff3b0"), (56, 78, 8, "#ffd1ea"), (44, 92, 7, "#c9b4ff"), (58, 50, 6, "#fff3b0"), (30, 86, 5, "#fff")]))
    save("decor/crystal.svg", 90, 110,
         f'<path d="M45 4 L74 40 L62 104 L28 104 L16 40 Z" fill="#b9a0ff" stroke="{OL}" stroke-width="3" stroke-linejoin="round"/>'
         '<path d="M45 4 L45 104 M16 40 L74 40" stroke="#e8dcff" stroke-width="2" opacity="0.7"/><path d="M30 46 L40 46 L36 90 Z" fill="#fff" opacity="0.5"/>')
    save("decor/kite.svg", 140, 180,
         f'<path d="M70 6 L120 64 L70 130 L20 64 Z" fill="#ff9ecf" stroke="{OL}" stroke-width="3"/><path d="M70 6 V130 M20 64 H120" stroke="#fff" stroke-width="2"/>'
         '<path d="M70 130 C60 146 82 152 70 170" fill="none" stroke="#3a2a55" stroke-width="2"/>'
         '<path d="M62 146 l8 4 l-8 4 Z M66 164 l8 4 l-8 4 Z" fill="#9fd6ff"/>')
    save("decor/lantern.svg", 90, 140,
         f'<path d="M45 4 V20" stroke="#c9a46a" stroke-width="4"/><rect x="18" y="20" width="54" height="16" rx="4" fill="#c9a46a" stroke="{OL}" stroke-width="3"/>'
         f'<rect x="22" y="36" width="46" height="80" rx="8" fill="#ffe8b0" stroke="#c9a46a" stroke-width="5"/>'
         '<path d="M45 56 C36 74 45 90 45 98 C54 90 56 74 45 56 Z" fill="#ffb84a"/>'
         f'<rect x="16" y="114" width="58" height="18" rx="4" fill="#c9a46a" stroke="{OL}" stroke-width="3"/>')


def mini_kira(x, y, s=1.0, mood="happy", outfit="#c9b8f4"):
    """Маленькая стилизованная Кира для иллюстраций воспоминаний."""
    mouth = '<path d="M-6 6 Q0 12 6 6" fill="none" stroke="#a24c62" stroke-width="2.4"/>' if mood != "calm" else '<path d="M-5 8 h10" stroke="#a24c62" stroke-width="2.4"/>'
    return (f'<g transform="translate({x} {y}) scale({s})">'
            f'<path d="M-38 120 C-40 80 -30 50 0 46 C30 50 40 80 38 120 Z" fill="{outfit}" stroke="{OL}" stroke-width="3"/>'
            '<path d="M-44 -10 C-50 -60 -20 -78 0 -78 C20 -78 50 -60 44 -10 L46 30 L30 18 L-30 18 L-46 30 Z" fill="#211d33" stroke="#120f1d" stroke-width="3"/>'
            '<path d="M-32 -10 C-32 30 -16 42 0 42 C16 42 32 30 32 -10 C32 -40 -32 -40 -32 -10 Z" fill="#fdebe3" stroke="#c98a84" stroke-width="2"/>'
            '<path d="M-34 -18 C-20 -50 20 -50 34 -18 L26 -26 L18 -10 L10 -28 L0 -8 L-10 -28 L-18 -10 L-26 -26 Z" fill="#211d33"/>'
            '<ellipse cx="-13" cy="4" rx="7" ry="9" fill="#7446c9"/><ellipse cx="13" cy="4" rx="7" ry="9" fill="#7446c9"/>'
            '<circle cx="-15" cy="1" r="2.5" fill="#fff"/><circle cx="11" cy="1" r="2.5" fill="#fff"/>'
            f'<g transform="translate(0 16)">{mouth}</g>'
            '<rect x="16" y="-26" width="18" height="5" rx="2.5" fill="#b89cff" transform="rotate(-30 25 -24)"/>'
            '<ellipse cx="-20" cy="18" rx="7" ry="3" fill="#ff9ecf" opacity="0.6"/><ellipse cx="20" cy="18" rx="7" ry="3" fill="#ff9ecf" opacity="0.6"/>'
            '</g>')


def sky(id_, stops):
    s = "".join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{id_}" x1="0" y1="0" x2="0" y2="1">{s}</linearGradient>'


def stars_bg(n, w=800, h=300, seed=1):
    r = random.Random(seed)
    return "".join(f'<circle cx="{r.uniform(0, w):.0f}" cy="{r.uniform(0, h):.0f}" r="{r.choice([1, 1.5, 2])}" fill="#fff" opacity="{r.uniform(0.4, 1):.2f}"/>' for _ in range(n))


def memories():
    W, H = 800, 450
    m = {}
    # Первая встреча: комната вечером, Кира у окна, на экране монитора курсор
    m["first_meeting"] = (sky("s", [(0, "#4a3f8f"), (0.6, "#c46c9e"), (1, "#f6a37a")]),
        f'<rect width="{W}" height="{H}" fill="#c6b3e6"/><rect x="60" y="50" width="260" height="220" fill="url(#s)" stroke="#f6f1fc" stroke-width="12"/>'
        '<path d="M190 50 V270 M60 160 H320" stroke="#f6f1fc" stroke-width="8"/><circle cx="120" cy="230" r="26" fill="#fff1c9"/>'
        f'<rect y="340" width="{W}" height="110" fill="#8d6f99"/>' + mini_kira(470, 230, 1.4) +
        f'<rect x="600" y="200" width="160" height="110" rx="8" fill="#2a2346"/><rect x="610" y="210" width="140" height="90" rx="4" fill="#5a46b0"/>'
        '<path d="M670 240 v26 l7 -6 l6 12 l4 -2 l-6 -12 l9 0 Z" fill="#fff"/>' + star4(400, 80, 14, "#fff6c9"))
    m["first_stars"] = (sky("s", [(0, "#070820"), (1, "#3a2a6e")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' + stars_bg(90, seed=3) +
        '<path d="M230 120 L240 190 L330 190 L340 120 M240 190 L290 240 L330 190 M290 240 L420 230 L560 240 M420 230 L420 330 M560 240 L560 330 M560 240 L640 170 L700 120" fill="none" stroke="#d9c2ff" stroke-width="3"/>'
        + "".join(f'<circle cx="{x}" cy="{y}" r="6" fill="#fff6dc"/>' for x, y in [(230, 120), (240, 190), (330, 190), (340, 120), (290, 240), (420, 230), (560, 240), (420, 330), (560, 330), (640, 170), (700, 120)]) +
        f'<rect y="380" width="{W}" height="70" fill="#1a1440"/>')
    m["pancakes"] = ("",
        f'<rect width="{W}" height="{H}" fill="#4d3866"/><rect y="300" width="{W}" height="150" fill="#6a4a5e"/>'
        '<ellipse cx="400" cy="320" rx="220" ry="50" fill="#f4ece4"/>'
        + "".join(f'<ellipse cx="400" cy="{300 - i*18}" rx="150" ry="34" fill="{"#f2c27a" if i < 3 else "#5a3a2a"}" stroke="#c08a5a" stroke-width="3"/>' for i in range(4)) +
        "".join(f'<circle cx="{x}" cy="{y}" r="9" fill="#5a5ac8"/>' for x, y in [(370, 230), (400, 222), (430, 232), (390, 240)]) +
        '<path d="M470 210 C500 190 510 170 500 150" fill="none" stroke="#fff" stroke-width="4" opacity="0.6"/>')
    m["sea_photo"] = (sky("s", [(0, "#7cc3e8"), (1, "#cfeaff")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/><circle cx="620" cy="110" r="50" fill="#fff6c9"/>'
        '<rect y="250" width="800" height="200" fill="#4a8ac8"/><path d="M0 250 Q100 230 200 250 T400 250 T600 250 T800 250" fill="none" stroke="#fff" stroke-width="5"/>'
        '<path d="M0 360 C200 330 600 330 800 360 V450 H0 Z" fill="#f2d39c"/>'
        '<path d="M520 200 l30 -40 l30 40 Z" fill="#fff"/><path d="M550 160 V230" stroke="#3a2a55" stroke-width="3"/>')
    m["childs_drawing"] = ("",
        f'<rect width="{W}" height="{H}" fill="#fffaf0"/>'
        '<path d="M60 380 H740" stroke="#7a9a4a" stroke-width="10" stroke-linecap="round"/>'
        '<path d="M120 380 V240 H300 V380" fill="none" stroke="#8a6ab8" stroke-width="8" stroke-linejoin="round"/>'
        '<circle cx="640" cy="90" r="40" fill="none" stroke="#f2b84a" stroke-width="8"/>' +
        "".join(f'<path d="M{x} {y} l6 -16 l6 16 l16 4 l-16 4 l-6 16 l-6 -16 l-16 -4 Z" fill="none" stroke="#f2c84a" stroke-width="4"/>' for x, y in [(160, 80), (300, 60), (480, 110)]) +
        '<circle cx="420" cy="200" r="46" fill="none" stroke="#2a2046" stroke-width="7"/><path d="M372 190 C380 130 460 130 468 190 L470 250 M372 190 L370 250" fill="none" stroke="#2a2046" stroke-width="12"/>'
        '<circle cx="405" cy="200" r="8" fill="#8a4ad8"/><circle cx="435" cy="200" r="8" fill="#8a4ad8"/><path d="M405 222 Q420 234 435 222" fill="none" stroke="#e24a6a" stroke-width="5"/>'
        '<path d="M420 246 L390 340 H450 Z" fill="none" stroke="#b49aff" stroke-width="7"/><path d="M405 340 V380 M435 340 V380 M398 280 L360 300 M442 280 L480 300" stroke="#2a2046" stroke-width="6"/>'
        '<circle cx="560" cy="250" r="30" fill="none" stroke="#c87a5a" stroke-width="7"/><path d="M560 280 V340 M560 340 L540 380 M560 340 L580 380 M560 300 L520 290 M560 300 L600 280" stroke="#c87a5a" stroke-width="6"/>'
        '<path d="M100 396 V422 M100 410 L116 396 M100 410 L116 422 M128 422 V396 M128 422 L144 396 M144 396 V422 M156 422 V396 C174 396 174 412 156 412 M184 422 L194 396 L204 422 M188 412 H200" fill="none" stroke="#e24a6a" stroke-width="4" stroke-linecap="round"/>'
        '<path d="M470 196 l6 -6" stroke="#ff9ecf" stroke-width="5"/>')
    m["rain_cafe"] = (sky("s", [(0, "#2a1f5c"), (1, "#6a3f7e")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' +
        "".join(f'<path d="M{x} {y} l-6 22" stroke="#c9b4ff" stroke-width="2" opacity="0.6"/>' for x, y in [(random.Random(i).uniform(0, 800), random.Random(i + 99).uniform(0, 300)) for i in range(60)]) +
        '<rect x="80" y="180" width="640" height="270" fill="#ffd9a0" opacity="0.9"/><rect y="330" width="800" height="120" fill="#7a5a4a"/>'
        '<path d="M300 330 H500 V320 H300 Z" fill="#f6f1fc"/>'
        '<path d="M330 280 H390 V320 C390 330 330 330 330 320 Z" fill="#f2a9c4" stroke="#3a2a55" stroke-width="3"/><path d="M410 280 H470 V320 C470 330 410 330 410 320 Z" fill="#9fd6ff" stroke="#3a2a55" stroke-width="3"/>'
        '<path d="M350 270 C344 256 356 250 350 236 M440 270 C434 256 446 250 440 236" fill="none" stroke="#fff" stroke-width="4" opacity="0.8"/>')
    m["rooftop_promise"] = (sky("s", [(0, "#070820"), (0.7, "#2a1f5c"), (1, "#6b3f86")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' + stars_bg(100, seed=8) +
        '<rect y="330" width="800" height="120" fill="#2a2046"/><rect y="320" width="800" height="8" fill="#8f7ac0"/>'
        '<rect x="260" y="300" width="280" height="16" rx="6" fill="#a58bd0"/>'
        '<path d="M330 300 C320 250 340 220 360 220 C380 220 390 250 380 300 Z" fill="#1a1430"/><circle cx="360" cy="200" r="24" fill="#1a1430"/>'
        '<path d="M430 300 C420 250 440 216 462 216 C484 216 492 250 482 300 Z" fill="#1a1430"/><circle cx="462" cy="194" r="26" fill="#1a1430"/>'
        '<path d="M380 270 C400 262 420 262 440 270" stroke="#ff9ecf" stroke-width="4" fill="none"/>' + star5(600, 90, 20, "#fff3c0"))
    m["author_desk"] = ("",
        f'<rect width="{W}" height="{H}" fill="#2a1f3a"/><rect y="300" width="800" height="150" fill="#5a3a2a"/>'
        '<ellipse cx="560" cy="200" rx="200" ry="160" fill="#ffd98a" opacity="0.18"/>'
        '<path d="M600 300 L600 200 L560 150" stroke="#3a2a35" stroke-width="10" fill="none"/><path d="M520 130 L590 130 L580 170 L530 170 Z" fill="#f6d58a"/>'
        '<path d="M260 300 L280 230 L420 230 L440 300 Z" fill="#f6efe0" stroke="#3a2a55" stroke-width="3"/>'
        '<path d="M300 250 h90 M300 264 h70" stroke="#8a7a6a" stroke-width="3"/><path d="M180 296 L320 296" stroke="#c9a6ff" stroke-width="5"/>'
        '<rect x="120" y="270" width="100" height="26" rx="3" fill="#8a5a7a"/>')
    m["ticket"] = (sky("s", [(0, "#060418"), (1, "#3a1a5c")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' + stars_bg(120, h=450, seed=5) +
        '<g transform="rotate(-8 400 225)"><rect x="180" y="140" width="440" height="180" rx="16" fill="#f6d58a" stroke="#3a2a55" stroke-width="4"/>'
        '<path d="M500 140 V320" stroke="#3a2a55" stroke-width="3" stroke-dasharray="10 8"/>'
        '<rect x="210" y="170" width="260" height="14" fill="#c9a46a"/><rect x="210" y="200" width="200" height="10" fill="#c9a46a"/><rect x="210" y="226" width="230" height="10" fill="#c9a46a"/>'
        + star5(560, 230, 34, "#fff3c0", "#c9a46a", 3) + '</g>')
    m["kite"] = (sky("s", [(0, "#3a1a5c"), (1, "#c46c9e")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' + stars_bg(60, seed=9) +
        '<path d="M520 80 L580 150 L520 230 L460 150 Z" fill="#ff9ecf" stroke="#fff" stroke-width="3"/><path d="M520 230 C500 270 540 300 500 340 C470 370 300 380 240 420" fill="none" stroke="#fff" stroke-width="2"/>'
        '<path d="M200 450 C200 400 220 380 240 380 C260 380 270 400 270 450 Z" fill="#1a1430"/><circle cx="238" cy="362" r="22" fill="#1a1430"/>')
    m["ending_our_world"] = (sky("s", [(0, "#0b0c2a"), (1, "#3a2f6e")]),
        f'<rect width="{W}" height="{H}" fill="#6a5aa0"/><rect x="80" y="40" width="280" height="240" fill="url(#s)" stroke="#f6f1fc" stroke-width="12"/>' + '<g transform="translate(90 50)">' + stars_bg(40, w=260, h=200, seed=4) + '</g>' +
        '<path d="M0 30 Q400 90 800 30" fill="none" stroke="#2a2046" stroke-width="3"/>' +
        "".join(star4(x, 30 + 60 * math.sin(x / 800 * math.pi) * 0.5 + 12, 10, ["#ffe7b0", "#ffc0e0", "#c9b4ff"][i % 3]) for i, x in enumerate(range(40, 800, 70))) +
        f'<rect y="350" width="{W}" height="100" fill="#4a3a6a"/>' + mini_kira(520, 250, 1.4, "happy", "#ddd2f8") +
        '<path d="M640 330 H700 V370 C700 380 640 380 640 370 Z" fill="#f2a9c4"/><path d="M700 340 C716 340 716 360 700 360" fill="none" stroke="#f2a9c4" stroke-width="5"/>')
    m["ending_new_dawn"] = (sky("s", [(0, "#7fa8e8"), (0.5, "#f5b3c8"), (1, "#ffe0a8")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/><circle cx="400" cy="330" r="110" fill="#fff4d6"/>' +
        "".join(f'<rect x="{x}" y="{330 - h}" width="{w}" height="{h + 120}" fill="#8a6ab8" opacity="0.85"/>' for x, w, h in [(0, 90, 160), (100, 70, 220), (180, 110, 130), (560, 80, 200), (650, 150, 150)]) +
        f'<rect y="380" width="{W}" height="70" fill="#6a4a8a"/>' + mini_kira(400, 290, 1.1, "happy", "#c6a9c4"))
    m["ending_constellation"] = (sky("s", [(0, "#070820"), (1, "#2a1f5c")]),
        f'<rect width="{W}" height="{H}" fill="url(#s)"/>' + stars_bg(140, seed=2) +
        '<path d="M400 60 L370 90 M400 60 L430 90 M370 90 L360 140 M430 90 L440 140 M360 140 L400 170 M440 140 L400 170 M400 170 L350 220 M400 170 L450 220 M400 170 L380 260 M400 170 L420 260 M430 90 L470 70" stroke="#ffd9f0" stroke-width="3" fill="none"/>'
        + "".join(f'<circle cx="{x}" cy="{y}" r="6" fill="#fff8e6"/>' for x, y in [(400, 60), (370, 90), (430, 90), (360, 140), (440, 140), (400, 170), (350, 220), (450, 220), (380, 260), (420, 260), (470, 70)]) +
        '<rect y="360" width="800" height="90" fill="#1a1440"/><path d="M380 360 C376 330 390 316 400 316 C410 316 424 330 420 360 Z" fill="#0e0a20"/><circle cx="400" cy="304" r="14" fill="#0e0a20"/>')
    for k, (defs, body) in m.items():
        save(f"memories/{k}.svg", W, H, body, defs)


if __name__ == "__main__":
    icons()
    decor()
    memories()
    print("ok")
