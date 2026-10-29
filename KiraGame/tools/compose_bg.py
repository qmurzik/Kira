import os, re, sys
D = os.path.join(os.path.dirname(__file__), "..", "assets", "bg")
def inner(path, x=0, y=0, n=0):
    s = open(os.path.join(D, path)).read()
    m = re.match(r'<svg [^>]*width="([\d.]+)" height="([\d.]+)" viewBox="([^"]+)"', s)
    w, h, vb = m.group(1), m.group(2), m.group(3)
    s = re.sub(r'<svg [^>]*>', f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="{vb}">', s, count=1)
    return s.replace('id="', f'id="L{n}').replace('url(#', f'url(#L{n}')
out = sys.argv[1]; layers = sys.argv[2:]
body = ""
for i, l in enumerate(layers):
    parts = l.split("@"); x = y = 0
    if len(parts) > 1: x, y = parts[1].split(",")
    body += inner(parts[0], x, y, i)
open(out, "w").write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 1080">{body}</svg>')
