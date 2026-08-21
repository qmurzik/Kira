export interface Env {
  // Bindings
  DB: D1Database;
  KIRA_KV: KVNamespace;
  // KIRA_ASSETS?: R2Bucket; — добавьте после включения R2 (см. wrangler.toml)
  AI: Ai;

  // Секреты (wrangler secret put ...)
  TELEGRAM_BOT_TOKEN: string;
  TELEGRAM_WEBHOOK_SECRET: string;
  GROQ_API_KEY?: string;
  OPENROUTER_API_KEY?: string;

  // Обычные переменные (wrangler.toml [vars])
  AI_PROVIDER: "cloudflare" | "groq" | "openrouter";
  AI_MODEL: string;
  KIRA_TOPIC_NAME: string;
  STRICT_TOPIC_ONLY: string;
  RATE_LIMIT_PER_HOUR: string;
  RESPONSE_DELAY_MS: string;
  SHORT_TERM_LIMIT: string;
  SHORT_TERM_TTL_SECONDS: string;
}

export function numVar(value: string | undefined, fallback: number): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export function boolVar(value: string | undefined, fallback: boolean): boolean {
  if (value === undefined) return fallback;
  return value.trim().toLowerCase() === "true";
}
