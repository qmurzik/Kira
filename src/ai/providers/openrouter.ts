import type { Env } from "../../config/env";
import type { AIProvider, ChatMessage } from "../types";

/**
 * Резервный бесплатный вариант: OpenRouter, модели с суффиксом ":free"
 * (например meta-llama/llama-3.1-8b-instruct:free). Требует OPENROUTER_API_KEY
 * (бесплатная регистрация). Включается через AI_PROVIDER=openrouter.
 */
export function createOpenRouterProvider(env: Env): AIProvider {
  return {
    name: "openrouter",
    async generate(messages: ChatMessage[]): Promise<string> {
      if (!env.OPENROUTER_API_KEY) {
        throw new Error("OPENROUTER_API_KEY не задан (wrangler secret put OPENROUTER_API_KEY)");
      }

      const model = env.AI_MODEL && env.AI_MODEL.startsWith("@cf/")
        ? "meta-llama/llama-3.1-8b-instruct:free"
        : env.AI_MODEL || "meta-llama/llama-3.1-8b-instruct:free";

      const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
          "HTTP-Referer": "https://qmods.app",
          "X-Title": "Kira QMODS Bot",
        },
        body: JSON.stringify({
          model,
          messages,
          max_tokens: 512,
          temperature: 0.7,
        }),
      });

      if (!res.ok) {
        throw new Error(`OpenRouter API ошибка: ${res.status} ${await res.text()}`);
      }

      const data = (await res.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const content = data.choices?.[0]?.message?.content;
      if (!content) throw new Error("OpenRouter API: пустой ответ модели");
      return content.trim();
    },
  };
}
