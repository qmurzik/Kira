-- Привязка Telegram-аккаунта к личному кабинету qmods.ru.
-- Пароль НЕ хранится — только session_token, полученный от client_api.php
-- при логине (используется как Bearer-токен, срок жизни задаёт сам сайт).
CREATE TABLE IF NOT EXISTS qmods_links (
  telegram_user_id  INTEGER PRIMARY KEY,
  qmods_user_id     TEXT NOT NULL,
  qmods_username    TEXT NOT NULL,
  session_token     TEXT NOT NULL,
  linked_at         INTEGER NOT NULL,
  token_expires_at  INTEGER NOT NULL
);
