import type { Env } from "./config/env";
import { routeUpdate } from "./telegram/router";
import type { TgUpdate } from "./telegram/types";
import { pruneErrorLog } from "./database/errorLog";
import { logError } from "./database/errorLog";
import { TelegramClient } from "./telegram/client";
import { runPeriodicPhotos } from "./telegram/periodicPhotos";
import { runProactiveMessage } from "./telegram/proactiveMessages";

const ERROR_LOG_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000; // 30 дней
const DAILY_MAINTENANCE_CRON = "0 3 * * *";

// Секрет, вставленный вручную в URL/дашборд, может обрасти пробелом или
// переводом строки — сравниваем по обрезанному значению, чтобы не спотыкаться
// об это на ровном месте.
function normalizeSecret(value: string | null | undefined): string {
  return (value ?? "").trim();
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response("Кира на связи 💜", { status: 200 });
    }

    const expectedSecret = normalizeSecret(env.TELEGRAM_WEBHOOK_SECRET);
    const expectedPath = `/webhook/${expectedSecret}`;
    if (url.pathname === expectedPath && request.method === "POST") {
      // Доп. проверка секрета Telegram (см. setWebhook secret_token).
      const secretHeader = normalizeSecret(request.headers.get("X-Telegram-Bot-Api-Secret-Token"));
      if (!expectedSecret || secretHeader !== expectedSecret) {
        return new Response("Forbidden", { status: 403 });
      }

      let update: TgUpdate;
      try {
        update = await request.json<TgUpdate>();
      } catch {
        return new Response("Bad Request", { status: 400 });
      }

      // Отвечаем Telegram сразу, а обработку продолжаем в фоне —
      // так соблюдается и таймаут вебхука, и требуемая задержка ответа.
      ctx.waitUntil(
        routeUpdate(env, update).catch((error) => logError(env, "fetch:webhook", error)),
      );
      return new Response("OK", { status: 200 });
    }

    // Разовая регистрация webhook без необходимости передавать токен бота
    // куда-либо ещё: доступ к этому эндпоинту защищён тем же секретом, что
    // и сам webhook (его знает только владелец воркера, задавший секрет).
    if (url.pathname === "/setup-webhook" && request.method === "GET") {
      const providedSecret = normalizeSecret(url.searchParams.get("secret"));
      if (!expectedSecret || providedSecret !== expectedSecret) {
        // Ничего секретного не логируем — только длины и служебный признак,
        // чтобы можно было отличить "не тот секрет" от "лишний пробел/перенос строки".
        await logError(
          env,
          "setup-webhook-forbidden",
          `secret_configured=${Boolean(expectedSecret)} provided_len=${providedSecret.length} expected_len=${expectedSecret.length} raw_provided_len=${(url.searchParams.get("secret") ?? "").length}`,
        );
        return new Response("Forbidden", { status: 403 });
      }

      const webhookUrl = `${url.origin}/webhook/${expectedSecret}`;
      try {
        const client = new TelegramClient(env);
        await client.setWebhook(webhookUrl, expectedSecret);
        return new Response(`Webhook установлен: ${webhookUrl}`, { status: 200 });
      } catch (error) {
        await logError(env, "setup-webhook", error);
        const message = error instanceof Error ? error.message : String(error);
        return new Response(`Ошибка установки webhook: ${message}`, { status: 500 });
      }
    }

    return new Response("Not Found", { status: 404 });
  },

  async scheduled(event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    if (event.cron === DAILY_MAINTENANCE_CRON) {
      // Ежедневная гигиена: чистим старый лог ошибок.
      // Короткая память в KV самоочищается через TTL, долговременная — по /clear_memory.
      ctx.waitUntil(
        pruneErrorLog(env, ERROR_LOG_MAX_AGE_MS).catch((error) =>
          logError(env, "scheduled", error),
        ),
      );
      // Периодическая отправка фото Киры в чаты, где это включено (/settings photos on).
      ctx.waitUntil(
        runPeriodicPhotos(env).catch((error) => logError(env, "scheduled:photos", error)),
      );
      return;
    }

    // Остальные срабатывания (несколько раз в день) — шанс, что Кира сама
    // напишет создателю первой, без повода (см. src/telegram/proactiveMessages.ts).
    ctx.waitUntil(
      runProactiveMessage(env).catch((error) => logError(env, "scheduled:proactive", error)),
    );
  },
};
