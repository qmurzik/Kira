# VK Lite — лёгкий клиент VK для Android

Неофициальный нативный клиент (Kotlin + Jetpack Compose). Работает только через **официальный VK API**
и **VK ID** с токеном **вашего собственного** приложения. Название, иконки и цвета свои, фирменные
ресурсы VK не используются. Имя пакета `app.pulse.client` (debug: `app.pulse.client.debug`) и ключ подписи фиксированы: они зарегистрированы в кабинете VK ID и меняться не должны.

> Статус: код написан, на устройстве **не проверялся**. Что именно не проверено — `docs/UNVERIFIED.md`.
> Что доступно через API, а что закрыто — `docs/API-COVERAGE.md`.

## Стек и версии

| | |
|---|---|
| AGP / Gradle / Kotlin | 8.7.3 / 8.10.2 / 2.0.21 (KSP 2.0.21-1.0.28) |
| Compose BOM | 2024.12.01 (Material 3) |
| JVM | 17 |
| SDK | minSdk 21, targetSdk/compileSdk 35 |
| Сеть | OkHttp 4.12 + kotlinx.serialization 1.7.3 |
| Хранилище | Room 2.6.1 (кэш), DataStore (настройки), AndroidKeyStore (сессии) |
| Прочее | Navigation Compose, Paging 3, Coil 2, Media3 1.5.1, WorkManager, Biometric |
| DI | ручной (`di/AppContainer`, `di/UserSession`), без Hilt |

## Сборка

1. Android Studio Ladybug или новее, JDK 17.
2. `cp local.properties.example local.properties`, указать `sdk.dir`. Можно сразу указать `VK_CLIENT_ID`.
3. `./gradlew assembleDebug` → `app/build/outputs/apk/debug/`.
   `./gradlew assembleRelease` → R8 + shrinkResources. Без `keystore.properties` release подписывается
   debug-ключом: годится для замеров и установки себе, но не для публикации.

### Ключ подписи (не коммитится)
`keystore.properties` в корне: `PULSE_KEYSTORE_PATH`, `PULSE_KEYSTORE_PASSWORD`, `PULSE_KEY_ALIAS`, `PULSE_KEY_PASSWORD`.

## Настройка VK ID

1. Создайте приложение на https://id.vk.ru (тип «Android» или «Web»/«Standalone» — что позволит кабинет).
2. Включите нужные доступы (друзья, стена, фото, видео, истории, сообщества, уведомления, документы, сообщения).
   Какие из них кабинет реально даёт стороннему приложению, решает VK.
3. Добавьте **доверенный Redirect URL**. По умолчанию приложение использует `vk<ID>://vk.com/blank.html`.
   Если кабинет требует другой, укажите его на экране входа → «Дополнительно».
4. Укажите ID приложения в `local.properties` (`VK_CLIENT_ID`) или введите его на экране входа.

Чужие client_id (официальных и сторонних клиентов) приложение не использует и не поддерживает.

## Архитектура

```
app/src/main/java/app/pulse/client/
  core/security   SecureStore (AES-GCM в AndroidKeyStore), PKCE, хэш PIN
  core/util       форматирование дат/чисел (ru)
  data/api        VkApi (POST, троттлинг 3 rps, код 5 → refresh), DTO
  data/auth       VK ID OAuth 2.1, AccountStore (несколько аккаунтов, зашифровано)
  data/mapper     DTO → неизменяемые доменные модели
  data/settings   DataStore
  di              AppContainer (синглтоны), UserSession (всё, что привязано к аккаунту)
  domain/model    @Immutable модели для UI
  ui/...          экраны Compose + ViewModel/StateFlow
```

- Один Activity, Navigation Compose. Нижняя панель: Главная, Сервисы, Мессенджер, Клипы, Видео.
- Смена аккаунта пересоздаёт `UserSession` и граф навигации (`key(userId)`), кэш у каждого аккаунта свой.
- Токены не логируются, в URL не попадают (все запросы POST), резервное копирование отключено
  (`allowBackup=false`, `data_extraction_rules`).

## Сообщения

- Диалоги кэшируются в Room (`cache_<userId>.db`), список открывается мгновенно из кэша и обновляется из сети.
- Long Poll работает, только пока приложение на экране (ProcessLifecycleOwner). В фоне раз в 15+ минут
  WorkManager проверяет непрочитанные и показывает уведомление с ответом из шторки.
  Настоящих push нет: VK не даёт сторонним приложениям регистрироваться на push.
- Отправка оптимистичная: сообщение сразу появляется с часиками и заменяется серверной копией.
- Если VK не выдаст приложению права на сообщения, раздел честно покажет «Недоступно через API».

## Лента и профиль

- Лента и рекомендации идут через Paging 3 + `RemoteMediator`. Страницы пишутся в Room, поэтому при
  повторном открытии лента показывается из кэша мгновенно, а затем обновляется.
- `LazyColumn` везде со стабильными `key` и `contentType`; модели `@Immutable`.
- Профиль: шапка, счётчики, фото, стена с подгрузкой; друзья (все, онлайн, заявки), сообщества,
  фото с полноэкранным просмотром, дни рождения, поиск людей, сообществ и записей.

## Видео и клипы

- Вкладки «Подписки» (`newsfeed.get filters=video`), «Мои», «Поиск». Каталога VK Видео в API нет, об этом
  прямо написано на вкладке.
- Плеер Media3: выбор качества (HLS «Авто» или mp4 144p…2160p), полноэкранный режим с поворотом,
  картинка в картинке (кнопка и автоматически при сворачивании). Если API не отдаёт прямые файлы,
  используется embed-плеер VK в WebView.
- «Клипы»: публичного API нет. Экран показывает ленту **ваших** вертикальных видео в стиле клипов:
  автовоспроизведение, предзагрузка следующего ролика, лайки и комментарии.

## Музыка

Методы `audio.*` VK закрыты для сторонних приложений, поэтому раздел работает с музыкой на устройстве:
треки, альбомы, исполнители, поиск, очередь, перемешивание и повтор, фоновое воспроизведение через
Media3 `MediaSessionService` с системным медиа-уведомлением и мини-плеер над нижней панелью.

## Уведомления и настройки

- Центр уведомлений (`notifications.get`) с заявками в друзья сверху, подгрузкой и отметкой просмотра.
- Настройки: тема (системная, светлая, тёмная), размер шрифта, фоновые уведомления и их частота,
  скрытие текста в уведомлениях, отправка «прочитано» и «печатает», запрет снимков экрана,
  PIN-код и биометрия с тайм-аутом блокировки, автовоспроизведение, экономия трафика,
  размер и очистка кэша, экспорт и импорт настроек в JSON. PIN и токены в экспорт не попадают.

## Скорость и размер: как измерить

Цель: release-APK заметно меньше 15 МБ и быстрый холодный старт. **Ничего из этого не замерялось.**

Что сделано для скорости:
- R8 (`isMinifyEnabled`) и `isShrinkResources`, только локали `ru`/`en`, без Hilt и рефлексии.
- `profileinstaller` + `app/src/main/baseline-prof.txt` для кода приложения
  (у AndroidX и Compose свои профили внутри библиотек).
- Холодный старт: в `Application` только создание контейнера. Сеть, БД и Long Poll запускаются лениво.
  Тема окна без splash-библиотеки.
- Списки: стабильные `key`/`contentType`, `@Immutable`-модели, ленты из Room-кэша, один `ImageLoader`
  с общим пулом соединений OkHttp.

Как измерить:
1. Размер: `./gradlew assembleRelease`, затем
   `$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer apk summary app/build/outputs/apk/release/app-release.apk`
   (или Build → Analyze APK в Android Studio).
2. Холодный старт на устройстве:
   `adb shell am force-stop app.pulse.client && adb shell am start -W -n app.pulse.client/.MainActivity`.
   Смотреть `TotalTime`, повторить 10 раз и взять медиану. Сравнить до и после установки профиля:
   `adb shell cmd package compile -f -m speed-profile app.pulse.client`.
3. Точнее: добавить модуль `:baselineprofile` (плагин `androidx.baselineprofile`, Macrobenchmark
   `BaselineProfileRule` + `StartupTimingMetric`) и сгенерировать профиль на реальном устройстве
   вместо ручного `baseline-prof.txt`.
4. Рекомпозиции: Layout Inspector → Recomposition counts на ленте и в чате.

## Проверка без SDK

- `python3 scripts/check_project.py`: импорты проектных классов, ссылки на ресурсы (`R.*` и `@type/name`),
  классы в манифесте, алиасы каталога версий, баланс скобок, следы логирования токенов, неиспользуемые
  импорты (`--fix-imports` удаляет их).
- `python3 scripts/check_symbols.py`: эвристический поиск неразрешённых вызовов и extension-функций без
  импорта (`.padding`, `.clickable`, `.stateIn`, `.collectAsStateWithLifecycle` и т.д.).
- Чистый Kotlin (`core/util/Format.kt`) дополнительно скомпилирован и проверен компилятором Kotlin 2.0.21,
  который поставляется вместе с Gradle.

Это не замена компиляции: Android- и Compose-код здесь не собирался. Полная проверка —
`./gradlew assembleDebug testDebugUnitTest` в Android Studio или GitHub Actions (`.github/workflows/build.yml`).

## Этапы

| Этап | Содержание | Статус |
|---|---|---|
| 1 | каркас, тема, навигация, вход VK ID, несколько аккаунтов | код готов |
| 2 | диалоги и чат, Long Poll, уведомления | код готов |
| 3 | лента, профиль, друзья | код готов |
| 4 | видео и клипы | код готов |
| 5 | музыка | код готов |
| 6 | уведомления, настройки, полировка | код готов |
