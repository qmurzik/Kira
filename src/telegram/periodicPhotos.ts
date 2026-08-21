import type { Env } from "../config/env";
import { numVar } from "../config/env";
import { TelegramClient } from "./client";
import { getChatsDueForPhoto, markPhotoSent } from "../database/chatSettings";
import { generateKiraPhoto } from "../ai/imageGen";
import { pickPhotoCaption } from "./photoCaptions";
import { logError } from "../database/errorLog";

/**
 * Запускается из cron (scheduled): для чатов с включённой опцией
 * "photos on" и вышедшим интервалом — генерирует и присылает фото Киры
 * в её тему (если назначена) без запроса пользователя.
 */
export async function runPeriodicPhotos(env: Env): Promise<void> {
  const intervalMs = numVar(env.PHOTO_AUTO_INTERVAL_HOURS, 72) * 60 * 60 * 1000;
  const due = await getChatsDueForPhoto(env, intervalMs);
  if (due.length === 0) return;

  const client = new TelegramClient(env);

  for (const chat of due) {
    try {
      const photo = await generateKiraPhoto(env);
      await client.sendPhoto(chat.chatId, photo, {
        caption: pickPhotoCaption(),
        messageThreadId: chat.kiraTopicId ?? undefined,
      });
      await markPhotoSent(env, chat.chatId, Date.now());
    } catch (error) {
      await logError(env, "runPeriodicPhotos", error);
    }
  }
}
