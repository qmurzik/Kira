#!/usr/bin/env python3
"""Генератор слоёв персонажа Киры (SVG).

Все слои рисуются в общей системе координат 1000x2000, но каждый файл
обрезается своим viewBox. Смещения записываются в assets/kira/parts.json,
чтобы Godot мог собрать персонажа из слоёв (моргание, эмоции, наряды).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "kira")
os.makedirs(OUT, exist_ok=True)

SKIN = "#fdebe3"
SKIN_SH = "#f0c8c0"
SKIN_LINE = "#c98a84"
HAIR = "#211d33"
HAIR_DK = "#110e1c"
HAIR_HI = "#4a4378"
LINE = "#120f1d"
IRIS_D = "#2c1260"
IRIS_M = "#7446c9"
IRIS_L = "#cdb0ff"

MIRROR = 'transform="matrix(-1 0 0 1 1000 0)"'

parts = {}


def write(name, region, body, defs=""):
    x, y, w, h = region
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
           f'viewBox="{x} {y} {w} {h}"><defs>{defs}</defs>{body}</svg>')
    with open(os.path.join(OUT, name + ".svg"), "w", encoding="utf-8") as f:
        f.write(svg)
    parts[name] = {"x": x, "y": y, "w": w, "h": h}


# ---------------------------------------------------------------- голова
HEAD_REGION = (340, 200, 320, 340)


def face():
    defs = f'''
    <radialGradient id="cheek" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#f6a3b5" stop-opacity="0.55"/>
      <stop offset="1" stop-color="#f6a3b5" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="faceg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#f7ddd5"/>
      <stop offset="0.35" stop-color="{SKIN}"/>
      <stop offset="1" stop-color="#fcefe9"/>
    </linearGradient>'''
    body = f'''
    <!-- уши -->
    <path d="M378 352 C362 344 356 372 364 394 C370 410 382 414 388 404 Z" fill="{SKIN_SH}" stroke="{SKIN_LINE}" stroke-width="2"/>
    <path d="M622 352 C638 344 644 372 636 394 C630 410 618 414 612 404 Z" fill="{SKIN_SH}" stroke="{SKIN_LINE}" stroke-width="2"/>
    <!-- лицо -->
    <path d="M374 300 C370 352 376 404 398 436 C418 464 458 486 500 490 C542 486 582 464 602 436 C624 404 630 352 626 300 C606 214 394 214 374 300 Z"
          fill="url(#faceg)" stroke="{SKIN_LINE}" stroke-width="2.4"/>
    <!-- тень от чёлки -->
    <path d="M378 318 C420 345 460 330 500 348 C540 330 580 345 622 318 L624 296 L376 296 Z" fill="{SKIN_SH}" opacity="0.55"/>
    <!-- румянец по умолчанию -->
    <ellipse cx="420" cy="420" rx="34" ry="16" fill="url(#cheek)"/>
    <ellipse cx="580" cy="420" rx="34" ry="16" fill="url(#cheek)"/>
    <!-- нос -->
    <path d="M503 412 C506 420 504 426 497 428" fill="none" stroke="{SKIN_LINE}" stroke-width="2.2" stroke-linecap="round"/>
    <ellipse cx="503" cy="416" rx="3" ry="2" fill="#ffffff" opacity="0.8"/>
    '''
    write("head", HEAD_REGION, body, defs)


# --------------------------------------------------------------- глаза
EYES_REGION = (380, 318, 240, 108)
EYE_WHITE = "M402 384 C408 360 430 350 452 350 C470 350 484 356 491 368 C490 392 474 410 450 411 C424 411 406 401 402 384 Z"


def eye_defs():
    return f'''
    <linearGradient id="iris" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{IRIS_D}"/>
      <stop offset="0.45" stop-color="{IRIS_M}"/>
      <stop offset="1" stop-color="{IRIS_L}"/>
    </linearGradient>
    <clipPath id="ewL"><path d="{EYE_WHITE}"/></clipPath>
    '''


def eye_open(dx=0, dy=0, iris_scale=1.0, lid=0, teary=False, side="L"):
    """Один открытый глаз (рисуется левый, правый — зеркальная копия)."""
    rx, ry = 24 * iris_scale, 30 * iris_scale
    cx, cy = 450 + dx, 381 + dy
    lid_path = ""
    if lid > 0:
        ly = 352 + lid
        lid_path = (f'<path d="M392 330 L496 330 L496 {ly+2} C480 {ly-4} 430 {ly-6} 398 {ly+16} Z" fill="{SKIN}"/>'
                    f'<path d="M398 {ly+18} C426 {ly-4} 470 {ly-6} 494 {ly+6}" fill="none" stroke="{LINE}" stroke-width="7" stroke-linecap="round"/>'
                    f'<path d="M400 {ly+14} L388 {ly+22}" stroke="{LINE}" stroke-width="4" stroke-linecap="round"/>')
    tear = ""
    if teary:
        tear = (f'<path d="M414 402 C432 412 458 412 478 400" fill="none" stroke="#eaf6ff" stroke-width="4" stroke-linecap="round" opacity="0.95"/>'
                f'<circle cx="474" cy="398" r="4.5" fill="#ffffff"/>')
    lash = ("" if lid > 0 else
            f'<path d="M396 388 C402 360 428 340 454 340 C474 340 488 350 494 366 C484 358 470 351 454 351 C430 351 410 364 402 390 Z" fill="{LINE}"/>'
            f'<path d="M400 376 L386 386 L403 386 Z" fill="{LINE}"/>'
            f'<path d="M404 366 L390 356 M410 356 L400 344" stroke="{LINE}" stroke-width="3" stroke-linecap="round"/>')
    return f'''
    <path d="{EYE_WHITE}" fill="#ffffff"/>
    <g clip-path="url(#ewL)">
      <ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="url(#iris)" stroke="{IRIS_D}" stroke-width="2.5"/>
      <ellipse cx="{cx}" cy="{cy+2}" rx="{rx*0.45}" ry="{ry*0.5}" fill="#1c0b3d"/>
      <path d="M{cx-rx*0.8} {cy+ry*0.35} C{cx-rx*0.3} {cy+ry*0.9} {cx+rx*0.4} {cy+ry*0.9} {cx+rx*0.85} {cy+ry*0.3}" fill="none" stroke="#efe2ff" stroke-width="3" opacity="0.85"/>
      <path d="M400 352 C430 344 466 344 494 360 L494 374 C466 360 430 360 400 372 Z" fill="#3a2a66" opacity="0.28"/>
      <ellipse cx="{cx-9}" cy="{cy-12}" rx="8" ry="9.5" fill="#ffffff"/>
      <circle cx="{cx+10}" cy="{cy+13}" r="3.6" fill="#ffffff" opacity="0.9"/>
    </g>
    {lash}
    <path d="M418 406 C434 413 452 414 466 410" fill="none" stroke="{LINE}" stroke-width="2.2" stroke-linecap="round" opacity="0.75"/>
    <path d="M412 344 C430 330 462 330 486 344" fill="none" stroke="{SKIN_LINE}" stroke-width="2" stroke-linecap="round" opacity="0.6"/>
    {lid_path}
    {tear}
    '''


def eyes():
    d = eye_defs()
    # Зеркалим целиком группу, поэтому clipPath внутри группы тоже зеркалится —
    # используем один и тот же clipPath ewL в обеих копиях.
    def pair(l, r=None):
        r = r if r is not None else l
        return f'<g>{l}</g><g {MIRROR}>{r}</g>'

    write("eyes_open", EYES_REGION, pair(eye_open()), d)
    # взгляд в сторону (смущение): зрачки смещены вниз-вправо (для зеркала — влево)
    write("eyes_aside", EYES_REGION, pair(eye_open(dx=8, dy=4), eye_open(dx=-8, dy=4)), d)
    # взгляд вверх (задумчивость)
    write("eyes_up", EYES_REGION, pair(eye_open(dx=-6, dy=-7, lid=4), eye_open(dx=6, dy=-7, lid=4)), d)
    write("eyes_wide", EYES_REGION, pair(eye_open(iris_scale=0.82)), d)
    write("eyes_half", EYES_REGION, pair(eye_open(dy=4, lid=14)), d)
    write("eyes_sleepy", EYES_REGION, pair(eye_open(dy=6, lid=22)), d)
    write("eyes_teary", EYES_REGION, pair(eye_open(dy=3, lid=6, teary=True)), d)
    closed = (f'<path d="M402 384 C420 400 466 402 490 380" fill="none" stroke="{LINE}" stroke-width="6" stroke-linecap="round"/>'
              f'<path d="M402 384 L388 380" stroke="{LINE}" stroke-width="4" stroke-linecap="round"/>'
              f'<path d="M416 395 L410 405 M432 399 L429 409" stroke="{LINE}" stroke-width="2.5" stroke-linecap="round"/>')
    write("eyes_closed", EYES_REGION, pair(closed), d)
    happy = (f'<path d="M404 392 C420 360 470 356 490 386" fill="none" stroke="{LINE}" stroke-width="7" stroke-linecap="round"/>'
             f'<path d="M404 392 L392 396" stroke="{LINE}" stroke-width="4" stroke-linecap="round"/>')
    write("eyes_happy", EYES_REGION, pair(happy), d)


# --------------------------------------------------------------- брови
BROWS_REGION = (396, 290, 208, 60)


def brows():
    def pair(l, r=None):
        r = r if r is not None else l
        halo = 'fill="none" stroke="#7d70b0" stroke-width="8" stroke-linecap="round" opacity="0.45"'
        st = f'fill="none" stroke="#1d1729" stroke-width="4.2" stroke-linecap="round"'
        return (f'<path d="{l}" {halo}/><path d="{l}" {st}/>'
                f'<g {MIRROR}><path d="{r}" {halo}/><path d="{r}" {st}/></g>')
    write("brows_neutral", BROWS_REGION, pair("M414 324 C430 314 458 312 478 318"))
    write("brows_up", BROWS_REGION, pair("M414 314 C430 300 458 298 478 306"))
    write("brows_sad", BROWS_REGION, pair("M414 318 C432 320 458 316 478 306"))
    write("brows_angry", BROWS_REGION, pair("M414 314 C432 314 456 320 480 330"))
    write("brows_soft", BROWS_REGION, pair("M414 322 C432 316 458 316 478 322"))
    # одна бровь приподнята (хитрость / задумчивость)
    write("brows_raise", BROWS_REGION, pair("M414 324 C430 316 458 314 478 320", "M414 312 C430 300 458 298 478 306"))


# ---------------------------------------------------------------- рот
MOUTH_REGION = (470, 428, 60, 44)


def mouths():
    st = 'fill="none" stroke="#a24c62" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"'
    write("mouth_smile", MOUTH_REGION, f'<path d="M486 446 C494 454 506 454 514 446" {st}/>')
    write("mouth_neutral", MOUTH_REGION, f'<path d="M490 449 C496 451 504 451 510 449" {st}/>')
    write("mouth_grin", MOUTH_REGION,
          '<path d="M482 442 C492 444 508 444 518 442 C516 462 484 462 482 442 Z" fill="#8a2e45" stroke="#7a2038" stroke-width="2"/>'
          '<path d="M488 455 C494 450 506 450 512 455 C506 461 494 461 488 455 Z" fill="#ec8ea0"/>'
          '<path d="M484 443 C494 446 506 446 516 443 L515 447 C505 449 495 449 485 447 Z" fill="#fff"/>')
    write("mouth_talk", MOUTH_REGION,
          '<path d="M489 444 C496 442 504 442 511 444 C510 456 490 456 489 444 Z" fill="#8a2e45" stroke="#7a2038" stroke-width="2"/>'
          '<path d="M493 452 C498 449 502 449 507 452 C503 455 497 455 493 452 Z" fill="#ec8ea0"/>')
    write("mouth_o", MOUTH_REGION,
          '<ellipse cx="500" cy="451" rx="7" ry="9" fill="#8a2e45" stroke="#7a2038" stroke-width="2"/>'
          '<ellipse cx="500" cy="456" rx="4" ry="3" fill="#ec8ea0"/>')
    write("mouth_frown", MOUTH_REGION, f'<path d="M488 454 C494 446 506 446 512 454" {st}/>')
    write("mouth_pout", MOUTH_REGION,
          f'<path d="M488 450 C493 444 498 446 500 449 C502 446 507 444 512 450" {st}/>')
    write("mouth_wavy", MOUTH_REGION,
          f'<path d="M484 449 Q489 444 494 449 Q499 454 504 449 Q509 444 514 449" {st}/>')
    write("mouth_cat", MOUTH_REGION,
          f'<path d="M485 445 Q492 454 500 446 Q508 454 515 445" {st}/>')
    write("mouth_yawn", MOUTH_REGION,
          '<ellipse cx="500" cy="451" rx="6" ry="7" fill="#8a2e45" stroke="#7a2038" stroke-width="2"/>')


# ----------------------------------------------------------- накладки
FX_REGION = (380, 380, 240, 80)


def overlays():
    defs = '''<radialGradient id="bl" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#ff7d9e" stop-opacity="0.7"/>
      <stop offset="1" stop-color="#ff7d9e" stop-opacity="0"/></radialGradient>'''
    lines = ''.join(f'<path d="M{x} 412 l-7 12" stroke="#e06a88" stroke-width="2.4" stroke-linecap="round" opacity="0.8"/>'
                    for x in (412, 424, 436, 572, 584, 596))
    write("blush", FX_REGION,
          f'<ellipse cx="422" cy="418" rx="42" ry="20" fill="url(#bl)"/><ellipse cx="578" cy="418" rx="42" ry="20" fill="url(#bl)"/>{lines}', defs)


# ------------------------------------------------------------- волосы
HAIRB_REGION = (310, 120, 380, 420)
HAIRF_REGION = (330, 120, 340, 420)


def hair_defs():
    return f'''
    <linearGradient id="hg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#2d2748"/>
      <stop offset="0.5" stop-color="{HAIR}"/>
      <stop offset="1" stop-color="{HAIR_DK}"/>
    </linearGradient>
    <linearGradient id="hl" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" stop-color="{HAIR_HI}" stop-opacity="0"/>
      <stop offset="0.5" stop-color="#6d63a8" stop-opacity="0.85"/>
      <stop offset="1" stop-color="{HAIR_HI}" stop-opacity="0"/>
    </linearGradient>'''


def strand(xt, yt, xb, yb, w, bend=0.0, fill="url(#hg)", stroke=True):
    """Прядь-«лист»: от корня (xt, yt) шириной w до кончика (xb, yb)."""
    l, r = xt - w / 2, xt + w / 2
    my = (yt + yb) / 2
    c1x = l + (xb - xt) * 0.35 + bend
    c2x = r + (xb - xt) * 0.35 + bend
    st = f'stroke="{LINE}" stroke-width="2.6" stroke-linejoin="round"' if stroke else ''
    return (f'<path d="M{l} {yt} C{l} {my} {c1x - 4} {yb - (yb-yt)*0.25} {xb} {yb} '
            f'C{c2x + 4} {yb - (yb-yt)*0.3} {r} {my} {r} {yt} Z" fill="{fill}" {st}/>')


def hair_back():
    body = f'''
    <path d="M356 300 C336 196 414 130 500 130 C586 130 664 196 644 300
             C654 352 660 410 662 458 C668 476 676 492 684 502 C664 502 650 494 640 486
             C640 500 646 514 652 524 C628 518 614 506 606 492 L394 492
             C386 506 372 518 348 524 C354 514 360 500 360 486 C350 494 336 502 316 502
             C324 492 332 476 338 458 C340 410 346 352 356 300 Z"
          fill="url(#hg)" stroke="{LINE}" stroke-width="3" stroke-linejoin="round"/>
    <path d="M372 470 C384 430 386 380 380 330" fill="none" stroke="#3a3360" stroke-width="3" opacity="0.6"/>
    <path d="M628 470 C616 430 614 380 620 330" fill="none" stroke="#3a3360" stroke-width="3" opacity="0.6"/>
    '''
    write("hair_back", (300, 120, 400, 420), body, hair_defs())


def hair_front():
    cap = (f'<path d="M360 318 C346 220 420 152 500 152 C582 152 656 220 640 318 '
           f'C600 262 560 250 500 250 C440 250 400 262 360 318 Z" fill="url(#hg)"/>'
           f'<path d="M360 318 C346 220 420 152 500 152 C582 152 656 220 640 318" fill="none" stroke="{LINE}" stroke-width="3"/>')
    # чёлка: пряди разной длины, слегка зачёсаны в стороны
    bangs = [
        (400, 246, 382, 352, 44, -6), (426, 232, 414, 340, 50, -4), (600, 246, 626, 350, 44, 6),
        (576, 232, 594, 334, 50, 4), (452, 222, 446, 346, 50, -2), (552, 222, 560, 342, 50, 2),
        (486, 218, 480, 336, 46, -2), (518, 218, 526, 352, 44, 2), (500, 214, 500, 316, 40, 0),
    ]
    b = "".join(strand(*p) for p in bangs)
    # боковые пряди, обрамляющие лицо, с подкрученными кончиками
    side = (strand(386, 262, 376, 506, 42, -10) + strand(400, 290, 402, 470, 32, 4) +
            strand(614, 262, 626, 506, 42, 10) + strand(600, 290, 598, 470, 32, -4))
    tufts = (f'<path d="M500 156 C492 120 520 96 552 104 C530 110 514 126 512 156 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="3" stroke-linejoin="round"/>'
             f'<path d="M430 170 C410 160 392 168 384 182 C402 176 416 178 428 186 Z" fill="{HAIR}" stroke="{LINE}" stroke-width="2.5"/>')
    shine = f'''
    <path d="M394 238 C430 216 470 224 500 216 C532 224 572 214 608 238 C598 250 580 244 566 254
             C548 244 530 252 512 246 C494 254 476 244 458 254 C440 244 418 252 400 250 Z" fill="url(#hl)"/>
    <path d="M440 270 C436 290 436 306 440 320 M560 270 C564 290 564 306 560 320" fill="none" stroke="#5b5290" stroke-width="2.5" opacity="0.6"/>
    '''
    clips = f'''
    <g transform="rotate(-30 590 276)">
      <rect x="566" y="270" width="54" height="11" rx="5.5" fill="#b89cff" stroke="#4d3394" stroke-width="2.5"/>
      <rect x="571" y="272.5" width="22" height="3" rx="1.5" fill="#f1e9ff"/>
    </g>
    <g transform="rotate(-52 604 304)">
      <rect x="582" y="298" width="46" height="10" rx="5" fill="#ffb3d1" stroke="#9a3f72" stroke-width="2.5"/>
    </g>
    <path d="M404 272 l6 -14 l6 14 l14 6 l-14 6 l-6 14 l-6 -14 l-14 -6 Z" fill="#fff4b8" stroke="#c6a24a" stroke-width="1.6"/>
    '''
    write("hair_front", (320, 90, 360, 440), side + b + cap + tufts + shine + clips, hair_defs())


# ----------------------------------------------------------------- тело
BODY_REGION = (250, 430, 500, 1540)

NECK = "M468 440 L532 440 L536 548 C520 558 480 558 464 548 Z"
LEG_L = ("M398 1000 C394 1100 398 1230 410 1340 C418 1420 424 1480 426 1520 C420 1600 424 1700 436 1800 "
         "C440 1850 442 1870 444 1890 L478 1890 C476 1860 474 1820 474 1780 C478 1700 486 1600 478 1520 "
         "C480 1440 484 1360 488 1300 C494 1200 498 1120 502 1060 Z")


def legs_clip():
    return (f'<clipPath id="legs"><path d="{LEG_L}"/><path d="{LEG_L}" {MIRROR}/></clipPath>')


def skin_base(legs=True):
    s = f'''
    <path d="{NECK}" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>
    <path d="M466 470 C482 498 518 498 534 470 L535 506 C518 524 482 524 465 506 Z" fill="{SKIN_SH}"/>
    '''
    if legs:
        s += f'''
    <path d="{LEG_L}" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>
    <path d="{LEG_L}" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2" {MIRROR}/>
    <path d="M432 1508 C442 1520 462 1520 472 1508" fill="none" stroke="{SKIN_LINE}" stroke-width="2" opacity="0.6"/>
    <path d="M568 1508 C558 1520 538 1520 528 1508" fill="none" stroke="{SKIN_LINE}" stroke-width="2" opacity="0.6"/>
    '''
    return s


def legwear(top, color, stripe=None, line=True):
    """Носки/колготки: прямоугольник, обрезанный по форме ног."""
    s = f'<g clip-path="url(#legs)"><rect x="380" y="{top}" width="240" height="{1900-top}" fill="{color}"/>'
    if stripe:
        s += f'<rect x="380" y="{top+10}" width="240" height="8" fill="{stripe}"/>'
    s += '</g>'
    if line:
        s += f'<path d="{LEG_L}" fill="none" stroke="{LINE}" stroke-width="2" opacity="0.5"/><path d="{LEG_L}" fill="none" stroke="{LINE}" stroke-width="2" opacity="0.5" {MIRROR}/>'
    return s


def torso_path(bottom=1010, flare=0):
    """Силуэт верха одежды от плеч до низа (bottom)."""
    return (f"M500 546 C470 546 440 550 410 560 C380 568 360 584 352 612 L364 692 C372 752 396 820 404 880 "
            f"C394 930 {386-flare} {bottom-60} {382-flare} {bottom} "
            f"L{618+flare} {bottom} C{614+flare} {bottom-60} 606 930 596 880 "
            f"C604 820 628 752 636 692 L648 612 C640 584 620 568 590 560 C560 550 530 546 500 546 Z")


def _shoe_pair(one):
    return f'<g>{one}</g><g {MIRROR}>{one}</g>'


def shoes(kind, color, sole="#ffffff", accent="#9b7be0"):
    if kind == "sneakers":
        return _shoe_pair(f'''
        <path d="M440 1868 C424 1880 408 1904 414 1932 L490 1934 C494 1910 488 1884 480 1868 Z" fill="{color}" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M410 1926 L492 1928 L492 1944 L408 1942 Z" fill="{sole}" stroke="{LINE}" stroke-width="2"/>
        <path d="M434 1886 L470 1882 M430 1900 L474 1896" stroke="{accent}" stroke-width="3" stroke-linecap="round"/>''')
    if kind == "boots":
        return _shoe_pair(f'''
        <path d="M424 1700 L478 1700 C478 1780 482 1860 490 1912 L490 1944 L406 1944 C402 1916 420 1896 432 1876 C428 1810 424 1750 424 1700 Z" fill="{color}" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M404 1934 L490 1934 L490 1948 L404 1948 Z" fill="{sole}"/>
        <path d="M424 1712 L478 1712" stroke="#000" stroke-width="5" opacity="0.25"/>''')
    if kind == "slippers":
        return _shoe_pair(f'''
        <path d="M414 1904 C410 1932 422 1944 452 1944 C482 1944 494 1932 488 1904 Z" fill="{color}" stroke="{LINE}" stroke-width="2.5"/>
        <circle cx="450" cy="1910" r="11" fill="{accent}" opacity="0.9"/>''')
    return _shoe_pair(f'''
        <path d="M440 1876 C420 1890 406 1918 414 1940 L488 1940 C492 1916 486 1890 480 1876 Z" fill="{color}" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M432 1894 C446 1900 468 1900 482 1892" fill="none" stroke="{accent}" stroke-width="3"/>''')


def pants(color, shade, bottom=1890, wide=False):
    w = 12 if wide else 0
    return f'''
    <path d="M392 960 L608 960 C614 1100 606 1300 592 1460 C586 1600 {580+w} 1760 {578+w} {bottom}
             L520 {bottom} C518 1760 516 1600 514 1460 C510 1320 506 1180 500 1080
             C494 1180 490 1320 486 1460 C484 1600 482 1760 480 {bottom} L{422-w} {bottom}
             C{420-w} 1760 414 1600 408 1460 C394 1300 386 1100 392 960 Z"
          fill="{color}" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <path d="M500 1080 L500 1000" stroke="{shade}" stroke-width="3"/>
    <path d="M440 1220 C446 1330 446 1430 450 1540" fill="none" stroke="{shade}" stroke-width="3" opacity="0.6"/>
    <path d="M560 1220 C554 1330 554 1430 550 1540" fill="none" stroke="{shade}" stroke-width="3" opacity="0.6"/>
    '''


def pleated_skirt(color, shade, top=880, bottom=1200, check=None):
    pleats = "".join(f'<path d="M{500+i*26} {top+30} L{500+i*36} {bottom-6}" stroke="{shade}" stroke-width="3" opacity="0.8"/>'
                     for i in range(-4, 5) if i != 0)
    chk = ""
    if check:
        chk = "".join(f'<path d="M340 {y} L660 {y}" stroke="{check}" stroke-width="5" opacity="0.55"/>' for y in range(top+40, bottom, 46))
        chk += "".join(f'<path d="M{x} {top} L{500+(x-500)*1.4} {bottom}" stroke="{check}" stroke-width="5" opacity="0.45"/>' for x in range(400, 620, 44))
    shape = (f"M406 {top} L594 {top} C608 {top+100} 630 {bottom-80} 648 {bottom} "
             f"Q622 {bottom+14} 596 {bottom} Q570 {bottom+14} 544 {bottom} Q518 {bottom+14} 500 {bottom} "
             f"Q482 {bottom+14} 456 {bottom} Q430 {bottom+14} 404 {bottom} Q378 {bottom+14} 352 {bottom} "
             f"C370 {bottom-80} 392 {top+100} 406 {top} Z")
    return f'''
    <defs><clipPath id="skc"><path d="{shape}"/></clipPath></defs>
    <path d="{shape}" fill="{color}" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
    <g clip-path="url(#skc)">{chk}
      <path d="M380 {top+40} C440 {top+60} 560 {top+60} 620 {top+40} L640 {bottom} L360 {bottom} Z" fill="#000" opacity="0.08"/>
    </g>
    {pleats}
    <path d="M404 {top} L596 {top} L598 {top+26} L402 {top+26} Z" fill="{shade}" stroke="{LINE}" stroke-width="2"/>
    '''


def outfit(name):
    """Возвращает (svg тела, параметры рукава для рук)."""
    s = ""
    if name == "casual":
        sleeve = ("#c9b8f4", "#a592dd", "cuff")
        s += skin_base()
        s += legwear(1560, "#2c2944", "#b8a6ef")
        s += shoes("sneakers", "#f6f3ff", "#b49cf2", "#8b6ad8")
        s += pleated_skirt("#2f2c55", "#23203f", 880, 1200)
        s += f'<path d="{torso_path(960)}" fill="#fbfaff" stroke="{LINE}" stroke-width="2.5"/>'
        s += '<path d="M472 550 C482 590 518 590 528 550" fill="none" stroke="#d7cfee" stroke-width="5"/>'
        s += f'<path d="M474 548 C484 580 516 580 526 548 C516 560 484 560 474 548 Z" fill="{SKIN}"/>'
        s += f'''
        <path d="M470 550 C440 550 412 556 394 564 C366 576 354 594 350 618 L362 700 C374 766 390 830 396 880
                 C390 940 380 1000 374 1050 L470 1056 C468 900 466 720 476 590 Z" fill="#c9b8f4" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
        <path d="M530 550 C560 550 588 556 606 564 C634 576 646 594 650 618 L638 700 C626 766 610 830 604 880
                 C610 940 620 1000 626 1050 L530 1056 C532 900 534 720 524 590 Z" fill="#c9b8f4" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
        <path d="M470 560 C460 700 462 880 470 1054" fill="none" stroke="#a592dd" stroke-width="6"/>
        <path d="M530 560 C540 700 538 880 530 1054" fill="none" stroke="#a592dd" stroke-width="6"/>
        <circle cx="476" cy="760" r="6" fill="#f5efff" stroke="#7d68c0" stroke-width="2"/>
        <circle cx="477" cy="840" r="6" fill="#f5efff" stroke="#7d68c0" stroke-width="2"/>
        <circle cx="478" cy="920" r="6" fill="#f5efff" stroke="#7d68c0" stroke-width="2"/>
        <path d="M378 1022 L468 1028 M622 1022 L532 1028" stroke="#a592dd" stroke-width="5"/>
        <path d="M398 760 C410 800 420 840 424 880 M602 760 C590 800 580 840 576 880" fill="none" stroke="#a592dd" stroke-width="2.5" opacity="0.7"/>
        <path d="M430 690 C440 720 446 760 440 800 L452 800 C456 760 450 720 440 690 Z" fill="#b9a6ea" opacity="0.8"/>
        <path d="M446 700 l6 -10 l6 10 l10 4 l-10 4 l-6 10 l-6 -10 l-10 -4 Z" fill="#fff6c9" opacity="0.9"/>
        '''
    elif name == "hoodie":
        sleeve = ("#9576e0", "#6f52b8", "rib")
        s += skin_base()
        s += pants("#2b3256", "#1f2541")
        s += shoes("sneakers", "#2a2440", "#f2eefe", "#c8b4ff")
        s += f'''
        <path d="{torso_path(1090, 14)}" fill="#9576e0" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M408 560 C420 622 580 622 592 560 C570 544 430 544 408 560 Z" fill="#7d5fcc" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M454 584 C470 606 530 606 546 584" fill="none" stroke="#5f43a6" stroke-width="4"/>
        <path d="M476 600 L470 700 M524 600 L530 700" stroke="#f2ecff" stroke-width="4" stroke-linecap="round"/>
        <circle cx="470" cy="704" r="5" fill="#f2ecff"/><circle cx="530" cy="704" r="5" fill="#f2ecff"/>
        <path d="M414 910 L586 910 L600 1020 L400 1020 Z" fill="#8667d4" stroke="#5f43a6" stroke-width="3"/>
        <path d="M368 1060 L632 1060 L634 1090 L366 1090 Z" fill="#7d5fcc" stroke="{LINE}" stroke-width="2"/>
        <path d="M480 770 l12 -24 l12 24 l26 8 l-26 8 l-12 24 l-12 -24 l-26 -8 Z" fill="#fff3b0" stroke="#d9b54e" stroke-width="2"/>
        <circle cx="560" cy="730" r="5" fill="#fff3b0"/><circle cx="436" cy="830" r="4" fill="#fff3b0"/>
        <path d="M400 700 C412 760 420 820 424 880" fill="none" stroke="#7d5fcc" stroke-width="3" opacity="0.7"/>
        '''
    elif name == "sweater":
        sleeve = ("#f6ead9", "#d9c6ad", "rib")
        s += skin_base()
        s += legwear(1520, "#f5efe6", "#b894e0")
        s += shoes("flats", "#5b3a4a", "#3a2430", "#d9a9c0")
        s += pleated_skirt("#7a3d66", "#5c2a4c", 900, 1220, check="#c98ab6")
        s += f'''
        <path d="{torso_path(1000, 10)}" fill="#f6ead9" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M456 470 L544 470 L548 572 C520 584 480 584 452 572 Z" fill="#efdfc8" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M466 484 L466 570 M484 484 L484 578 M502 484 L502 580 M520 484 L520 578 M536 484 L536 572" stroke="#d6c1a5" stroke-width="3"/>
        <path d="M374 970 L626 970 L628 1000 L372 1000 Z" fill="#eadbc4" stroke="{LINE}" stroke-width="2"/>
        ''' + "".join(f'<path d="M{x} 620 C{x-6} 700 {x+6} 800 {x} 960" fill="none" stroke="#e2d1b9" stroke-width="4"/>' for x in (420, 460, 500, 540, 580)) + '''
        <path d="M440 700 C450 720 470 720 480 700 C470 690 450 690 440 700 Z" fill="#c9a6e8" opacity="0.8"/>
        '''
    elif name == "coat":
        sleeve = ("#c6a9c4", "#977a96", "cuff")
        s += skin_base()
        s += legwear(1000, "#2b2238", line=False)
        s += shoes("boots", "#3c2b33", "#22181d")
        s += f'''
        <path d="M500 546 C440 546 404 554 380 566 C356 580 348 598 346 622 L356 704 C368 770 386 830 392 880
                 C378 1000 358 1220 352 1420 L648 1420 C642 1220 622 1000 608 880 C614 830 632 770 644 704 L654 622
                 C652 598 644 580 620 566 C596 554 560 546 500 546 Z" fill="#c6a9c4" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
        <path d="M500 600 L500 1420" stroke="#987b97" stroke-width="4"/>
        <path d="M392 880 L608 880 L610 920 L390 920 Z" fill="#8f6f8e" stroke="{LINE}" stroke-width="2"/>
        <rect x="484" y="872" width="32" height="56" rx="4" fill="none" stroke="#e8d7a0" stroke-width="5"/>
        <circle cx="530" cy="660" r="7" fill="#7d607c"/><circle cx="530" cy="760" r="7" fill="#7d607c"/>
        <circle cx="470" cy="660" r="7" fill="#7d607c"/><circle cx="470" cy="760" r="7" fill="#7d607c"/>
        <path d="M420 1000 C426 1140 430 1280 432 1410 M580 1000 C574 1140 570 1280 568 1410" fill="none" stroke="#a88aa6" stroke-width="3"/>
        <path d="M450 520 C470 560 530 560 550 520 L566 560 C540 600 460 600 434 560 Z" fill="#b8a2f2" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M520 580 L548 720 L512 724 L500 590 Z" fill="#a990ea" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M514 712 L516 734 M524 711 L527 733 M534 710 L538 731 M544 709 L548 728" stroke="#8a72cf" stroke-width="3"/>
        '''
    elif name == "dress":
        sleeve = ("#2d2f63", "#1f2149", "puff")
        s += skin_base()
        s += shoes("flats", "#24234a", "#141331", "#b8a6ef")
        s += f'''
        <path d="M402 870 L598 870 C620 980 660 1200 690 1380 Q660 1400 630 1386 Q600 1404 570 1388 Q540 1406 500 1390
                 Q460 1406 430 1388 Q400 1404 370 1386 Q340 1400 310 1380 C340 1200 380 980 402 870 Z"
              fill="#2b2d60" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>
        <path d="M440 960 C420 1100 400 1250 380 1380 M560 960 C580 1100 600 1250 620 1380 M500 900 L500 1380" fill="none" stroke="#202250" stroke-width="3"/>
        <path d="{torso_path(890)}" fill="#2b2d60" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M430 556 C450 610 480 618 500 600 C520 618 550 610 570 556 C540 548 460 548 430 556 Z" fill="#f7f4ff" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M486 604 L500 640 L514 604 Z" fill="#c59bff" stroke="{LINE}" stroke-width="2"/>
        <path d="M398 862 L602 862 L604 890 L396 890 Z" fill="#c59bff" stroke="{LINE}" stroke-width="2"/>
        <path d="M500 876 C470 856 460 906 500 886 C540 906 530 856 500 876 Z" fill="#d9bcff" stroke="{LINE}" stroke-width="2"/>
        ''' + "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#f2e9ff" opacity="0.9"/>' for x, y, r in
                      [(440, 700, 3), (560, 740, 4), (470, 800, 2.5), (530, 680, 2.5), (420, 1000, 4), (580, 1060, 3),
                       (480, 1150, 4), (620, 1260, 3), (380, 1280, 3), (520, 990, 2.5), (450, 1300, 3), (560, 1320, 3.5)]) + \
            "".join(f'<path d="M{x} {y} l4 -9 l4 9 l9 4 l-9 4 l-4 9 l-4 -9 l-9 -4 Z" fill="#fff3b8"/>' for x, y in
                    [(540, 1160), (420, 1160), (600, 980), (456, 950)])
    elif name == "pajama":
        sleeve = ("#ddd2f8", "#b8a8e8", "cuff")
        s += skin_base()
        s += pants("#d3c6f4", "#b3a2e2", 1890, wide=True)
        s += shoes("slippers", "#f4eefe", "#f4eefe", "#c9a6ff")
        s += f'''
        <path d="{torso_path(1030, 12)}" fill="#ddd2f8" stroke="{LINE}" stroke-width="2.5"/>
        <path d="M440 556 L500 640 L560 556 C540 550 460 550 440 556 Z" fill="{SKIN}" stroke="{LINE}" stroke-width="2"/>
        <path d="M440 556 L500 640 L500 660 L430 570 Z M560 556 L500 640 L500 660 L570 570 Z" fill="#bfaef0" stroke="{LINE}" stroke-width="2"/>
        <path d="M500 650 L500 1030" stroke="#b8a8e8" stroke-width="4"/>
        <circle cx="510" cy="720" r="6" fill="#fff"/><circle cx="510" cy="820" r="6" fill="#fff"/><circle cx="510" cy="920" r="6" fill="#fff"/>
        ''' + "".join(f'<path d="M{x} {y} a14 14 0 1 0 12 22 a11 11 0 1 1 -12 -22 Z" fill="#fff5c4" opacity="0.95"/>' for x, y in
                      [(420, 700), (570, 780), (440, 900), (580, 960), (430, 1260), (560, 1360), (450, 1580), (556, 1660)])
    else:
        raise ValueError(name)
    return s, sleeve


# ------------------------------------------------------------------- руки
ARM_REGION_L = (280, 540, 200, 640)


def hand(cx, cy, rot=0):
    """Расслабленная кисть: (cx, cy) — запястье."""
    return f'''<g transform="rotate({rot} {cx} {cy})">
      <path d="M{cx-15} {cy-6} C{cx-22} {cy+18} {cx-20} {cy+52} {cx-8} {cy+72} C{cx-2} {cy+82} {cx+10} {cy+80} {cx+14} {cy+66}
               C{cx+20} {cy+46} {cx+20} {cy+20} {cx+15} {cy-6} Z" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>
      <path d="M{cx+14} {cy+14} C{cx+26} {cy+24} {cx+26} {cy+42} {cx+16} {cy+50}" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2"/>
      <path d="M{cx-9} {cy+46} L{cx-7} {cy+68} M{cx+1} {cy+48} L{cx+3} {cy+72}" stroke="{SKIN_LINE}" stroke-width="1.6" opacity="0.6"/>
    </g>'''


def arm_down(sleeve):
    col, sh, kind = sleeve
    cuff = ""
    if kind == "cuff":
        cuff = f'<path d="M320 1028 L366 1028 L368 1056 L318 1056 Z" fill="{sh}" stroke="{LINE}" stroke-width="2.2"/>'
    elif kind == "rib":
        cuff = (f'<path d="M318 1020 L366 1020 L368 1058 L316 1058 Z" fill="{sh}" stroke="{LINE}" stroke-width="2.2"/>'
                f'<path d="M328 1024 L328 1054 M340 1024 L340 1056 M352 1024 L352 1054" stroke="{col}" stroke-width="2"/>')
    if kind == "puff":
        sl = (f'<path d="M392 566 C356 568 330 590 326 640 C330 690 356 704 384 692 C396 660 400 600 392 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5"/>'
              f'<path d="M330 670 C344 690 370 694 386 684" fill="none" stroke="{sh}" stroke-width="5"/>')
        skin_arm = (f'<path d="M334 680 C328 760 324 840 326 920 C328 970 330 1020 334 1060 L362 1060 C362 1010 364 960 366 920 '
                    f'C370 840 374 760 380 690 Z" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>')
        return skin_arm + sl + hand(348, 1060, 4)
    return (f'<path d="M392 566 C360 572 340 594 336 640 C330 720 324 800 322 870 C320 940 320 1000 320 1050 L366 1050 '
            f'C368 1000 370 940 372 880 C376 800 380 730 382 680 C384 640 396 600 392 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>'
            f'<path d="M332 860 C344 872 360 872 372 860" fill="none" stroke="{sh}" stroke-width="3"/>'
            f'<path d="M344 620 C340 700 336 780 336 840" fill="none" stroke="{sh}" stroke-width="3" opacity="0.6"/>'
            f'{cuff}' + hand(344, 1056, 4))


def arm_behind(sleeve):
    col, sh, kind = sleeve
    if kind == "puff":
        sl = (f'<path d="M392 566 C356 568 330 590 326 640 C330 690 356 704 384 692 C396 660 400 600 392 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5"/>')
        skin_arm = (f'<path d="M334 680 C332 760 342 840 362 900 L404 936 L414 880 C396 820 384 750 380 690 Z" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>')
        return skin_arm + sl
    return (f'<path d="M392 566 C360 572 340 594 336 640 C332 720 336 820 358 900 L404 944 L416 884 C398 820 386 750 384 690 '
            f'C386 640 396 600 392 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>'
            f'<path d="M340 840 C352 852 368 852 380 840" fill="none" stroke="{sh}" stroke-width="3"/>')


ARM_REGION_CHIN = (430, 470, 280, 600)


def arm_chin(sleeve):
    """Правая рука (для зрителя) согнута, кулачок у подбородка."""
    col, sh, kind = sleeve
    upper = (f'<path d="M608 566 C640 572 660 594 664 640 C668 720 668 790 664 850 L622 846 C624 790 622 730 618 680 '
             f'C616 640 604 600 608 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5" stroke-linejoin="round"/>')
    fore_col, fore_line = col, LINE
    if kind == "puff":
        upper = (f'<path d="M608 566 C644 568 670 590 674 640 C670 690 644 704 616 692 C604 660 600 600 608 566 Z" fill="{col}" stroke="{LINE}" stroke-width="2.5"/>'
                 f'<path d="M666 690 C670 750 668 800 664 850 L624 846 C626 790 626 740 622 696 Z" fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>')
        fore_col, fore_line = SKIN, SKIN_LINE
    fore = (f'<path d="M664 846 C646 872 596 790 574 560 L540 566 C556 770 600 884 640 884 C662 882 672 866 664 846 Z" '
            f'fill="{fore_col}" stroke="{fore_line}" stroke-width="2.5" stroke-linejoin="round"/>')
    fist = (f'<path d="M530 494 C518 508 518 550 536 566 C550 576 572 570 578 554 C584 534 578 510 566 498 C554 488 538 488 530 494 Z" '
            f'fill="{SKIN}" stroke="{SKIN_LINE}" stroke-width="2.2"/>'
            f'<path d="M536 516 C546 514 558 516 568 522 M534 534 C546 532 558 534 570 540" fill="none" stroke="{SKIN_LINE}" stroke-width="1.8"/>')
    return upper + fore + fist


OUTFITS = ["casual", "hoodie", "sweater", "coat", "dress", "pajama"]


def bodies():
    for o in OUTFITS:
        svg, sleeve = outfit(o)
        write(f"body_{o}", BODY_REGION, svg, legs_clip())
        write(f"armL_down_{o}", ARM_REGION_L, arm_down(sleeve))
        write(f"armR_down_{o}", (1000 - ARM_REGION_L[0] - ARM_REGION_L[2],) + ARM_REGION_L[1:],
              f'<g {MIRROR}>{arm_down(sleeve)}</g>')
        write(f"armL_behind_{o}", ARM_REGION_L, arm_behind(sleeve))
        write(f"armR_behind_{o}", (1000 - ARM_REGION_L[0] - ARM_REGION_L[2],) + ARM_REGION_L[1:],
              f'<g {MIRROR}>{arm_behind(sleeve)}</g>')
        write(f"armR_chin_{o}", ARM_REGION_CHIN, arm_chin(sleeve))


def main():
    face()
    eyes()
    brows()
    mouths()
    overlays()
    hair_back()
    hair_front()
    bodies()
    meta = {
        "canvas": [1000, 2000],
        "neck_pivot": [500, 520],
        "head_scale": 0.87,
        "outfits": OUTFITS,
        "parts": parts,
    }
    with open(os.path.join(OUT, "parts.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print("parts:", len(parts))


if __name__ == "__main__":
    main()
