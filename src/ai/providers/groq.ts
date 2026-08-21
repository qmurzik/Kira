import type { Env } from "../../config/env";
import type { AIProvider, ChatMessage } from "../types";

/**
 * Резервный бесплатный вариант: Groq (щедрый free tier, требует GROQ_API_KEY).
 * Включается через AI_PROVIDER=groq в wrangler.toml.
 */
export function createGroqProvider(env: Env): AIProvider {
  return {
    name: "groq",
    async generate(messages: ChatMessage[]): Promise<string> {
      if (!env.GROQ_API_KEY) {
        throw new Error("GROQ_API_KEY не задан (wrangler secret put GROQ_API_KEY)");
      }

      const model = env.AI_MODEL && env.AI_MODEL.startsWith("@cf/")
        ? "llama-3.1-8b-instant"
        : env.AI_MODEL || "llama-3.1-8b-instant";

      const res = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${env.GROQ_API_KEY}`,
        },
        body: JSON.stringify({
          model,
          messages,
          max_tokens: 512,
          temperature: 0.7,
        }),
      });

      if (!res.ok) {
        throw new Error(`Groq API ошибка: ${res.status} ${await res.text()}`);
      }

      const data = (await res.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const content = data.choices?.[0]?.message?.content;
      if (!content) throw new Error("Groq API: пустой ответ модели");
      return content.trim();
    },
  };
}
