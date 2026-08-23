import type { Env } from "../config/env";
import type { GroupHistoryEntry } from "../memory/groupHistory";
import { getUserProfile, safeParse } from "../database/users";

const MAX_PARTICIPANTS = 4;

/**
 * Короткая заметка о других людях, недавно писавших в этой теме — чтобы
 * Кира не забывала, кто есть кто, когда в разговоре участвует несколько
 * человек, а не только тот, кто написал последнее сообщение.
 */
export async function buildParticipantsNote(
  env: Env,
  history: GroupHistoryEntry[],
  excludeUserId: number,
): Promise<string | null> {
  const seen = new Set<number>([excludeUserId]);
  const ids: number[] = [];
  for (let i = history.length - 1; i >= 0 && ids.length < MAX_PARTICIPANTS; i--) {
    const id = history[i]?.speakerId;
    if (id && !seen.has(id)) {
      seen.add(id);
      ids.push(id);
    }
  }
  if (ids.length === 0) return null;

  const parts: string[] = [];
  for (const id of ids) {
    const profile = await getUserProfile(env, id);
    const name = profile?.firstName || profile?.username;
    if (!name) continue;

    const prefs = profile ? safeParse(profile.preferencesJson) : {};
    const facts = Object.entries(prefs).slice(0, 2).map(([key, value]) => `${key}: ${value}`);
    parts.push(facts.length > 0 ? `${name} (${facts.join(", ")})` : name);
  }
  if (parts.length === 0) return null;

  return `Другие участники, недавно писавшие в этой теме: ${parts.join("; ")}. Учитывай это, если разговор их касается.`;
}
