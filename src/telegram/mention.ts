import type { TgMessage } from "./types";

/** Проверяет, упомянут ли бот через @username в тексте сообщения. */
export function isBotMentioned(message: TgMessage, botUsername: string | undefined): boolean {
  if (!botUsername || !message.text || !message.entities) return false;
  const mention = `@${botUsername}`.toLowerCase();
  return message.entities.some((entity) => {
    if (entity.type !== "mention") return false;
    const text = message.text!.slice(entity.offset, entity.offset + entity.length).toLowerCase();
    return text === mention;
  });
}

/** Проверяет, является ли сообщение ответом на сообщение бота. */
export function isReplyToBot(message: TgMessage, botId: number): boolean {
  return message.reply_to_message?.from?.id === botId;
}

/** Простая эвристика прямого обращения по имени, без @упоминания ("кира, ...", "кира?"). */
export function isAddressedByName(message: TgMessage): boolean {
  if (!message.text) return false;
  return /^\s*кир[ауыи]?[\s,!?.:]/i.test(message.text) || /кир[ауыи]?[,!?]/i.test(message.text);
}
