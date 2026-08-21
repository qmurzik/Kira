import type { Env } from "../config/env";

export interface ChatSettings {
  chatId: number;
  kiraTopicId: number | null;
  kiraTopicName: string | null;
  photosEnabled: boolean;
  lastPhotoSentAt: number;
  updatedAt: number;
}

interface ChatSettingsRow {
  chat_id: number;
  kira_topic_id: number | null;
  kira_topic_name: string | null;
  photos_enabled: number;
  last_photo_sent_at: number;
  updated_at: number;
}

function rowToSettings(row: ChatSettingsRow): ChatSettings {
  return {
    chatId: row.chat_id,
    kiraTopicId: row.kira_topic_id,
    kiraTopicName: row.kira_topic_name,
    photosEnabled: row.photos_enabled === 1,
    lastPhotoSentAt: row.last_photo_sent_at,
    updatedAt: row.updated_at,
  };
}

export async function getChatSettings(env: Env, chatId: number): Promise<ChatSettings | null> {
  const row = await env.DB.prepare("SELECT * FROM chat_settings WHERE chat_id = ?")
    .bind(chatId)
    .first<ChatSettingsRow>();
  return row ? rowToSettings(row) : null;
}

export async function setKiraTopic(
  env: Env,
  chatId: number,
  topicId: number,
  topicName: string | null,
): Promise<void> {
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO chat_settings (chat_id, kira_topic_id, kira_topic_name, updated_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(chat_id) DO UPDATE SET
       kira_topic_id = excluded.kira_topic_id,
       kira_topic_name = excluded.kira_topic_name,
       updated_at = excluded.updated_at`,
  )
    .bind(chatId, topicId, topicName, now)
    .run();
}

export async function setPhotosEnabled(env: Env, chatId: number, enabled: boolean): Promise<void> {
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO chat_settings (chat_id, photos_enabled, updated_at)
     VALUES (?, ?, ?)
     ON CONFLICT(chat_id) DO UPDATE SET
       photos_enabled = excluded.photos_enabled,
       updated_at = excluded.updated_at`,
  )
    .bind(chatId, enabled ? 1 : 0, now)
    .run();
}

export async function markPhotoSent(env: Env, chatId: number, sentAt: number): Promise<void> {
  await env.DB.prepare("UPDATE chat_settings SET last_photo_sent_at = ? WHERE chat_id = ?")
    .bind(sentAt, chatId)
    .run();
}

/** Чаты, где включена периодическая отправка фото и пора слать следующее. */
export async function getChatsDueForPhoto(env: Env, minIntervalMs: number): Promise<ChatSettings[]> {
  const cutoff = Date.now() - minIntervalMs;
  const { results } = await env.DB.prepare(
    "SELECT * FROM chat_settings WHERE photos_enabled = 1 AND last_photo_sent_at < ?",
  )
    .bind(cutoff)
    .all<ChatSettingsRow>();
  return (results ?? []).map(rowToSettings);
}
