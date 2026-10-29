#!/bin/bash
# Запуск автотестов в безголовом режиме. Код выхода 0 — всё хорошо.
cd "$(dirname "$0")/.."
G=${GODOT:-/opt/godot/Godot_v4.3-stable_linux.x86_64}
LOG=${1:-/tmp/kira_tests.log}
timeout 1800 $G --headless --path . res://tests/test_runner.tscn > "$LOG" 2>&1
code=$?
grep -E "^—|✗|Проверок|РЕЗУЛЬТАТ|реплик|SCRIPT ERROR" "$LOG"
echo "exit=$code"
exit $code
