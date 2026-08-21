-- Профили пользователей: долговременная память Киры
CREATE TABLE IF NOT EXISTS users (
  user_id           INTEGER PRIMARY KEY,
  username          TEXT,
  first_name        TEXT,
  preferences_json  TEXT NOT NULL DEFAULT '{}',
  message_count     INTEGER NOT NULL DEFAULT 0,
  first_seen_at     INTEGER NOT NULL,
  last_active_at    INTEGER NOT NULL
);

-- Настройки чата: где именно (в какой теме форума) Кира отвечает
CREATE TABLE IF NOT EXISTS chat_settings (
  chat_id          INTEGER PRIMARY KEY,
  kira_topic_id    INTEGER,
  kira_topic_name  TEXT,
  updated_at       INTEGER NOT NULL
);

-- Лог ошибок для диагностики (без содержимого сообщений пользователей)
CREATE TABLE IF NOT EXISTS error_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  context     TEXT NOT NULL,
  message     TEXT NOT NULL,
  created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_error_log_created_at ON error_log (created_at);
