import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getQmodsLink } from "../../database/qmodsLink";
import { qmodsUnlinkDevice, QmodsApiError } from "../../qmods/client";
import { setPendingConfirmation, clearPendingConfirmation } from "../pendingConfirm";
import { logError } from "../../database/errorLog";

/** Пользователь попросил отвязать устройство — просим подтверждение, ничего не выполняем. */
export async function requestDeviceUnlink(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  const link = await getQmodsLink(env, userId);
  if (!link) {
    await client.sendMessage(
      message.chat.id,
      "Аккаунт QMODS не привязан, отвязывать нечего. Сначала сделай /link логин пароль в личке.",
      { messageThreadId: message.message_thread_id },
    );
    return;
  }

  await setPendingConfirmation(env, message.chat.id, userId, "unlink_device");
  await client.sendMessage(
    message.chat.id,
    "Точно отвязать устройство от аккаунта QMODS? Это сбросит текущую сессию приложения. Напиши «да» для подтверждения — или просто продолжай общаться, если передумаешь, через 5 минут я забуду про запрос.",
    { messageThreadId: message.message_thread_id },
  );
}

/** Пользователь подтвердил ранее запрошенное действие — выполняем. */
export async function executeConfirmedAction(
  env: Env,
  message: TgMessage,
  action: "unlink_device",
): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  await clearPendingConfirmation(env, message.chat.id, userId);

  if (action === "unlink_device") {
    const link = await getQmodsLink(env, userId);
    if (!link) {
      await client.sendMessage(message.chat.id, "Аккаунт уже не привязан.", {
        messageThreadId: message.message_thread_id,
      });
      return;
    }

    try {
      await qmodsUnlinkDevice(link.sessionToken);
      await client.sendMessage(message.chat.id, "Готово, устройство отвязано от аккаунта QMODS 💜", {
        messageThreadId: message.message_thread_id,
      });
    } catch (error) {
      await logError(env, "executeConfirmedAction:unlink_device", error);
      const reason = error instanceof QmodsApiError ? error.message : "не получилось связаться с qmods.ru";
      await client.sendMessage(message.chat.id, `Не вышло отвязать устройство: ${reason}`, {
        messageThreadId: message.message_thread_id,
      });
    }
  }
}
