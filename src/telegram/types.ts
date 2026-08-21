/** Минимальный набор типов Telegram Bot API, необходимый Кире. */

export interface TgUser {
  id: number;
  is_bot: boolean;
  first_name: string;
  username?: string;
}

export interface TgChat {
  id: number;
  type: "private" | "group" | "supergroup" | "channel";
  title?: string;
  is_forum?: boolean;
}

export interface TgMessageEntity {
  type: string;
  offset: number;
  length: number;
}

export interface TgForumTopicCreated {
  name: string;
  icon_color?: number;
}

export interface TgMessage {
  message_id: number;
  message_thread_id?: number;
  is_topic_message?: boolean;
  date: number;
  chat: TgChat;
  from?: TgUser;
  text?: string;
  entities?: TgMessageEntity[];
  reply_to_message?: TgMessage;
  forum_topic_created?: TgForumTopicCreated;
}

export interface TgUpdate {
  update_id: number;
  message?: TgMessage;
  edited_message?: TgMessage;
}
