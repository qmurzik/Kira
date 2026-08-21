import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { qmodsLogin, qmodsGetSubscription, QmodsApiError } from "../../qmods/client";
import { setQmodsLink } from "../../database/qmodsLink";

/**
 * /link <логин> <пароль> — привязывает Telegram к личному кабинету
 * qmods.ru. Пароль используется один раз, чтобы получить session_token
 * от сайта, и никогда не сохраняется. Работает только в личных
 * сообщениях: пароль в истории группового чата — плохая идея.
 */
export async function handleLink(env: Env, message: TgMessage, args: string[]): Promise<void> {
  const client = new TelegramClient(env);

  if (message.chat.type !== "private") {
    await client.sendMessage(
      message.chat.id,
      "Привязку аккаунта делай мне в личных сообщениях, а не в группе — там пароль останется в истории чата 🙈",
      { messageThreadId: message.message_thread_id },
    );
    return;
  }

  const userId = message.from?.id;
  if (!userId) return;

  const [login, ...passwordParts] = args;
  const password = passwordParts.join(" ");

  if (!login || !password) {
    await client.sendMessage(
      message.chat.id,
      "Напиши так: /link логин пароль — это данные от твоего личного кабинета на qmods.ru. После привязки сразу пришли это сообщение с паролём — я не смогу его удалить сама.",
    );
    return;
  }

  try {
    const loginResult = await qmodsLogin(login, password);
    await setQmodsLink(env, userId, loginResult.qmodsUserId, loginResult.qmodsUsername, loginResult.sessionToken);

    const sub = await qmodsGetSubscription(loginResult.sessionToken).catch(() => null);
    const subLine = sub
      ? sub.active
        ? `Подписка активна (${sub.planTitle}), осталось дней: ${sub.daysLeft}.`
        : "Подписка сейчас не активна."
      : "";

    await client.sendMessage(
      message.chat.id,
      `Готово, привязала аккаунт ${loginResult.qmodsUsername} 💜 ${subLine}\nТеперь можешь спрашивать меня про подписку. Удали, пожалуйста, своё сообщение с паролём выше — я его больше не запрашиваю и не храню.`,
    );
  } catch (error) {
    const reason = error instanceof QmodsApiError ? error.message : "не получилось связаться с qmods.ru";
    await client.sendMessage(message.chat.id, `Не вышло привязать аккаунт: ${reason}`);
  }
}
