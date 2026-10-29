#!/usr/bin/env python3
"""Собирает превью персонажа из слоёв для визуальной проверки."""
import json, os, re, sys
D = os.path.join(os.path.dirname(__file__), "..", "assets", "kira")
meta = json.load(open(os.path.join(D, "parts.json")))
def layer(name):
    p = meta["parts"][name]
    s = open(os.path.join(D, name + ".svg")).read()
    s = re.sub(r'<svg [^>]*>', f'<svg x="{p["x"]}" y="{p["y"]}" width="{p["w"]}" height="{p["h"]}" viewBox="{p["x"]} {p["y"]} {p["w"]} {p["h"]}" overflow="visible">', s, count=1)
    # уникализируем id
    return s.replace('id="', f'id="{name}_').replace('url(#', f'url(#{name}_')
def compose(outfit="casual", eyes="open", brows="neutral", mouth="smile", blush=False, pose="down", crop=None):
    L = []
    armz_back = pose == "behind"
    L.append("hair_back")
    if armz_back: L += [f"armL_behind_{outfit}", f"armR_behind_{outfit}"]
    L.append(f"body_{outfit}")
    if pose == "down": L += [f"armL_down_{outfit}", f"armR_down_{outfit}"]
    if pose == "chin": L += [f"armL_down_{outfit}"]
    L += ["head", f"eyes_{eyes}", f"mouth_{mouth}"]
    if blush: L.append("blush")
    L.append("hair_front")
    L.append(f"brows_{brows}")
    if pose == "chin": L.append(f"armR_chin_{outfit}")
    vb = crop or "0 0 1000 2000"
    head = {"hair_back","head","blush","hair_front"} 
    out = []
    for n in L:
        s = layer(n)
        if n in head or n.split("_")[0] in ("eyes","brows","mouth"):
            s = '<g transform="translate(500 520) scale(0.87) translate(-500 -520)">' + s + '</g>'
        out.append(s)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">' + "".join(out) + '</svg>'
if __name__ == "__main__":
    out = sys.argv[1]
    kw = dict(a.split("=") for a in sys.argv[2:])
    if "blush" in kw: kw["blush"] = kw["blush"] == "1"
    open(out, "w").write(compose(**kw))
