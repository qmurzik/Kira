#!/bin/bash
# Проверка всех GDScript-файлов на ошибки разбора
cd "$(dirname "$0")/.."
G=${GODOT:-/opt/godot/Godot_v4.3-stable_linux.x86_64}
fail=0
for f in $(find scripts -name "*.gd"); do
  out=$(timeout 60 $G --headless --path . --check-only --script "res://$f" 2>&1 | grep -E "SCRIPT ERROR|ERROR: res|at: " | grep -v "^$")
  if echo "$out" | grep -q "Parse Error"; then
    echo "== $f"; echo "$out" | head -6; fail=1
  fi
done
exit $fail
