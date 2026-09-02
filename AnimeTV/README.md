# AnimeTV

A native Android TV / Google TV app for discovering and watching anime: search across
multiple metadata sources, browse seasons and episodes, track favorites and watch history,
and play video through a full Media3/ExoPlayer pipeline with resume, quality switching,
subtitle/audio track selection and auto-play-next — all built for a D-pad, from a couch.

> **Read this before you judge the "watch" feature as fake or real.** This project ships
> real, working search/metadata (MyAnimeList via Jikan, AniList) and a fully real playback
> pipeline (adaptive HLS, quality/track switching, resume, autoplay). What it does **not**
> ship is a licensed video backend, because none exists that a hobbyist/open-source project
> can legally call without a commercial partner agreement. See
> [Sources & limitations](#sources--limitations) for exactly what that means and how to plug
> in a real one.

---

## Table of contents

- [What's implemented](#whats-implemented)
- [Sources & limitations](#sources--limitations)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Building locally](#building-locally)
- [Building via GitHub Actions](#building-via-github-actions)
- [Installing the APK on Android TV / Google TV](#installing-the-apk-on-android-tv--google-tv)
- [Adding a new source](#adding-a-new-source)
- [Release signing](#release-signing)
- [Permissions](#permissions)
- [Known limitations](#known-limitations)

---

## What's implemented

- **Discovery**: Home with Continue Watching, Trending, Popular, Recently Added and
  Recommended shelves, plus a hero banner for a featured title.
- **Search**: debounced, cancels stale in-flight requests, merges + deduplicates results
  from every metadata source, keeps a local search history, sorts by relevance/title/year/
  rating.
- **Details**: synopsis, genres, year, status, rating, episode count, season/episode list
  with per-episode watch progress, favorite toggle, and links to the title's **official**
  licensed streaming pages (Crunchyroll, Netflix, HIDIVE, ...) when AniList publishes them.
- **Player**: Media3/ExoPlayer with adaptive-bitrate HLS, manual quality override, audio and
  subtitle track cycling, ±10s/±30s seek, playback speed, resume-from-last-position,
  next/previous episode, and an auto-play-next countdown you can cancel.
- **Favorites**: local, persisted, sortable (recently added / title / year).
- **History**: every episode you've opened, with position/duration, resumable, clearable.
- **Settings**: playback (quality, autoplay, skip opening/ending, preferred source),
  appearance (language, text size, animations), network (Wi-Fi only), storage (clear image
  cache / metadata cache / history).
- **Offline handling**: a live connectivity observer drives a friendly "you're offline"
  state with retry, and screens automatically retry the moment connectivity returns.
- **Onboarding**: welcome → language → quality → preferred source, skippable.
- **Localization**: English and Russian, fully wired through `strings.xml` (no hardcoded UI
  text).
- **TV-first UX**: D-pad-only navigation everywhere, a persistent focus scale + glow
  indicator on every focusable element, safe-margin layout, large 10-foot-readable
  typography, a persistent left-edge navigation rail.

## Sources & limitations

The app's source layer is deliberately modular (`domain/source/SourceProvider.kt`) so a
title can be resolved through more than one provider, with automatic fallback and
deduplication. Three roles are wired up today:

| Role | Provider | What it is |
|---|---|---|
| Search + metadata | **Jikan** (`api.jikan.moe`) | Free, key-less, public REST wrapper around MyAnimeList. No auth, published rate limit (~3 req/s), used as the primary source. |
| Search + metadata + official links | **AniList** (`graphql.anilist.co`) | Free, key-less, public GraphQL API. Used as the metadata fallback, and as the source of `externalLinks(type: STREAMING)` — direct links to a title's *official* licensed streaming pages. |
| Streaming | **Demo sample source** | See below. |

**Why there's no real video streaming backend.** Every legitimate anime streaming catalog
(Crunchyroll, Netflix, HIDIVE, ...) requires a commercial partner agreement and an
authenticated backend to serve video — there is no free, public, ToS-compliant API that
returns actual licensed episode video. This project's brief explicitly forbids scraping,
DRM bypass, or impersonating a browser against a site that disallows automated access, so it
does not attempt any of that.

What's shipped instead is `DemoStreamingSource`
(`data/source/demo/DemoStreamingSource.kt`): a fully wired `StreamingProvider` that proves
the real playback pipeline end-to-end — adaptive HLS, quality switching, resume, next/prev,
autoplay — using openly licensed (Creative Commons / publisher demo) sample streams. It is
the last link in the fallback chain, and `PlaybackSource.isDemoContent = true` makes the
Player screen show a visible **"Demo source"** badge whenever it's what's actually playing,
so the app never pretends this is the real episode.

**To go live**: implement `StreamingProvider` (four methods:
`getStreams(externalId, season, episode)`, plus `id`/`displayName`/`capabilities`) against
your licensed backend and register it ahead of `DemoStreamingSource` in
`di/SourceModule.kt`. Nothing else in the app needs to change — screens, the player, and
history/favorites/progress tracking all consume `PlaybackSource` generically.

## Architecture

Kotlin, Jetpack Compose (Material3, dark theme tuned for a 10-foot UI), MVVM + a light Clean
Architecture split:

```
ui/            Compose screens + ViewModels (Hilt-injected, StateFlow-driven)
domain/        Platform-agnostic models, repository interfaces, source interfaces
data/          Repository implementations, Room, DataStore, Retrofit/GraphQL clients,
               and the concrete source adapters (Jikan / AniList / Demo)
di/            Hilt modules wiring it all together
```

- **Coroutines + Flow** everywhere; no callbacks.
- **Hilt** for DI (`di/*Module.kt`).
- **Room** for favorites, watch history/progress, the anime search-result cache, and search
  history (`data/local/db`).
- **DataStore Preferences** for settings (`data/local/datastore`).
- **Retrofit + kotlinx.serialization** for Jikan (REST) and AniList (GraphQL over a single
  POST endpoint).
- **Media3 (ExoPlayer)** for playback, including HLS.
- **Coil** for image loading with disk/memory caching.
- **Navigation Compose** for the nav graph (`ui/navigation`).

### The source abstraction

```kotlin
interface SourceProvider { val id: String; val displayName: String; val capabilities: SourceCapabilities }
interface SearchProvider : SourceProvider { suspend fun search(query: String): SourceResult<List<AnimeSummary>> }
interface AnimeMetadataProvider : SourceProvider { suspend fun getDetails(externalId: String): SourceResult<AnimeDetails> }
interface EpisodeProvider : SourceProvider { suspend fun getSeasons(externalId: String): SourceResult<List<Season>> }
interface StreamingProvider : SourceProvider { suspend fun getStreams(externalId: String, season: Int, episode: Int): SourceResult<PlaybackSource> }
```

`SearchAggregator` fans a query out to every `SearchProvider` in parallel and merges results
by normalized title (`TitleNormalizer`), unioning each match's source refs. `MetadataRegistry`
/ `EpisodeRegistry` / `StreamingRegistry` each wrap a `FallbackChain`: given a title's known
`SourceRef`s, they try providers in order and return the first success — "Source A → if it
fails → Source B → Source C", exactly as specified. Settings → Playback → "preferred source"
reorders that chain per-request without changing the underlying wiring.

## Project structure

```
AnimeTV/
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml        # version catalog - every dependency version lives here
│   └── wrapper/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/qmurzik/animetv/
│       │   ├── domain/            # models, repository + source interfaces
│       │   ├── data/               # repositories, Room, DataStore, network, sources
│       │   ├── di/                 # Hilt modules
│       │   ├── ui/                 # theme, navigation, components, screens+viewmodels
│       │   ├── util/                # connectivity observer, rate limiter
│       │   ├── AnimeTvApplication.kt
│       │   └── MainActivity.kt
│       └── res/                    # strings (en, ru), theme colors, TV banner/icon
└── README.md
```

## Building locally

**Requirements**: JDK 17, Android SDK with platform 34 + build-tools 34.0.0 (Android Studio
manages this for you), an internet connection (Gradle needs to resolve dependencies from
`google()`/Maven Central on first sync).

```bash
git clone <this repo>
cd AnimeTV
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the `AnimeTV/` directory directly in Android Studio (Iguana+) and run the `app`
configuration on an Android TV emulator or a real device (see below).

No API keys are required to build or run: Jikan and AniList are both key-less public APIs.

## Building via GitHub Actions

`.github/workflows/build.yml` (repo root) runs on every push/PR that touches `AnimeTV/`:

1. Checks out the repo.
2. Installs JDK 17 (Temurin).
3. Installs the exact Android SDK platform/build-tools this project targets.
4. Sets up the Gradle build cache (`gradle/actions/setup-gradle`).
5. Runs `lintDebug`, `testDebugUnitTest`, then `assembleDebug`.
6. Uploads the debug APK (and the lint report) as workflow artifacts.
7. On `main` only, and only if release-signing secrets are configured (see
   [Release signing](#release-signing)), also builds and uploads a signed **release** APK.

**Where to get the APK**: open the workflow run under the repo's **Actions** tab → the run
for your commit → **Artifacts** at the bottom of the summary page → download
`animetv-debug-apk` (or `animetv-release-apk` on `main` with signing configured).

## Installing the APK on Android TV / Google TV

Android TV has no built-in "install unknown APK" file browser, so sideload with `adb` from a
computer on the same network as the TV:

```bash
# On the TV: Settings → Device Preferences → About → click "Build" 7x to enable Developer
# options, then Settings → Device Preferences → Developer options → enable
# "USB debugging" (or "Network debugging" if present) and note the TV's IP address.

adb connect <tv-ip-address>:5555
adb install -r app-debug.apk
```

Alternatively, sideload via an app like "Send Files to TV" / "Downloader" if you'd rather not
use `adb`. The app appears on the Android TV home screen's app row (it declares the
`LEANBACK_LAUNCHER` category and a TV banner) once installed.

## Adding a new source

1. Implement one or more of `SearchProvider` / `AnimeMetadataProvider` / `EpisodeProvider` /
   `StreamingProvider` from `domain/source/SourceProvider.kt` in a new class under
   `data/source/<your-source>/`.
2. Set `capabilities` accurately (e.g. `subtitles = true` only if you actually return
   subtitle tracks) — the UI trusts these flags to decide what controls to show.
3. Return `SourceResult.Success` / `SourceResult.Failure` (never throw across the interface -
   wrap your implementation body in `sourceResultOf(id) { ... }`, which does this for you and
   maps common exceptions to a `SourceError` the UI can render safely).
4. Register your provider in `di/SourceModule.kt`, in the list(s) for the role(s) it
   implements, in the position you want it tried (earlier = higher priority in the fallback
   chain).

That's it — no other code needs to change. Search aggregation, fallback, caching, favorites,
history and the player all consume the domain interfaces, not any specific provider.

## Release signing

Never commit a real keystore or its passwords. Two supported paths, both read by
`app/build.gradle.kts`:

**Local builds** — create `AnimeTV/keystore.properties` (git-ignored):

```properties
ANIMETV_KEYSTORE_PATH=/absolute/path/to/your.jks
ANIMETV_KEYSTORE_PASSWORD=...
ANIMETV_KEY_ALIAS=...
ANIMETV_KEY_PASSWORD=...
```

Generate a keystore if you don't have one:

```bash
keytool -genkeypair -v -keystore release.jks -alias animetv \
  -keyalg RSA -keysize 2048 -validity 10000
```

**CI (GitHub Actions)** — add these **repository secrets** (Settings → Secrets and
variables → Actions):

| Secret | Value |
|---|---|
| `ANIMETV_KEYSTORE_BASE64` | `base64 -w0 release.jks` output |
| `ANIMETV_KEYSTORE_PASSWORD` | keystore password |
| `ANIMETV_KEY_ALIAS` | key alias |
| `ANIMETV_KEY_PASSWORD` | key password |

The release job decodes the keystore into a temporary file, writes a temporary
`keystore.properties`, builds, uploads the APK, then deletes both — nothing signed-related
is ever persisted in the repo or the job's cache. Without these secrets configured, the
release job is skipped entirely and only the debug build runs (debug builds are automatically
signed with the standard Android debug key, which is fine for sideloading/testing).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | All metadata/search/streaming calls. |
| `ACCESS_NETWORK_STATE` | Drives the offline/retry UI (item 13). |
| `WAKE_LOCK` | Keeps playback alive during video. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reserved for Media3's
  playback-service integration; the app does not currently background-play, but declares
  these so that capability can be enabled later (e.g. picture-in-picture, background audio)
  without a manifest change. |

No storage, camera, microphone, contacts, or location permissions are requested.

## Known limitations

- **Streaming video itself is a demo/sample source**, not real licensed anime footage — see
  [Sources & limitations](#sources--limitations). This is the single most important thing to
  understand about this project before deploying it for real use.
- **Seasons**: both Jikan/MyAnimeList and AniList model a sequel season as a *separate*
  catalog entry rather than a season nested under one entry, so this app reports one season
  per matched entry; a sequel season appears as its own search result, matching how MAL/
  AniList themselves present it.
- **Skip Opening/Ending**: the UI and settings toggle are wired up, but no metadata source
  used here publishes real per-episode OP/ED timestamps, so the skip buttons simply never
  appear (per the brief: never guess at timing aggressively). A real streaming partner API
  that does publish these intervals just needs to populate `PlaybackSource.openingSkip` /
  `endingSkip` and the existing UI will use them.
- **Jikan rate limits**: the bundled client throttles itself to stay under Jikan's published
  ~3 req/s budget; very rapid navigation can still occasionally hit a 429, which surfaces as
  the standard "source is busy, try again" error with a retry action, and typically also
  falls back to AniList.
