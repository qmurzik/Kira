import type { Env } from "./config/env";
import { routeUpdate } from "./telegram/router";
import type { TgUpdate } from "./telegram/types";
import { pruneErrorLog } from "./database/errorLog";
import { logError } from "./database/errorLog";

const ERROR_LOG_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000; // 30 дней

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response("Кира на связи 💜", { status: 200 });
    }

    const expectedPath = `/webhook/${env.TELEGRAM_WEBHOOK_SECRET}`;
    if (url.pathname === expectedPath && request.method === "POST") {
      // Доп. проверка секрета Telegram (см. setWebhook secret_token).
      const secretHeader = request.headers.get("X-Telegram-Bot-Api-Secret-Token");
      if (secretHeader !== env.TELEGRAM_WEBHOOK_SECRET) {
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

    return new Response("Not Found", { status: 404 });
  },

  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    // Ежедневная гигиена: чистим старый лог ошибок.
    // Короткая память в KV самоочищается через TTL, долговременная — по /clear_memory.
    ctx.waitUntil(
      pruneErrorLog(env, ERROR_LOG_MAX_AGE_MS).catch((error) =>
        logError(env, "scheduled", error),
      ),
    );
  },
};
