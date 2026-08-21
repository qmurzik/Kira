import type { Env } from "../config/env";
import { TelegramClient } from "./client";

export function getBotId(env: Env): number {
  const id = Number(env.TELEGRAM_BOT_TOKEN.split(":")[0]);
  if (!Number.isFinite(id)) {
    throw new Error("TELEGRAM_BOT_TOKEN имеет неверный формат");
  }
  return id;
}

const BOT_USERNAME_KV_KEY = "kira:bot:username";

/** Username бота, нужен для детекции @упоминаний. Кэшируется в KV на сутки. */
export async function getBotUsername(env: Env): Promise<string | undefined> {
  const cached = await env.KIRA_KV.get(BOT_USERNAME_KV_KEY);
  if (cached) return cached;

  const client = new TelegramClient(env);
  const me = await client.getMe();
  if (me.username) {
    await env.KIRA_KV.put(BOT_USERNAME_KV_KEY, me.username, { expirationTtl: 86400 });
  }
  return me.username;
}
