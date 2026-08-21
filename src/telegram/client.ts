import type { Env } from "../config/env";

interface SendMessageOptions {
  messageThreadId?: number;
  replyToMessageId?: number;
}

export class TelegramClient {
  private readonly apiBase: string;

  constructor(private readonly env: Env) {
    this.apiBase = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}`;
  }

  private async call<T>(method: string, payload: Record<string, unknown>): Promise<T> {
    const res = await fetch(`${this.apiBase}/${method}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const data = (await res.json()) as { ok: boolean; result: T; description?: string };
    if (!data.ok) {
      throw new Error(`Telegram API ${method} ошибка: ${data.description ?? res.status}`);
    }
    return data.result;
  }

  async sendMessage(chatId: number, text: string, options: SendMessageOptions = {}): Promise<void> {
    await this.call("sendMessage", {
      chat_id: chatId,
      text,
      message_thread_id: options.messageThreadId,
      reply_to_message_id: options.replyToMessageId,
      parse_mode: "HTML",
    });
  }

  async sendChatAction(chatId: number, messageThreadId?: number): Promise<void> {
    await this.call("sendChatAction", {
      chat_id: chatId,
      action: "typing",
      message_thread_id: messageThreadId,
    });
  }

  async getMe(): Promise<{ id: number; username?: string }> {
    return this.call("getMe", {});
  }

  async getChatMemberStatus(chatId: number, userId: number): Promise<string> {
    const result = await this.call<{ status: string }>("getChatMember", {
      chat_id: chatId,
      user_id: userId,
    });
    return result.status;
  }

  async setWebhook(url: string, secretToken: string): Promise<void> {
    await this.call("setWebhook", {
      url,
      secret_token: secretToken,
      allowed_updates: ["message"],
    });
  }
}
