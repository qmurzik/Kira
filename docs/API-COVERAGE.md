# Покрытие VK API

Версия API: **5.199** (`BuildConfig.VK_API_VERSION`). Хост: `api.vk.com` (переопределяется `VK_API_HOST`).
Авторизация: **VK ID, OAuth 2.1 + PKCE** (`id.vk.ru`), токен пользователя **вашего собственного** приложения.

Статусы:
- **реализовано** — код вызова и UI написаны, но на устройстве ещё не проверены (см. `UNVERIFIED.md`);
- **недоступно** — метода нет в публичном API или он закрыт для сторонних приложений: в UI честная заглушка;
- **не проверено** — метод документирован, но доступ для стороннего приложения под вопросом (см. примечание).

Как сверялась документация: dev.vk.com и id.vk.ru из среды разработки недоступны (прокси блокирует),
поэтому сверка шла через веб-поиск по страницам документации. Названия методов и параметров нужно
перепроверить по dev.vk.com перед релизом — это отдельный пункт в `UNVERIFIED.md`.

## Вход и сессия (этап 1)

| Функция | Метод / эндпоинт | Права | Статус |
|---|---|---|---|
| Вход в WebView | `GET https://id.vk.ru/authorize` (response_type=code, PKCE S256, state) | — | реализовано |
| Обмен кода на токены | `POST https://id.vk.ru/oauth2/auth` grant_type=authorization_code, code_verifier, device_id | — | реализовано |
| Обновление сессии при ошибке 5 | `POST https://id.vk.ru/oauth2/auth` grant_type=refresh_token, device_id | — | реализовано |
| Выход | `POST https://id.vk.ru/oauth2/logout` (best effort) + удаление локальных данных | — | не проверено: эндпоинт не нашёлся в доступных выдержках документации |
| Имя и аватар аккаунта | `users.get` (fields=photo_100) | vkid.personal_info | реализовано |
| Несколько аккаунтов | локально, зашифрованный список сессий | — | реализовано |

Запрашиваемые права (scope, через пробел): `vkid.personal_info friends wall photos video stories groups notifications messages docs status`.
Фактически выдаются только те, что включены для приложения в кабинете VK ID.

## Обработка ошибок API

| Код | Поведение |
|---|---|
| 5 | один раз обновить токен через refresh_token и повторить запрос; при неудаче — баннер «Сессия истекла» |
| 6, 9, 10 | пауза 400/800/1200 мс и повтор (до 3 раз) |
| 14 | сообщение «VK запросил капчу» (капча не обходится) |
| 3, 7, 15, 27, 28, 30, 203, 260, 917 | «Недоступно через API для этого приложения» |

## Сообщения (этап 2)

Важно: по открытым источникам (форумы разработчиков, roadmap VK) доступ к `messages.*` по
**пользовательскому** токену для сторонних приложений ограничен и, вероятно, выдаётся только после
одобрения VK. Код вызовов написан полностью. Если VK вернёт 15/917/«no right to use messages»,
экран покажет «Недоступно через API для этого приложения (код N)», данные не подставляются.

| Функция | Метод | Права | Статус |
|---|---|---|---|
| Список диалогов, закрепы (sort_id.major_id), непрочитанные | `messages.getConversations` (extended=1, filter=all/unread) | messages | не проверено |
| Поиск диалогов | `messages.searchConversations` | messages | не проверено |
| Обновление диалога по событию | `messages.getConversationsById` | messages | не проверено |
| История | `messages.getHistory` (extended=1) | messages | не проверено |
| Полные объекты по событиям Long Poll | `messages.getById` | messages | не проверено |
| Отправка, ответ (`reply_to`), пересылка (`forward_messages`), стикер (`sticker_id`) | `messages.send` (random_id) | messages | не проверено |
| Редактирование | `messages.edit` (keep_forward_messages, keep_snippets) | messages | не проверено |
| Удаление для себя / для всех | `messages.delete` (delete_for_all) | messages | не проверено |
| Прочтение | `messages.markAsRead` (start_message_id) | messages | не проверено |
| «Печатает» / «записывает голосовое» | `messages.setActivity` (typing / audiomessage) | messages | не проверено |
| Long Poll | `messages.getLongPollServer` (lp_version=3) + `https://{server}?act=a_check&mode=202&version=3` | messages | не проверено |
| Фото во вложении | `photos.getMessagesUploadServer` → upload `photo` → `photos.saveMessagesPhoto` | photos, messages | не проверено |
| Голосовое | `docs.getMessagesUploadServer` (type=audio_message) → upload `file` → `docs.save` | docs, messages | не проверено |
| Файл | `docs.getMessagesUploadServer` (type=doc) → `docs.save` | docs, messages | не проверено |
| Наборы стикеров | `store.getProducts` (type=stickers, filters=active) | — | не проверено; при отказе заглушка «Отправка стикеров недоступна через API» |
| Эмодзи | локально (Unicode) | — | реализовано |
| Push-уведомления в реальном времени | `account.registerDevice` (FCM) | — | **недоступно**: регистрация push VK для сторонних приложений не предусмотрена. Замена: Long Poll на переднем плане и WorkManager-проверка `messages.getConversations(filter=unread)` раз в 15+ минут |
| Ответ из шторки, «Прочитано» | `messages.send`, `messages.markAsRead` из BroadcastReceiver | messages | не проверено |
| Онлайн-статус в реальном времени (события 8/9) | — | — | не реализовано: статус обновляется при обновлении диалога |
