import type { Env } from "../config/env";

const TTL_SECONDS = 300; // 5 минут на подтверждение

export type PendingAction = "unlink_device";

function key(chatId: number, userId: number): string {
  return `kira:confirm:${chatId}:${userId}`;
}

export async function setPendingConfirmation(
  env: Env,
  chatId: number,
  userId: number,
  action: PendingAction,
): Promise<void> {
  await env.KIRA_KV.put(key(chatId, userId), action, { expirationTtl: TTL_SECONDS });
}

export async function getPendingConfirmation(
  env: Env,
  chatId: number,
  userId: number,
): Promise<PendingAction | null> {
  const value = await env.KIRA_KV.get(key(chatId, userId));
  return value as PendingAction | null;
}

export async function clearPendingConfirmation(env: Env, chatId: number, userId: number): Promise<void> {
  await env.KIRA_KV.delete(key(chatId, userId));
}
