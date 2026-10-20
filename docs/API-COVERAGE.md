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

## Лента, профиль, друзья (этап 3)

| Функция | Метод | Права | Статус |
|---|---|---|---|
| Новости (бесконечная прокрутка, кэш Room + Paging 3 RemoteMediator) | `newsfeed.get` (filters=post, start_from) | wall, friends | реализовано, не проверено |
| Рекомендации | `newsfeed.getRecommended` (start_from) | wall | не проверено (доступность метода для стороннего приложения под вопросом) |
| Истории | `stories.get` (extended=1), `stories.markSeen` | stories | не проверено; без права `stories` ряд просто скрыт |
| Лайк записи / комментария | `likes.add` / `likes.delete` (type=post/comment) | wall | реализовано |
| Репост к себе | `wall.repost` (object=wall{owner}_{id}) | wall | реализовано |
| Запись целиком | `wall.getById` (extended=1) | wall | реализовано |
| Комментарии и ветки | `wall.getComments` (need_likes, thread_items_count=3) | wall | реализовано |
| Написать комментарий / ответ | `wall.createComment` (reply_to_comment) | wall | реализовано |
| Создание записи с фото | `photos.getWallUploadServer` → upload `photo` → `photos.saveWallPhoto` → `wall.post` (friends_only) | wall, photos | реализовано |
| Поиск по записям | `newsfeed.search` | wall | реализовано |
| Профиль пользователя | `users.get` (counters, status, last_seen, friend_status…) | vkid.personal_info, friends | реализовано |
| Профиль сообщества | `groups.getById` (description, members_count, is_member) | groups | реализовано (оба формата ответа: `{groups:[…]}` и массив) |
| Стена | `wall.get` (extended=1) | wall | реализовано |
| Фото | `photos.getAll` (photo_sizes=1) | photos | реализовано |
| Друзья, онлайн, поиск среди друзей | `friends.get` (order=hints, fields) | friends | реализовано |
| Заявки в друзья | `friends.getRequests` + `users.get`; `friends.add` / `friends.delete` | friends | реализовано |
| Сообщества пользователя | `groups.get` (extended=1) | groups | реализовано |
| Вступить / выйти | `groups.join` / `groups.leave` | groups | реализовано |
| Поиск людей | `users.search` | friends | реализовано |
| Поиск сообществ | `groups.search` | groups | реализовано |
| Дни рождения | `friends.get` (fields=bdate), сортировка локально | friends | реализовано |
| Редактирование своего профиля | `account.saveProfileInfo` | — | не реализовано: метод закрыт для сторонних приложений по данным открытых источников, кнопка ведёт в настройки приложения |

## Видео и клипы (этап 4)

| Функция | Метод | Права | Статус |
|---|---|---|---|
| Мои видео | `video.get` (owner_id=me) | video | реализовано |
| Поиск видео | `video.search` (sort=2, adult=0) | video | реализовано |
| Видео из подписок | `newsfeed.get` (filters=video) | wall, video | реализовано, не проверено |
| Каталог VK Видео (подборки, тренды) | — | — | **недоступно**: в публичной документации нет методов каталога; на вкладке честная пометка |
| Воспроизведение, выбор качества | `video.get` (videos=owner_id_id_accesskey) → `files` (mp4_144…mp4_2160, hls) в Media3 | video | не проверено: VK может не отдавать `files` стороннему приложению |
| Запасной плеер | поле `player` (embed URL) в WebView | video | не проверено; качество и PiP в нём недоступны |
| Внешние видео (YouTube и т.п.) | `files.external` → открыть в браузере | video | реализовано |
| PiP, полноэкранный режим | системные API Android 8+ | — | реализовано, не проверено |
| Лайк видео | `likes.add` / `likes.delete` (type=video) | video | реализовано |
| Комментарии к видео | `video.getComments` / `video.createComment` | video | реализовано |
| Лента клипов VK | — | — | **недоступно**: публичных методов для клипов нет. Вместо неё вертикальная лента **ваших** вертикальных видео (`video.get`) с автовоспроизведением, предзагрузкой соседнего ролика, лайками и комментариями. Чужие клипы не показываются и не имитируются |

## Музыка (этап 5)

| Функция | Метод | Права | Статус |
|---|---|---|---|
| Аудиозаписи, плейлисты, поиск музыки VK | `audio.*` | — | **недоступно**: методы закрыты для сторонних приложений (с 2016 года), в документации их нет. На экране честная заглушка, обходы не используются |
| Музыка на устройстве | MediaStore (`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`) | — | реализовано |
| Альбомы и исполнители (локальные «плейлисты»), поиск | группировка и фильтр по MediaStore | — | реализовано |
| Фоновое воспроизведение, уведомление, MediaSession, гарнитура | Media3 `MediaSessionService` + `DefaultMediaNotificationProvider` | — | реализовано, не проверено |
| Мини-плеер, полноэкранный плеер, очередь, перемешивание, повтор | `MediaController` | — | реализовано, не проверено |
| Аудио во вложениях записей и сообщений | — | — | только название и длительность: ссылки на аудио VK стороннему приложению не отдаются |
