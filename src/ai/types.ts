export type ChatRole = "system" | "user" | "assistant";

export interface ChatMessage {
  role: ChatRole;
  content: string;
}

export interface AIProvider {
  name: string;
  generate(messages: ChatMessage[]): Promise<string>;
}
