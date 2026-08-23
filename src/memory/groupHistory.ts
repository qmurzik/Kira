import type { Env } from "../config/env";
import { numVar } from "../config/env";
import type { ChatRole } from "../ai/types";

/**
 * Общая память на тему форума (в отличие от src/memory/shortTerm.ts,
 * который хранит приватную историю на пару чат+пользователь). Без этого
 * Кира видела только свой личный диалог с каждым отдельным человеком и не
 * могла следить за разговором нескольких участников в одной теме.
 */
export interface GroupHistoryEntry {
  role: ChatRole;
  content: string;
  speakerName?: string;
  speakerId?: number;
  ts: number;
}

function groupKey(chatId: number, threadId: number | undefined): string {
  return `kira:group-mem:${chatId}:${threadId ?? "general"}`;
}

export async function getGroupHistory(
  env: Env,
  chatId: number,
  threadId: number | undefined,
): Promise<GroupHistoryEntry[]> {
  const raw = await env.KIRA_KV.get(groupKey(chatId, threadId));
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

async function appendEntry(
  env: Env,
  chatId: number,
  threadId: number | undefined,
  entry: GroupHistoryEntry,
): Promise<void> {
  const limit = numVar(env.GROUP_HISTORY_LIMIT, 24);
  const ttl = numVar(env.SHORT_TERM_TTL_SECONDS, 604800);
  const history = await getGroupHistory(env, chatId, threadId);
  history.push(entry);
  const trimmed = history.slice(-limit);
  await env.KIRA_KV.put(groupKey(chatId, threadId), JSON.stringify(trimmed), { expirationTtl: ttl });
}

/** Записывает сообщение участника — вызывается всегда, даже если Кира решит промолчать (SKIP). */
export async function appendGroupUserMessage(
  env: Env,
  chatId: number,
  threadId: number | undefined,
  speakerId: number,
  speakerName: string,
  content: string,
): Promise<void> {
  await appendEntry(env, chatId, threadId, {
    role: "user",
    content,
    speakerId,
    speakerName,
    ts: Date.now(),
  });
}

export async function appendGroupAssistantMessage(
  env: Env,
  chatId: number,
  threadId: number | undefined,
  content: string,
): Promise<void> {
  await appendEntry(env, chatId, threadId, { role: "assistant", content, ts: Date.now() });
}
