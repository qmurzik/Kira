import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getQmodsLink, clearQmodsLink } from "../../database/qmodsLink";
import { qmodsLogout } from "../../qmods/client";

export async function handleUnlink(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  const link = await getQmodsLink(env, userId);
  if (!link) {
    await client.sendMessage(message.chat.id, "У тебя и не было привязанного аккаунта QMODS 💜", {
      messageThreadId: message.message_thread_id,
    });
    return;
  }

  await qmodsLogout(link.sessionToken);
  await clearQmodsLink(env, userId);
  await client.sendMessage(message.chat.id, "Отвязала аккаунт QMODS.", {
    messageThreadId: message.message_thread_id,
  });
}
