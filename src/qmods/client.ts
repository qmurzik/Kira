/**
 * Тонкий клиент к существующему self-service API личного кабинета
 * qmods.ru (mod/api/client_api.php). Сервер уже умеет: логин по
 * логин+пароль → session_token (Bearer, живёт 30 дней на стороне сайта),
 * и запрос статуса подписки по этому токену. Отдельный серверный
 * секрет не нужен — Кира действует от имени привязанного пользователя.
 */

const QMODS_API_BASE = "https://qmods.ru/mod/api/client_api.php";

export class QmodsApiError extends Error {
  constructor(
    message: string,
    public readonly httpStatus: number,
  ) {
    super(message);
  }
}

interface QmodsLoginResponse {
  success: boolean;
  message?: string;
  session_token?: string;
  user?: { id: string; login: string };
}

interface QmodsSubscriptionResponse {
  success: boolean;
  message?: string;
  status?: string;
  plan?: string;
  plan_title?: string;
  expires_at?: number;
  days_left?: number;
}

export interface QmodsLoginResult {
  sessionToken: string;
  qmodsUserId: string;
  qmodsUsername: string;
}

export interface QmodsSubscription {
  active: boolean;
  plan: string;
  planTitle: string;
  expiresAt: number;
  daysLeft: number;
}

async function callApi<T extends { success: boolean; message?: string }>(
  path: string,
  options: { method?: string; token?: string; body?: unknown } = {},
): Promise<T> {
  const res = await fetch(`${QMODS_API_BASE}/${path}`, {
    method: options.method ?? "GET",
    headers: {
      "Content-Type": "application/json",
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  const data = (await res.json()) as T;
  if (!data.success) {
    throw new QmodsApiError(data.message ?? "Ошибка QMODS API", res.status);
  }
  return data;
}

export async function qmodsLogin(login: string, password: string): Promise<QmodsLoginResult> {
  const data = await callApi<QmodsLoginResponse>("login", {
    method: "POST",
    body: { login, password },
  });
  if (!data.session_token || !data.user) {
    throw new QmodsApiError("QMODS API вернул неполный ответ на логин", 502);
  }
  return {
    sessionToken: data.session_token,
    qmodsUserId: data.user.id,
    qmodsUsername: data.user.login,
  };
}

export async function qmodsGetSubscription(sessionToken: string): Promise<QmodsSubscription> {
  const data = await callApi<QmodsSubscriptionResponse>("subscription", { token: sessionToken });
  return {
    active: data.status === "active",
    plan: data.plan ?? "",
    planTitle: data.plan_title ?? data.plan ?? "",
    expiresAt: data.expires_at ?? 0,
    daysLeft: data.days_left ?? 0,
  };
}

export async function qmodsLogout(sessionToken: string): Promise<void> {
  await callApi("logout", { method: "POST", token: sessionToken }).catch(() => undefined);
}
