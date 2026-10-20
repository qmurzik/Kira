#!/usr/bin/env python3
"""Static sanity checks that do not need the Android SDK.

Checks:
  1. every `import app.pulse.client...` points at a declared top-level symbol;
  2. every R.<type>.<name> used in Kotlin exists in res/ (or is an android: resource);
  3. every @type/name reference in res/ and the manifest exists;
  4. every class named in AndroidManifest.xml exists in sources;
  5. every libs.<alias> used in Gradle files is declared in the version catalog;
  6. no obvious secrets / token logging in sources;
  7. unused project imports of project symbols (warning only).
Exit code 1 on any error.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")
RES = os.path.join(ROOT, "app/src/main/res")
MANIFEST = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")
PKG = "app.pulse.client"

errors, warnings = [], []


def kotlin_files():
    for d, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(d, f)


# ---------- 1. project symbols ----------
decl_re = re.compile(
    r"^(?:@[\w.]+(?:\([^)]*\))?\s+)*(?:(?:private|internal|public|data|sealed|enum|abstract|open|inline|value|annotation|fun|suspend|const|operator|infix|tailrec)\s+)*"
    r"(class|interface|object|fun|val|var|typealias)\s+(?:<[^>]+>\s*)?(?:[\w.]+\.)?(`?\w+`?)",
    re.M,
)
symbols = {}  # package -> set(names)
file_pkg = {}
for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    m = re.search(r"^package\s+([\w.]+)", text, re.M)
    pkg = m.group(1) if m else ""
    file_pkg[path] = pkg
    expected = os.path.relpath(os.path.dirname(path), SRC).replace(os.sep, ".")
    if pkg != expected:
        errors.append(f"{path}: package '{pkg}' does not match directory '{expected}'")
    names = symbols.setdefault(pkg, set())
    for line in text.splitlines():
        if line.startswith((" ", "\t")):
            continue  # top-level declarations only
        dm = decl_re.match(line)
        if dm:
            names.add(dm.group(2).strip("`"))

nested_ok = set()
for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    for imp in re.findall(r"^import\s+(app\.pulse\.client[\w.]*)", text, re.M):
        if imp == f"{PKG}.R" or imp == f"{PKG}.BuildConfig":
            continue
        parts = imp.split(".")
        ok = False
        # try longest package prefix that exists, then the first symbol after it
        for i in range(len(parts) - 1, 2, -1):
            pkg = ".".join(parts[:i])
            if pkg in symbols:
                ok = parts[i] in symbols[pkg]
                break
        if not ok:
            errors.append(f"{os.path.relpath(path, ROOT)}: unresolved import {imp}")

# ---------- 2/3. resources ----------
res_names = {}
for d in os.listdir(RES):
    full = os.path.join(RES, d)
    rtype = d.split("-")[0]
    for f in os.listdir(full):
        name, ext = os.path.splitext(f)
        if rtype != "values":
            res_names.setdefault(rtype, set()).add(name)
        else:
            text = open(os.path.join(full, f), encoding="utf-8").read()
            for t, n in re.findall(r'<(string|color|dimen|style|bool|integer|plurals|array|string-array)\s+name="([\w.]+)"', text):
                t = {"string-array": "array"}.get(t, t)
                res_names.setdefault(t, set()).add(n.replace(".", "_") if t != "style" else n)

for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    for t, n in re.findall(r"(?<![\w.])R\.(\w+)\.(\w+)", text):
        if n not in res_names.get(t, set()):
            errors.append(f"{os.path.relpath(path, ROOT)}: missing resource R.{t}.{n}")

xml_files = [MANIFEST]
for d, _, files in os.walk(RES):
    xml_files += [os.path.join(d, f) for f in files if f.endswith(".xml")]
for path in xml_files:
    text = open(path, encoding="utf-8").read()
    for t, n in re.findall(r'"@(\w+)/([\w.]+)"', text):
        if t in ("android", "id", "+id"):
            continue
        if n not in res_names.get(t, set()):
            errors.append(f"{os.path.relpath(path, ROOT)}: missing resource @{t}/{n}")

# ---------- 4. manifest classes ----------
manifest = open(MANIFEST, encoding="utf-8").read()
for cls in re.findall(r'android:name="(\.[\w.]+|app\.pulse\.client[\w.]*)"', manifest):
    fq = PKG + cls if cls.startswith(".") else cls
    pkg, name = fq.rsplit(".", 1)
    if name not in symbols.get(pkg, set()):
        errors.append(f"AndroidManifest.xml: class {fq} not found")

# ---------- 5. version catalog ----------
toml = open(os.path.join(ROOT, "gradle/libs.versions.toml"), encoding="utf-8").read()
section = None
aliases = {"libraries": set(), "plugins": set(), "versions": set()}
for line in toml.splitlines():
    s = line.strip()
    if s.startswith("["):
        section = s.strip("[]")
        continue
    m = re.match(r"([\w-]+)\s*=", s)
    if m and section in aliases:
        aliases[section].add(m.group(1).replace("-", "."))
    for ref in re.findall(r'version\.ref\s*=\s*"([\w-]+)"', s):
        if ref.replace("-", ".") not in aliases["versions"]:
            errors.append(f"libs.versions.toml: unknown version.ref {ref}")
for gf in ["build.gradle.kts", "app/build.gradle.kts"]:
    text = open(os.path.join(ROOT, gf), encoding="utf-8").read()
    for alias in re.findall(r"libs\.((?:plugins\.)?[\w.]+)", text):
        if alias.startswith("plugins."):
            if alias[len("plugins."):] not in aliases["plugins"]:
                errors.append(f"{gf}: unknown plugin alias libs.{alias}")
        elif alias not in aliases["libraries"]:
            errors.append(f"{gf}: unknown library alias libs.{alias}")

# ---------- 6. secrets / token logging ----------
for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    rel = os.path.relpath(path, ROOT)
    if re.search(r"Log\.\w\(.*(token|Token)", text):
        errors.append(f"{rel}: looks like a token is being logged")
    if re.search(r"vk1\.a\.[\w-]{20,}", text):
        errors.append(f"{rel}: hard-coded VK token")
    if re.search(r"client_secret|clientSecret", text):
        errors.append(f"{rel}: client secret in sources")

# ---------- 7. unused project imports ----------
for path in kotlin_files():
    text = open(path, encoding="utf-8").read()
    body = re.sub(r"^import .*$", "", text, flags=re.M)
    for imp in re.findall(r"^import\s+([\w.]+)(?:\s+as\s+\w+)?$", text, re.M):
        name = imp.rsplit(".", 1)[1]
        if name == "*":
            continue
        if name in ("getValue", "setValue"):
            continue  # used implicitly by `by` delegation
        if not re.search(r"\b" + re.escape(name) + r"\b", body):
            warnings.append(f"{os.path.relpath(path, ROOT)}: unused import {imp}")

for w in warnings:
    print("WARN ", w)
for e in errors:
    print("ERROR", e)
print(f"\n{len(errors)} error(s), {len(warnings)} warning(s), {sum(1 for _ in kotlin_files())} Kotlin files")
sys.exit(1 if errors else 0)
