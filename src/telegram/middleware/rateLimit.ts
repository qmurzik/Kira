import type { Env } from "../../config/env";
import { numVar } from "../../config/env";

/**
 * Ограничивает число ответов ИИ на пользователя в час (защита от спама и
 * от перерасхода бесплатного лимита нейронов). Используется скользящее окно
 * на базе KV-счётчика с TTL в один час.
 */
export async function isRateLimited(env: Env, userId: number): Promise<boolean> {
  const limit = numVar(env.RATE_LIMIT_PER_HOUR, 30);
  const key = `kira:rl:${userId}`;
  const current = Number((await env.KIRA_KV.get(key)) ?? "0");

  if (current >= limit) return true;

  await env.KIRA_KV.put(key, String(current + 1), { expirationTtl: 3600 });
  return false;
}
