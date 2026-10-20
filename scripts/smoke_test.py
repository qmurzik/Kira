#!/usr/bin/env python3
"""Emulator smoke test, run in CI: install, cold-start timing, login screen, a short UI flow, crash check.

Usage: smoke_test.py <apk> <out_dir>
Fails (exit 1) if the app crashes or the login screen does not appear.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, OUT = sys.argv[1], sys.argv[2]
PKG = "app.pulse.client"
ACTIVITY = f"{PKG}/.MainActivity"
os.makedirs(OUT, exist_ok=True)
failures = []


def adb(*args, check=False, timeout=120):
    r = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed: {r.stderr}")
    return r.stdout


def dump_ui(name):
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    local = os.path.join(OUT, f"{name}.xml")
    adb("pull", "/sdcard/ui.xml", local)
    xml = open(local, encoding="utf-8", errors="replace").read() if os.path.exists(local) else ""
    adb("shell", "screencap", "-p", "/sdcard/s.png")
    adb("pull", "/sdcard/s.png", os.path.join(OUT, f"{name}.png"))
    nodes = []
    try:
        root = ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
        for n in root.iter("node"):
            nodes.append(n.attrib)
    except Exception as e:  # noqa: BLE001
        print(f"[{name}] cannot parse UI dump: {e}")
    texts = [n.get("text") or n.get("content-desc") for n in nodes if (n.get("text") or n.get("content-desc"))]
    print(f"[{name}] visible texts: {texts}")
    return nodes


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node["bounds"]))
    return (x1 + x2) // 2, (y1 + y2) // 2


def crashes():
    # The crash buffer is not available on every API level; scan the main log as well.
    log = adb("logcat", "-d", "-b", "crash") + adb("logcat", "-d", "-s", "AndroidRuntime:E")
    lines = log.splitlines()
    hit = any(("Process: " + PKG) in l for l in lines)
    return [l for l in lines if "AndroidRuntime" in l or PKG in l] if hit else []


SDK = int(adb("shell", "getprop", "ro.build.version.sdk").strip() or 0)
print("SDK:", SDK)
adb("logcat", "-c")
# -g (grant runtime permissions) exists only on API 23+.
install = ["install", "-r", "-g", APK] if SDK >= 23 else ["install", "-r", APK]
res = adb(*install, timeout=300)
print(res.strip())
if "Success" not in res:
    raise SystemExit("install failed")

# Cold start: force-stop + am start -W, several runs.
times = []
for i in range(5):
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    out = adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    m = re.search(r"TotalTime:\s*(\d+)", out)
    if m:
        times.append(int(m.group(1)))
    time.sleep(2)
print(f"COLD_START_MS runs={times} median={sorted(times)[len(times) // 2] if times else 'n/a'}")

time.sleep(4)
nodes = dump_ui("01_login")
texts = " ".join((n.get("text") or "") for n in nodes)
if "Войти через VK ID" not in texts:
    failures.append("login screen not found")

# Type an app id and press the button: the VK ID page must open in the WebView without crashing.
edit = next((n for n in nodes if n.get("class") == "android.widget.EditText"), None)
button = next((n for n in nodes if n.get("text") == "Войти через VK ID"), None)
if edit and button:
    adb("shell", "input", "tap", *map(str, center(edit)))
    time.sleep(1)
    adb("shell", "input", "text", "51234567")
    time.sleep(1)
    adb("shell", "input", "keyevent", "111")  # ESC: hide keyboard
    time.sleep(1)
    nodes = dump_ui("02_filled")
    button = next((n for n in nodes if n.get("text") == "Войти через VK ID"), button)
    adb("shell", "input", "tap", *map(str, center(button)))
    time.sleep(12)
    dump_ui("03_vkid_webview")
    # Back to the form.
    adb("shell", "input", "keyevent", "4")
    time.sleep(2)
    dump_ui("04_back")
else:
    failures.append("login form fields not found")

# Rotate (configChanges) and background/foreground (lifecycle observers, lock).
adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
adb("shell", "settings", "put", "system", "user_rotation", "1")
time.sleep(2)
adb("shell", "settings", "put", "system", "user_rotation", "0")
adb("shell", "input", "keyevent", "3")
time.sleep(2)
adb("shell", "am", "start", "-n", ACTIVITY)
time.sleep(3)
dump_ui("05_resumed")

ps = adb("shell", "ps")
pid = next((l.split()[1] for l in ps.splitlines() if l.strip().endswith(PKG)), "")
print("process alive:", bool(pid), pid)
if not pid:
    failures.append("process is not running at the end")

c = crashes()
open(os.path.join(OUT, "logcat.txt"), "w").write(adb("logcat", "-d", timeout=120))
if c:
    print("CRASH LOG:\n" + "\n".join(c[:80]))
    failures.append("crash detected")

print("SMOKE_RESULT", "FAIL " + "; ".join(failures) if failures else "OK")
sys.exit(1 if failures else 0)
