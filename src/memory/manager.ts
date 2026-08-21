import type { Env } from "../config/env";
import { getUserProfile, upsertUserSeen, deleteUser } from "../database/users";
import { getShortTermHistory, appendExchange, clearShortTerm } from "./shortTerm";

/**
 * Единая точка входа для работы с памятью Киры:
 * - KV хранит короткую историю диалога (нужна модели для контекста, TTL);
 * - D1 хранит долговременный профиль (имя, предпочтения, счётчики).
 */
export async function loadContext(env: Env, chatId: number, userId: number) {
  const [profile, history] = await Promise.all([
    getUserProfile(env, userId),
    getShortTermHistory(env, chatId, userId),
  ]);
  return { profile, history };
}

export async function rememberSeen(
  env: Env,
  userId: number,
  username: string | null,
  firstName: string | null,
): Promise<void> {
  await upsertUserSeen(env, userId, username, firstName);
}

export async function rememberExchange(
  env: Env,
  chatId: number,
  userId: number,
  userMessage: string,
  assistantMessage: string,
): Promise<void> {
  await appendExchange(env, chatId, userId, userMessage, assistantMessage);
}

/** Полная очистка памяти о пользователе в рамках чата: KV-история + профиль в D1. */
export async function clearMemory(env: Env, chatId: number, userId: number): Promise<void> {
  await Promise.all([clearShortTerm(env, chatId, userId), deleteUser(env, userId)]);
}
