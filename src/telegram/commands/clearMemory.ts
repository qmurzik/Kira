import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { clearMemory } from "../../memory/manager";

export async function handleClearMemory(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  await clearMemory(env, message.chat.id, userId);
  await client.sendMessage(
    message.chat.id,
    "Готово, я всё забыла 💜 Начнём знакомство заново, если захочешь.",
    { messageThreadId: message.message_thread_id },
  );
}
