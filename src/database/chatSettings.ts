import type { Env } from "../config/env";

export interface ChatSettings {
  chatId: number;
  kiraTopicId: number | null;
  kiraTopicName: string | null;
  updatedAt: number;
}

interface ChatSettingsRow {
  chat_id: number;
  kira_topic_id: number | null;
  kira_topic_name: string | null;
  updated_at: number;
}

export async function getChatSettings(env: Env, chatId: number): Promise<ChatSettings | null> {
  const row = await env.DB.prepare("SELECT * FROM chat_settings WHERE chat_id = ?")
    .bind(chatId)
    .first<ChatSettingsRow>();
  if (!row) return null;
  return {
    chatId: row.chat_id,
    kiraTopicId: row.kira_topic_id,
    kiraTopicName: row.kira_topic_name,
    updatedAt: row.updated_at,
  };
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
