import type { Env } from "../../config/env";
import { numVar } from "../../config/env";
import { TelegramClient } from "../client";
import type { TgMessage } from "../types";
import { getAIProvider } from "../../ai";
import { buildMessages } from "../../ai/promptBuilder";
import { loadContext, rememberSeen, rememberExchange } from "../../memory/manager";
import { isRateLimited } from "../middleware/rateLimit";
import { logError } from "../../database/errorLog";
import { getQmodsContextLine } from "../../qmods/context";
import { getPendingConfirmation, clearPendingConfirmation } from "../pendingConfirm";
import { executeConfirmedAction, requestDeviceUnlink } from "./deviceAction";
import { handlePhotoRequest } from "./photo";
import { isAffirmative, isDeviceUnlinkRequest, isPhotoRequest } from "../intents";

/**
 * Основной обработчик сообщения, адресованного Кире. Сначала разбирает
 * особые случаи (подтверждение отложенного действия, запрос фото, запрос
 * на отвязку устройства), а если ни один не подошёл — обычный AI-диалог:
 * контекст памяти, AI-провайдер, задержка перед ответом, запись в память.
 */
export async function handleAIMessage(env: Env, message: TgMessage): Promise<void> {
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

  try {
    await client.sendChatAction(message.chat.id, message.message_thread_id);

    const { profile, history } = await loadContext(env, message.chat.id, userId);
    const qmodsContextLine = await getQmodsContextLine(env, userId).catch(() => null);
    const provider = getAIProvider(env);
    const prompt = buildMessages(profile, history, text, qmodsContextLine);
    const reply = await provider.generate(prompt);

    const delayMs = numVar(env.RESPONSE_DELAY_MS, 1500);
    if (delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }

    await client.sendMessage(message.chat.id, reply, {
      messageThreadId: message.message_thread_id,
      replyToMessageId: message.message_id,
    });

    await rememberExchange(env, message.chat.id, userId, text, reply);
  } catch (error) {
    await logError(env, "handleAIMessage", error);
    await client
      .sendMessage(
        message.chat.id,
        "Что-то пошло не так на моей стороне 💜 Попробуй ещё раз чуть позже.",
        { messageThreadId: message.message_thread_id },
      )
      .catch(() => undefined);
  }
}
