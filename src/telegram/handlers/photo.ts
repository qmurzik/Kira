import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { generateKiraPhoto } from "../../ai/imageGen";
import { pickPhotoCaption } from "../photoCaptions";
import { isPhotoOnCooldown } from "../middleware/rateLimit";
import { logError } from "../../database/errorLog";

export async function handlePhotoRequest(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  if (await isPhotoOnCooldown(env, userId)) {
    await client.sendMessage(message.chat.id, "Не так часто 🙈 дай мне пару минут.", {
      messageThreadId: message.message_thread_id,
    });
    return;
  }

  try {
    await client.sendChatAction(message.chat.id, message.message_thread_id);
    const photo = await generateKiraPhoto(env);
    await client.sendPhoto(message.chat.id, photo, {
      caption: pickPhotoCaption(),
      messageThreadId: message.message_thread_id,
      replyToMessageId: message.message_id,
    });
  } catch (error) {
    await logError(env, "handlePhotoRequest", error);
    await client
      .sendMessage(message.chat.id, "Не получилось сгенерировать фото 💜 Попробуй чуть позже.", {
        messageThreadId: message.message_thread_id,
      })
      .catch(() => undefined);
  }
}
