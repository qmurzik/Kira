import type { Env } from "../config/env";

export interface UserProfile {
  userId: number;
  username: string | null;
  firstName: string | null;
  preferencesJson: string;
  messageCount: number;
  firstSeenAt: number;
  lastActiveAt: number;
}

interface UserRow {
  user_id: number;
  username: string | null;
  first_name: string | null;
  preferences_json: string;
  message_count: number;
  first_seen_at: number;
  last_active_at: number;
}

function rowToProfile(row: UserRow): UserProfile {
  return {
    userId: row.user_id,
    username: row.username,
    firstName: row.first_name,
    preferencesJson: row.preferences_json,
    messageCount: row.message_count,
    firstSeenAt: row.first_seen_at,
    lastActiveAt: row.last_active_at,
  };
}

export async function getUserProfile(env: Env, userId: number): Promise<UserProfile | null> {
  const row = await env.DB.prepare("SELECT * FROM users WHERE user_id = ?")
    .bind(userId)
    .first<UserRow>();
  return row ? rowToProfile(row) : null;
}

export async function upsertUserSeen(
  env: Env,
  userId: number,
  username: string | null,
  firstName: string | null,
): Promise<void> {
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO users (user_id, username, first_name, preferences_json, message_count, first_seen_at, last_active_at)
     VALUES (?, ?, ?, '{}', 1, ?, ?)
     ON CONFLICT(user_id) DO UPDATE SET
       username = excluded.username,
       first_name = excluded.first_name,
       message_count = message_count + 1,
       last_active_at = excluded.last_active_at`,
  )
    .bind(userId, username, firstName, now, now)
    .run();
}

export async function setPreference(
  env: Env,
  userId: number,
  key: string,
  value: unknown,
): Promise<void> {
  const profile = await getUserProfile(env, userId);
  const prefs = profile ? safeParse(profile.preferencesJson) : {};
  prefs[key] = value;
  await env.DB.prepare("UPDATE users SET preferences_json = ? WHERE user_id = ?")
    .bind(JSON.stringify(prefs), userId)
    .run();
}

export async function deleteUser(env: Env, userId: number): Promise<void> {
  await env.DB.prepare("DELETE FROM users WHERE user_id = ?").bind(userId).run();
}

function safeParse(json: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(json);
    return typeof parsed === "object" && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}
