import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getUserProfile } from "../../database/users";
import { getQmodsLink } from "../../database/qmodsLink";

export async function handleProfile(env: Env, message: TgMessage): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  if (!userId) return;

  const [profile, qmodsLink] = await Promise.all([getUserProfile(env, userId), getQmodsLink(env, userId)]);
  const qmodsLine = qmodsLink
    ? `Аккаунт QMODS: привязан (${qmodsLink.qmodsUsername})`
    : "Аккаунт QMODS: не привязан — /link логин пароль в личке";

  if (!profile) {
    await client.sendMessage(
      message.chat.id,
      `Пока я о тебе почти ничего не помню 💜 Напиши мне что-нибудь, и я начну запоминать.\n${qmodsLine}`,
      { messageThreadId: message.message_thread_id },
    );
    return;
  }

  const prefs = safeParse(profile.preferencesJson);
  const lines = [
    `Имя: ${profile.firstName ?? "неизвестно"}`,
    profile.username ? `Юзернейм: @${profile.username}` : null,
    `Сообщений с тобой: ${profile.messageCount}`,
    `Помню тебя с: ${new Date(profile.firstSeenAt).toLocaleDateString("ru-RU")}`,
    Object.keys(prefs).length > 0 ? `Заметки: ${JSON.stringify(prefs)}` : null,
    qmodsLine,
  ].filter(Boolean);

  await client.sendMessage(message.chat.id, lines.join("\n"), {
    messageThreadId: message.message_thread_id,
  });
}

function safeParse(json: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(json);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}
