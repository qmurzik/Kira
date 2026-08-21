import type { Env } from "../../config/env";
import { KIRA_GREETING } from "../../config/character";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";

export async function handleStart(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  await client.sendMessage(message.chat.id, KIRA_GREETING, {
    messageThreadId: message.message_thread_id,
  });
}
