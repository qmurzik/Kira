# RU AdBlock — LSPosed-модуль

Скрывает рекламу в российских приложениях: Авито, Яндекс (Go, Карты, Маркет, Браузер, Музыка, Кинопоиск…), Ozon, Wildberries, RuStore, VK и др.

Модуль не привязан к обфусцированным классам конкретных приложений. Вместо этого он работает
универсальными эвристиками в четыре слоя, каждый из которых включается отдельно.

- Xposed API 82, Java, minSdk 26, targetSdk 35
- Требуется LSPosed (Zygisk/Riru) с поддержкой `xposedsharedprefs` (API ≥ 93)

## Установка

1. Скачайте APK:
   - **Release**: страница *Releases* репозитория (создаётся при пуше тега `v*`);
   - **Сборка из любого коммита**: вкладка *Actions* → workflow «ru-adblock-lsposed - Build» → нужный запуск → артефакт `ru-adblock-lsposed-release` (или `-debug`).
2. Установите APK как обычное приложение.
3. Откройте **LSPosed Manager → Модули → RU AdBlock**, включите модуль.
4. Отметьте приложения в области действия. Рекомендуемый список подставляется автоматически (см. `app/src/main/res/values/arrays.xml`).
5. Принудительно остановите целевые приложения (или перезагрузите телефон).

Настройки открываются из LSPosed (значок шестерёнки у модуля) или ярлыком «RU AdBlock».
После любого изменения настроек принудительно остановите целевые приложения: настройки
читаются при старте процесса.

Логи: LSPosed Manager → Журналы, тег `RuAdBlock`. Включите «Подробный лог», чтобы видеть
каждое срабатывание (заблокированный домен, удалённый JSON-элемент, схлопнутую ячейку).

## Слои

### 1. Рекламные SDK

Хуки на публичный API рекламных SDK (он не обфусцируется, иначе приложение не смогло бы им пользоваться):

| SDK | Классы |
|---|---|
| Yandex Mobile Ads (`com.yandex.mobile.ads`) | `BannerAdView`, `InterstitialAdLoader`, `RewardedAdLoader`, `AppOpenAdLoader`, `NativeAdLoader`, `NativeBulkAdLoader`, `SliderAdLoader`, `InstreamAdLoader`, `FeedAdLoader` |
| myTarget / VK Реклама (`com.my.target`) | `MyTargetView`, `InterstitialAd`, `RewardedAd`, `NativeAd`, `NativeBannerAd`, `NativeAppwallAd`, `InstreamAd`, `InstreamAudioAd`, `NativeAdLoader` |
| Google Mobile Ads (`com.google.android.gms.ads`) | `BaseAdView`/`AdView`/`AdManagerAdView`, `AdLoader`, `InterstitialAd`, `RewardedAd`, `RewardedInterstitialAd`, `AppOpenAd`, `AdManagerInterstitialAd` |

Методы загрузки (`loadAd`, `load`, `loadAds`…) не выполняют запрос. Вместо этого слушатель приложения через 0,5 с
получает штатную ошибку «нет рекламы» (`onAdFailedToLoad(AdRequestError NO_FILL)`,
`onNoAd(...)`, `onAdFailedToLoad(LoadAdError ERROR_CODE_NO_FILL)`). Приложение видит обычный no fill
и не падает. Слушатель запоминается при вызовах `set*Listener`/`set*Callback` или берётся из аргументов.

### 2. Блокировка рекламных доменов

- `InetAddress.getAllByName` / `getByName` (и скрытые `*OnNet`, которые использует `android.net.Network`);
- пользовательские `okhttp3.Dns` (DoH и т.п.) оборачиваются при вызове `OkHttpClient.Builder.dns()`.

Для доменов из списка бросается `UnknownHostException`. Список — в файле
[`app/src/main/res/raw/ad_domains.txt`](app/src/main/res/raw/ad_domains.txt).

### 3. Фильтрация JSON

- Хуки на конструкторы `org.json.JSONObject(String)` и `JSONArray(String)`.
- Если в приложении есть необфусцированный `okhttp3`, при `OkHttpClient.Builder.build()` добавляется интерцептор.
  Он фильтрует JSON-ответы (`Content-Type: *json*`). Так покрываются Retrofit + Gson/Moshi/kotlinx.serialization
  и экраны на Compose.

Правила:
- удаляются **только элементы массивов** (объекты), корневой объект не трогается;
- элемент удаляется, если у него (или у его прямого вложенного объекта) есть маркер `erid`
  (также `advertErid`, `ad_erid`) с непустым значением или флаг `isAd`, `isAdvert`, `advert`,
  `promoted`, `sponsored`, `adMarker`, `isPromo`… со значением `true`;
- элементы с защитными ключами (`subscription`, `tariff`, `paymentMethod`, `purchaseToken`, `sku`…) не удаляются никогда;
- ответы больше 1,5 МБ не обрабатываются. Перед разбором выполняется дешёвая проверка `indexOf` по именам ключей:
  если ни одного ключа в тексте нет, JSON не разбирается;
- ответы с путями, содержащими `pay`, `bank`, `billing`, `subscri`, `checkout`, `order`, `cart`, `auth`,
  `token`, `tariff`, `plus`, `wallet`, не трогаются.

Ключи — в файле [`app/src/main/res/raw/json_ad_keys.txt`](app/src/main/res/raw/json_ad_keys.txt).

### 4. UI-эвристика

Хук на `TextView.setText`. Если текст короткий (до 30 символов) и это «Реклама», «Реклама · …»,
«erid: …», «Спонсорский», «Промо», модуль находит ближайшего предка — элемент `RecyclerView` (`itemView`).
Если метка действительно видима, элемент схлопывается: ширина и высота 0 через `LayoutParams`, а не `GONE`.

Исходные `LayoutParams` восстанавливаются при каждом переиспользовании ячейки
(`RecyclerView.Adapter.bindViewHolder`), поэтому обычные товары не пропадают. Если найти этот
метод не удалось (RecyclerView обфусцирован), слой отключается целиком, чтобы не прятать лишнее.
Страницы `ViewPager2` и ячейки размером с весь список не схлопываются.

## Как добавить домены и ключи

**Без пересборки:** в настройках модуля есть поля «Дополнительные домены» и «Дополнительные JSON-ключи».

**В исходниках** (попадёт в следующую сборку):

`app/src/main/res/raw/ad_domains.txt`:
```
example-ads.ru        # домен и все поддомены
~adserver             # любой хост, содержащий подстроку
```

`app/src/main/res/raw/json_ad_keys.txt`:
```
marker:adToken        # элемент удаляется, если ключ есть и не пуст
flag:isBanner         # элемент удаляется, если значение == true
keep:offerId          # элемент с этим ключом не удаляется никогда
```

Чтобы найти нужный ключ, включите «Подробный лог» и посмотрите трафик приложения,
например через HTTP Toolkit или mitmproxy.

## Безопасность и ограничения ответственности

- Процессы банков и платёжных сервисов **не хукаются**. Исключаются пакеты и процессы, в имени которых есть
  сегменты `bank`, `fintech`, `finance`, `wallet`, `payment`, `billing`, `checkout`, `*pay`/`pay*`, `sbp`, `nspk`, `mir`
  (Ozon Банк, WB Банк, Яндекс Пэй, Яндекс Банк и т.п.), даже если их добавить в область действия.
- Модуль не трогает платный функционал, подписки и проверки покупок. Только реклама.
- Каждый хук обёрнут в `try/catch`, ошибки пишутся через `XposedBridge.log` с тегом `RuAdBlock`.

## Известные ограничения

- **Compose**-экраны не покрываются UI-слоем: там нет `TextView`/`RecyclerView`. Для них работает слой 3.
- **Обфусцированный OkHttp.** Если приложение переименовало `okhttp3.*` через R8, интерцептор и
  DNS-обёртка не ставятся. DNS всё равно блокируется через `InetAddress`, а JSON — только через `org.json`.
- **Нативный сетевой стек** (Cronet и Chromium в Яндекс Браузере, собственный DNS в нативных библиотеках)
  обходит хуки `InetAddress`. Реклама на веб-страницах внутри браузера модулем не блокируется.
- **Собственная реклама приложения** (например, продвижение объявлений на Авито или «Промо» в выдаче)
  может приходить с тех же доменов, что и контент. Её режут только слои 3 и 4, и только если в JSON есть
  маркер или флаг, а в UI — текстовая метка.
- **Ложные срабатывания.** Флаг `isPromo` иногда означает акционную цену, а не рекламу. Если пропадает
  нужный контент, удалите флаг из `json_ad_keys.txt` или выключите слой.
- **Сетка (GridLayoutManager).** Схлопнутая ячейка может оставить пустое место в строке.
- **Числа в JSON.** Изменённый ответ пересериализуется через `org.json`. Целые числа длиннее 64 бит
  теряют точность. Ответы без рекламных ключей не пересериализуются.
- Настройки применяются только после перезапуска целевого приложения.

## Сборка

```bash
./gradlew assembleDebug assembleRelease testDebugUnitTest
```

APK появятся в `app/build/outputs/apk/{debug,release}/`.

### Подпись release в GitHub Actions

Добавьте секреты репозитория (Settings → Secrets and variables → Actions):

| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | алиас ключа |
| `KEY_PASSWORD` | пароль ключа |

Если секретов нет, release подписывается debug-ключом из `app/debug.keystore`. Ключ лежит в репозитории
специально, чтобы сборки из CI ставились поверх друг друга.

### Релиз

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Workflow соберёт APK и создаст GitHub Release с файлами.
