import type { Env } from "../config/env";

const SESSION_LIFETIME_MS = 30 * 24 * 60 * 60 * 1000; // совпадает со сроком жизни токена на сайте

export interface QmodsLink {
  telegramUserId: number;
  qmodsUserId: string;
  qmodsUsername: string;
  sessionToken: string;
  linkedAt: number;
  tokenExpiresAt: number;
}

interface QmodsLinkRow {
  telegram_user_id: number;
  qmods_user_id: string;
  qmods_username: string;
  session_token: string;
  linked_at: number;
  token_expires_at: number;
}

function rowToLink(row: QmodsLinkRow): QmodsLink {
  return {
    telegramUserId: row.telegram_user_id,
    qmodsUserId: row.qmods_user_id,
    qmodsUsername: row.qmods_username,
    sessionToken: row.session_token,
    linkedAt: row.linked_at,
    tokenExpiresAt: row.token_expires_at,
  };
}

export async function getQmodsLink(env: Env, telegramUserId: number): Promise<QmodsLink | null> {
  const row = await env.DB.prepare("SELECT * FROM qmods_links WHERE telegram_user_id = ?")
    .bind(telegramUserId)
    .first<QmodsLinkRow>();
  return row ? rowToLink(row) : null;
}

export async function setQmodsLink(
  env: Env,
  telegramUserId: number,
  qmodsUserId: string,
  qmodsUsername: string,
  sessionToken: string,
): Promise<void> {
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO qmods_links (telegram_user_id, qmods_user_id, qmods_username, session_token, linked_at, token_expires_at)
     VALUES (?, ?, ?, ?, ?, ?)
     ON CONFLICT(telegram_user_id) DO UPDATE SET
       qmods_user_id = excluded.qmods_user_id,
       qmods_username = excluded.qmods_username,
       session_token = excluded.session_token,
       linked_at = excluded.linked_at,
       token_expires_at = excluded.token_expires_at`,
  )
    .bind(telegramUserId, qmodsUserId, qmodsUsername, sessionToken, now, now + SESSION_LIFETIME_MS)
    .run();
}

export async function clearQmodsLink(env: Env, telegramUserId: number): Promise<void> {
  await env.DB.prepare("DELETE FROM qmods_links WHERE telegram_user_id = ?")
    .bind(telegramUserId)
    .run();
}
