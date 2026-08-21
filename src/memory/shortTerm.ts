import type { Env } from "../config/env";
import { numVar } from "../config/env";
import type { ChatRole } from "../ai/types";

export interface ShortTermMessage {
  role: ChatRole;
  content: string;
  ts: number;
}

function memoryKey(chatId: number, userId: number): string {
  return `kira:mem:${chatId}:${userId}`;
}

export async function getShortTermHistory(
  env: Env,
  chatId: number,
  userId: number,
): Promise<ShortTermMessage[]> {
  const raw = await env.KIRA_KV.get(memoryKey(chatId, userId));
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** Добавляет пару сообщений (пользователь + ответ Киры) и обрезает историю до лимита. */
export async function appendExchange(
  env: Env,
  chatId: number,
  userId: number,
  userContent: string,
  assistantContent: string,
): Promise<void> {
  const limit = numVar(env.SHORT_TERM_LIMIT, 12);
  const ttl = numVar(env.SHORT_TERM_TTL_SECONDS, 604800);
  const history = await getShortTermHistory(env, chatId, userId);
  const now = Date.now();

  history.push({ role: "user", content: userContent, ts: now });
  history.push({ role: "assistant", content: assistantContent, ts: now });

  const trimmed = history.slice(-limit);
  await env.KIRA_KV.put(memoryKey(chatId, userId), JSON.stringify(trimmed), {
    expirationTtl: ttl,
  });
}

export async function clearShortTerm(env: Env, chatId: number, userId: number): Promise<void> {
  await env.KIRA_KV.delete(memoryKey(chatId, userId));
}
