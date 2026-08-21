import type { Env } from "../config/env";

/** Пишет ошибку в D1 и в консоль воркера. Никогда не бросает исключение сама. */
export async function logError(env: Env, context: string, error: unknown): Promise<void> {
  const message = error instanceof Error ? `${error.message}\n${error.stack ?? ""}` : String(error);
  console.error(`[${context}]`, message);
  try {
    await env.DB.prepare(
      "INSERT INTO error_log (context, message, created_at) VALUES (?, ?, ?)",
    )
      .bind(context, message.slice(0, 2000), Date.now())
      .run();
  } catch (dbError) {
    console.error("[logError] не удалось записать в error_log", dbError);
  }
}

/** Удаляет записи лога старше maxAgeMs (используется в cron-очистке). */
export async function pruneErrorLog(env: Env, maxAgeMs: number): Promise<void> {
  const cutoff = Date.now() - maxAgeMs;
  await env.DB.prepare("DELETE FROM error_log WHERE created_at < ?").bind(cutoff).run();
}
