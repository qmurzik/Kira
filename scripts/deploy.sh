#!/usr/bin/env bash
# Полный автоматизированный деплой Киры: миграции D1 -> деплой воркера -> webhook.
set -euo pipefail

echo "1/3 Применяю миграции D1..."
npx wrangler d1 migrations apply kira-db --remote

echo "2/3 Деплою Worker..."
npx wrangler deploy

echo "3/3 Регистрирую webhook Telegram..."
bash scripts/setup-webhook.sh

echo "Готово. Кира развёрнута 💜"
