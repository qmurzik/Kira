import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getChatSettings, setKiraTopic, setPhotosEnabled } from "../../database/chatSettings";

/**
 * /settings                 — показать текущие настройки чата;
 * /settings set_topic       — (только админы, внутри нужной темы форума)
 *                              назначить текущую тему как "тему Киры";
 * /settings photos on|off   — включить/выключить периодическую отправку
 *                              фото Киры в этот чат (в группе — только админы).
 */
export async function handleSettings(env: Env, message: TgMessage, args: string[]): Promise<void> {
  const client = new TelegramClient(env);
  const threadId = message.message_thread_id;
  const isGroup = message.chat.type !== "private";

  if (args[0] === "photos" && (args[1] === "on" || args[1] === "off")) {
    if (isGroup) {
      const userId = message.from?.id;
      if (!userId) return;
      const status = await client.getChatMemberStatus(message.chat.id, userId);
      if (status !== "administrator" && status !== "creator") {
        await client.sendMessage(message.chat.id, "Включать/выключать фото могут только администраторы.", {
          messageThreadId: threadId,
        });
        return;
      }
    }

    const enabled = args[1] === "on";
    await setPhotosEnabled(env, message.chat.id, enabled);
    await client.sendMessage(
      message.chat.id,
      enabled ? "Буду иногда сама присылать сюда фото 💜" : "Хорошо, больше не буду присылать фото сама.",
      { messageThreadId: threadId },
    );
    return;
  }

  if (!isGroup) {
    await client.sendMessage(
      message.chat.id,
      "В личных сообщениях доступна только настройка /settings photos on|off — остальное настраивать здесь нечего 💜",
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
  const photosInfo = `Периодические фото: ${settings?.photosEnabled ? "включены" : "выключены"} (/settings photos on|off)`;

  await client.sendMessage(message.chat.id, `${topicInfo}\n${photosInfo}`, { messageThreadId: threadId });
}
