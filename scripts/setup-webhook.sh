#!/usr/bin/env bash
# Регистрирует webhook Telegram на уже задеплоенный Worker.
# Использует .env (см. .env.example) — сам файл не коммитится.
set -euo pipefail

if [ -f .env ]; then
  # shellcheck disable=SC1091
  source .env
fi

: "${TELEGRAM_BOT_TOKEN:?Задайте TELEGRAM_BOT_TOKEN в .env}"
: "${TELEGRAM_WEBHOOK_SECRET:?Задайте TELEGRAM_WEBHOOK_SECRET в .env}"
: "${WORKER_URL:?Задайте WORKER_URL в .env (URL воркера после деплоя)}"

WEBHOOK_URL="${WORKER_URL%/}/webhook/${TELEGRAM_WEBHOOK_SECRET}"

echo "Регистрирую webhook: ${WEBHOOK_URL}"

curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"${WEBHOOK_URL}\",\"secret_token\":\"${TELEGRAM_WEBHOOK_SECRET}\",\"allowed_updates\":[\"message\"]}" \
  | tee /dev/stderr | grep -q '"ok":true' && echo "Webhook установлен."
