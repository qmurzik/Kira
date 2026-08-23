import type { Env } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getAIProvider } from "../../ai";
import { buildMessages } from "../../ai/promptBuilder";
import { loadContext, rememberSeen, rememberExchange } from "../../memory/manager";
import { getUserProfile, mergePreferences } from "../../database/users";
import {
  getGroupHistory,
  appendGroupUserMessage,
  appendGroupAssistantMessage,
} from "../../memory/groupHistory";
import { buildParticipantsNote } from "../../ai/participants";
import { isRateLimited } from "../middleware/rateLimit";
import { logError } from "../../database/errorLog";
import { getQmodsContextLine } from "../../qmods/context";
import { getPendingConfirmation, clearPendingConfirmation } from "../pendingConfirm";
import { executeConfirmedAction, requestDeviceUnlink } from "./deviceAction";
import { handlePhotoRequest } from "./photo";
import { isAffirmative, isDeviceUnlinkRequest, isPhotoRequest } from "../intents";
import { parseKiraReply } from "../../ai/replyParsing";
import { isOwnerId } from "../../config/owner";
import { humanDelayMs, splitIntoBubbles } from "../humanize";

function speakerDisplayName(message: TgMessage): string {
  return message.from?.first_name || message.from?.username || "Кто-то";
}

/**
 * Основной обработчик сообщения, адресованного Кире. Сначала разбирает
 * особые случаи (подтверждение отложенного действия, запрос фото, запрос
 * на отвязку устройства), а если ни один не подошёл — обычный AI-диалог:
 * контекст памяти, AI-провайдер, задержка перед ответом, запись в память.
 *
 * В группах используется общая память на тему форума (не приватная на
 * каждого пользователя) — иначе Кира не видела бы разговор нескольких
 * участников между собой, только свой диалог с каждым по отдельности.
 *
 * @param ambiguousAddressee — сообщение попало в тему Киры "по умолчанию",
 *   без прямого обращения; модель сама решает, отвечать ли (см. SKIP-протокол
 *   в src/ai/replyParsing.ts и src/config/character.ts).
 */
export async function handleAIMessage(
  env: Env,
  message: TgMessage,
  ambiguousAddressee = false,
): Promise<void> {
  const client = new TelegramClient(env);
  const userId = message.from?.id;
  const text = message.text?.trim();
  if (!userId || !text) return;

  const pending = await getPendingConfirmation(env, message.chat.id, userId);
  if (pending) {
    if (isAffirmative(text)) {
      await executeConfirmedAction(env, message, pending);
    } else {
      await clearPendingConfirmation(env, message.chat.id, userId);
      await client.sendMessage(message.chat.id, "Хорошо, отменила 💜", {
        messageThreadId: message.message_thread_id,
      });
    }
    return;
  }

  if (isDeviceUnlinkRequest(text)) {
    await requestDeviceUnlink(env, message);
    return;
  }

  if (isPhotoRequest(text)) {
    await handlePhotoRequest(env, message);
    return;
  }

  if (await isRateLimited(env, userId)) {
    return; // тихо игнорируем, чтобы не спамить и не жечь бесплатный лимит
  }

  await rememberSeen(env, userId, message.from?.username ?? null, message.from?.first_name ?? null);

  const isGroup = message.chat.type !== "private";
  const threadId = message.message_thread_id;
  const speakerName = speakerDisplayName(message);

  if (isGroup) {
    // Записываем реплику сразу — даже если Кира решит промолчать (SKIP),
    // она должна помнить, что было сказано в теме. Не даём сбою записи
    // сорвать сам ответ пользователю.
    await appendGroupUserMessage(env, message.chat.id, threadId, userId, speakerName, text).catch(
      (error) => logError(env, "appendGroupUserMessage", error),
    );
  }

  try {
    await client.sendChatAction(message.chat.id, threadId);

    const qmodsContextLine = await getQmodsContextLine(env, userId).catch(() => null);
    // Особый тон — только в личке с создателем, не при обращениях в группе,
    // чтобы это не выглядело странно на глазах остального сообщества.
    const isOwner = !isGroup && isOwnerId(env, userId);
    const provider = getAIProvider(env);

    let profile;
    let history;
    let participantsNote: string | null = null;
    let userMessageForPrompt = text;

    if (isGroup) {
      profile = await getUserProfile(env, userId);
      const groupHistory = await getGroupHistory(env, message.chat.id, threadId);
      // Последняя запись — только что добавленная реплика этого же пользователя, не нужна как "прошлая история".
      history = groupHistory.slice(0, -1);
      participantsNote = await buildParticipantsNote(env, history, userId).catch(() => null);
      userMessageForPrompt = `${speakerName}: ${text}`;
    } else {
      const context = await loadContext(env, message.chat.id, userId);
      profile = context.profile;
      history = context.history;
    }

    const prompt = buildMessages(
      profile,
      history,
      userMessageForPrompt,
      qmodsContextLine,
      ambiguousAddressee,
      isOwner,
      participantsNote,
    );
    const rawReply = await provider.generate(prompt);
    const { skip, visibleText, note } = parseKiraReply(rawReply);

    if (note) {
      await mergePreferences(env, userId, note).catch((error) => logError(env, "mergePreferences", error));
    }

    if (skip || !visibleText) {
      return; // модель решила, что обращались не к ней — молчим
    }

    // Иногда шлём длинный ответ парой сообщений подряд + разброс задержки —
    // меньше похоже на бота с фиксированным таймером.
    for (const [i, bubble] of splitIntoBubbles(visibleText).entries()) {
      const delayMs = humanDelayMs(env);
      if (delayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
      if (i > 0) {
        await client.sendChatAction(message.chat.id, threadId).catch(() => undefined);
      }
      await client.sendMessage(message.chat.id, bubble, {
        messageThreadId: threadId,
        replyToMessageId: i === 0 ? message.message_id : undefined,
      });
    }

    if (isGroup) {
      await appendGroupAssistantMessage(env, message.chat.id, threadId, visibleText);
    } else {
      await rememberExchange(env, message.chat.id, userId, text, visibleText);
    }
  } catch (error) {
    await logError(env, "handleAIMessage", error);
    await client
      .sendMessage(
        message.chat.id,
        "Что-то пошло не так на моей стороне 💜 Попробуй ещё раз чуть позже.",
        { messageThreadId: threadId },
      )
      .catch(() => undefined);
  }
}
