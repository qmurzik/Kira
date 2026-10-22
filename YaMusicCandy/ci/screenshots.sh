#!/usr/bin/env bash
# Installs the module APK on the emulator and screenshots every settings page in dark and light mode.
set -u
APK="$1"
OUT="$2"
PKG=com.qmurzik.yacandy
mkdir -p "$OUT"
adb install -r "$APK"
for mode in yes no; do
  adb shell cmd uimode night "$mode"
  sleep 2
  for page in home colors fonts shape clean player behaviour experiments more; do
    adb shell am force-stop "$PKG"
    if [ "$page" = home ]; then
      adb shell am start -W -n "$PKG/.ui.MainActivity" > /dev/null
    else
      adb shell am start -W -n "$PKG/.ui.MainActivity" --es yume_page "$page" > /dev/null
    fi
    sleep 3
    name="$([ "$mode" = yes ] && echo dark || echo light)_${page}"
    adb exec-out screencap -p > "$OUT/${name}_1.png"
    adb shell input swipe 540 1900 540 600 500
    sleep 2
    adb exec-out screencap -p > "$OUT/${name}_2.png"
  done
done
# A bottom sheet: the font list.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.ui.MainActivity" --es yume_page fonts > /dev/null
sleep 3
adb shell input tap 540 900
sleep 2
adb exec-out screencap -p > "$OUT/light_fonts_sheet.png"
# Back: from a section page it must return home, from home it must leave the app.
adb shell cmd uimode night yes
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.ui.MainActivity" --es yume_page player > /dev/null
sleep 3
adb shell input keyevent KEYCODE_BACK
sleep 2
adb exec-out screencap -p > "$OUT/back_from_page.png"
{
  echo "after back from page:"; adb shell dumpsys activity activities | grep -m1 -E "ResumedActivity|topResumedActivity"
  adb shell input keyevent KEYCODE_BACK
  sleep 2
  echo "after back from home:"; adb shell dumpsys activity activities | grep -m1 -E "ResumedActivity|topResumedActivity"
} > "$OUT/back_test.txt" 2>&1
cat "$OUT/back_test.txt"
adb logcat -d -s AndroidRuntime:E > "$OUT/crashes.txt" || true
ls -la "$OUT"
