import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getChatSettings, setKiraTopic } from "../../database/chatSettings";

/**
 * /settings              — показать текущие настройки чата;
 * /settings set_topic    — (только админы, внутри нужной темы форума)
 *                           назначить текущую тему как "тему Киры".
 */
export async function handleSettings(env: Env, message: TgMessage, args: string[]): Promise<void> {
  const client = new TelegramClient(env);
  const threadId = message.message_thread_id;

  if (message.chat.type === "private") {
    await client.sendMessage(
      message.chat.id,
      "В личных сообщениях я отвечаю всегда, настраивать здесь нечего 💜",
    );
    return;
  }

  if (args[0] === "set_topic") {
    const userId = message.from?.id;
    if (!userId) return;

    const status = await client.getChatMemberStatus(message.chat.id, userId);
    if (status !== "administrator" && status !== "creator") {
      await client.sendMessage(
        message.chat.id,
        "Назначать мою тему могут только администраторы группы.",
        { messageThreadId: threadId },
      );
      return;
    }

    if (!threadId) {
      await client.sendMessage(
        message.chat.id,
        "Эту команду нужно отправить внутри темы форума, которую хотите закрепить за мной.",
        { messageThreadId: threadId },
      );
      return;
    }

    await setKiraTopic(env, message.chat.id, threadId, null);
    await client.sendMessage(message.chat.id, "Готово 💜 Теперь я отвечаю здесь.", {
      messageThreadId: threadId,
    });
    return;
  }

  const settings = await getChatSettings(env, message.chat.id);
  const topicInfo = settings?.kiraTopicId
    ? `Моя тема: id ${settings.kiraTopicId}${settings.kiraTopicName ? ` ("${settings.kiraTopicName}")` : ""}`
    : "Моя тема ещё не назначена. Создайте тему форума и отправьте в ней /settings set_topic (нужны права администратора), либо назовите тему так, чтобы она содержала слово из KIRA_TOPIC_NAME — я подхвачу её автоматически.";

  await client.sendMessage(message.chat.id, topicInfo, { messageThreadId: threadId });
}
