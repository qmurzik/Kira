-- Периодическая отправка фото Киры в чат: опция на уровне чата + отметка
-- последней отправки, чтобы cron не слал чаще заданного интервала.
ALTER TABLE chat_settings ADD COLUMN photos_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE chat_settings ADD COLUMN last_photo_sent_at INTEGER NOT NULL DEFAULT 0;
