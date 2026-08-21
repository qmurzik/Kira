import type { Env } from "../../config/env";
import type { TgMessage } from "../types";
import { matchesKiraTopicName } from "../middleware/topicFilter";
import { setKiraTopic } from "../../database/chatSettings";

/**
 * Автоматизация: если название новой темы форума совпадает с KIRA_TOPIC_NAME
 * (например "💜 Кира AI"), запоминаем её как рабочую тему без ручной команды.
 */
export async function handleForumTopicCreated(env: Env, message: TgMessage): Promise<void> {
  const created = message.forum_topic_created;
  if (!created || !message.message_thread_id) return;

  if (matchesKiraTopicName(created.name, env.KIRA_TOPIC_NAME)) {
    await setKiraTopic(env, message.chat.id, message.message_thread_id, created.name);
  }
}
