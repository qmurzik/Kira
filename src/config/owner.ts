import type { Env } from "./env";

/** true, если это Telegram-аккаунт создателя Киры (см. OWNER_TELEGRAM_ID). */
export function isOwnerId(env: Env, userId: number | undefined): boolean {
  if (!userId) return false;
  const ownerId = Number(env.OWNER_TELEGRAM_ID);
  return Number.isFinite(ownerId) && ownerId > 0 && userId === ownerId;
}
