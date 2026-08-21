import type { Env } from "../../config/env";
import { boolVar } from "../../config/env";
import type { TgMessage } from "../types";
import { getChatSettings } from "../../database/chatSettings";
import { isBotMentioned, isReplyToBot, isAddressedByName } from "../mention";

interface TopicDecision {
  shouldRespond: boolean;
  reason: string;
}

/**
 * Решает, должна ли Кира обработать сообщение из группы:
 *  - личные сообщения — всегда;
 *  - внутри выделенной темы форума ("💜 Кира AI") — всегда;
 *  - в любом другом месте группы — только если её позвали (упоминание,
 *    ответ на её сообщение, обращение по имени), если STRICT_TOPIC_ONLY=false;
 *  - если STRICT_TOPIC_ONLY=true — строго только выделенная тема.
 */
export async function decideShouldRespond(
  env: Env,
  message: TgMessage,
  botId: number,
  botUsername: string | undefined,
): Promise<TopicDecision> {
  if (message.chat.type === "private") {
    return { shouldRespond: true, reason: "private_chat" };
  }

  const settings = await getChatSettings(env, message.chat.id);
  const kiraTopicId = settings?.kiraTopicId ?? null;

  if (kiraTopicId !== null && message.message_thread_id === kiraTopicId) {
    return { shouldRespond: true, reason: "kira_topic" };
  }

  const strict = boolVar(env.STRICT_TOPIC_ONLY, false);
  if (strict) {
    return { shouldRespond: false, reason: "strict_topic_only" };
  }

  const called =
    isBotMentioned(message, botUsername) ||
    isReplyToBot(message, botId) ||
    isAddressedByName(message);

  return { shouldRespond: called, reason: called ? "called_by_name" : "ignored_other_topic" };
}

/** Автоматически запоминает тему форума, если её название совпадает с KIRA_TOPIC_NAME. */
export function matchesKiraTopicName(topicName: string, configuredName: string): boolean {
  return topicName.toLowerCase().includes(configuredName.toLowerCase());
}
