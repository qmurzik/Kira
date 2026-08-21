import type { Env } from "../config/env";
import { getQmodsLink, clearQmodsLink } from "../database/qmodsLink";
import { qmodsGetSubscription, QmodsApiError } from "./client";

const CACHE_TTL_SECONDS = 600; // не дёргаем сайт чаще раза в 10 минут на пользователя

function cacheKey(telegramUserId: number): string {
  return `kira:qmods-sub:${telegramUserId}`;
}

/**
 * Возвращает готовую строку с данными подписки для системного контекста
 * LLM, если Telegram-пользователь привязал аккаунт qmods.ru. Кэширует
 * ответ сайта в KV, чтобы не запрашивать API на каждое сообщение.
 * Если токен истёк — тихо отвязывает аккаунт и просит привязать заново.
 */
export async function getQmodsContextLine(env: Env, telegramUserId: number): Promise<string | null> {
  const link = await getQmodsLink(env, telegramUserId);
  if (!link) return null;

  const cached = await env.KIRA_KV.get(cacheKey(telegramUserId));
  if (cached) return cached;

  try {
    const sub = await qmodsGetSubscription(link.sessionToken);
    const expiresText = sub.expiresAt > 0 ? new Date(sub.expiresAt * 1000).toLocaleDateString("ru-RU") : "—";
    const line = sub.active
      ? `Данные подписки QMODS пользователя (логин ${link.qmodsUsername}): подписка активна, тариф "${sub.planTitle}", осталось дней: ${sub.daysLeft}, дата окончания: ${expiresText}. Используй эти цифры, если спросят про подписку/аккаунт — не придумывай другие.`
      : `Данные подписки QMODS пользователя (логин ${link.qmodsUsername}): подписка сейчас не активна.`;

    await env.KIRA_KV.put(cacheKey(telegramUserId), line, { expirationTtl: CACHE_TTL_SECONDS });
    return line;
  } catch (error) {
    if (error instanceof QmodsApiError && error.httpStatus === 401) {
      await clearQmodsLink(env, telegramUserId);
      return "Привязка аккаунта QMODS у пользователя истекла. Если он спросит про подписку — сообщи, что нужно заново выполнить /link.";
    }
    return null; // сайт недоступен/ошибка — просто не добавляем контекст, не блокируем чат
  }
}
