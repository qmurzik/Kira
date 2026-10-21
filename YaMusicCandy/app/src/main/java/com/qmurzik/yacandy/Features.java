package com.qmurzik.yacandy;

/**
 * What the module knows about Yandex Music internals that keep their names across releases
 * (studied on 2026.09.5 #167): Gson DTO classes of the "skeleton" screens and Retrofit API
 * interfaces. Shared by the settings app (labels) and the hook side (matching).
 */
public final class Features {
    private Features() {
    }

    /** A user-facing group of skeleton block DTOs (the building blocks of the main screen). */
    public static final class BlockGroup {
        public final String id;
        public final String title;
        public final String desc;
        public final String[] dtos;

        BlockGroup(String id, String title, String desc, String... dtos) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.dtos = dtos;
        }
    }

    public static final BlockGroup[] BLOCK_GROUPS = {
            new BlockGroup("promo", "Промо и спецпроекты", "Рекламные подборки и спецпроекты",
                    "PromoBlockDto", "SpecialBlockDto"),
            new BlockGroup("featured", "Выбор редакции", "Большие карточки с подборками от редакции",
                    "FeaturedBlockDto"),
            new BlockGroup("pay", "Предложения подписки", "Карточки тарифов и скидок Плюса",
                    "PayOffersBlockDto"),
            new BlockGroup("banners", "Баннеры", "Обычные и динамические баннеры, «импортируйте музыку»",
                    "BannerBlockDto", "DynamicBannerBlockDto", "ImportMusicBannerBlockDto"),
            new BlockGroup("quests", "Квесты Плюса", null, "QuestsBlockDto"),
            new BlockGroup("nonmusic", "Подкасты и книги", "Баннеры и «продолжить слушать» для немузыкального контента",
                    "NonMusicBannerBlockDto", "ContinueListenBlockDto"),
            new BlockGroup("concerts", "Концерты", null, "ConcertsBlockDto", "ArtistConcertsBlockDto"),
            new BlockGroup("clips", "Клипы", null, "ClipBlockDto"),
            new BlockGroup("donations", "Донаты артистам", null, "DonationsBlockDto", "ArtistDonationsBlockDto"),
            new BlockGroup("wizard", "Подбор вкуса (визард)", "Карточки «выберите любимых артистов»",
                    "WizardBlockDto", "InlineWizardBlockDto"),
            new BlockGroup("artistpromo", "Промо на странице артиста", "Пресейвы и «рекомендует артист»",
                    "ArtistRecommendationsPromoBlockDto", "ArtistPreSaveBlockDto"),
            new BlockGroup("div", "Серверные div-блоки", "Вёрстка, которую сервер присылает целиком; чаще всего промо",
                    "StaticDivBlockDto"),
            new BlockGroup("waves", "Подборки волн", "Карусели волн и агентов на главной",
                    "WavesBlockDto", "WaveAgentsBlockDto", "EditorialWavesBlockDto",
                    "EditorialWaveAgentsBlockDto", "CollectionWaveAgentBlockDto"),
            new BlockGroup("releases", "Новые релизы", null, "NewReleasesBlockDto"),
            new BlockGroup("mixes", "Миксы и подборки", null, "MixesBlockDto", "CarouselBlockDto"),
    };

    // Retrofit interfaces whose calls the module fails on purpose (full names).
    public static final String[] API_POPUPS = {
            "com.yandex.music.shared.bdu.trigger.network.TriggerApi",
            "com.yandex.music.shared.bdui.trigger.network.TriggerApi",
    };
    public static final String[] API_SPLASH = {
            "com.yandex.music.shared.splash.screen.network.SplashScreenApi",
    };
    public static final String[] API_SPECIAL_HEADER = {
            "com.yandex.music.screen.landing.header.data.SpecialHeaderApi",
    };
    public static final String[] API_INFORMERS = {
            "com.yandex.music.shared.player.informer.data.network.PlayerInformersApi",
    };
    public static final String[] API_PB_SIMILAR = {
            "com.yandex.music.shared.player.screen.skeleton.blocks.data.similar.tracks.SimilarTracksApi",
    };
    public static final String[] API_PB_ARTISTS = {
            "com.yandex.music.shared.player.screen.skeleton.blocks.data.artistsinfo.ArtistsInfoBlockApi",
    };
    public static final String[] API_PB_CONCERTS = {
            "com.yandex.music.shared.player.screen.skeleton.blocks.data.concerts.ArtistsWithConcertsApi",
    };
    public static final String[] API_PB_NONMUSIC = {
            "com.yandex.music.shared.player.screen.skeleton.blocks.data.nonmusic.book.AboutBookPlayerBlockApi",
            "com.yandex.music.shared.player.screen.skeleton.blocks.data.nonmusic.podcast.AboutPodcastPlayerBlockApi",
            "com.yandex.music.shared.skeleton.blocks.episode.AboutPodcastEpisodePlayerBlockApi",
    };
    public static final String[] API_WAVE_WORDS = {
            "com.yandex.music.shared.wave.screen.wordsinwave.data.WaveScreenWordsInWaveApi",
    };

    // Backend paths of the same endpoints (Retrofit annotation values survive obfuscation and
    // interface renames). A trailing '/' means "starts with", otherwise the path must match exactly.
    public static final String[] PATH_POPUPS = {
            "dynamic-pages/v2/trigger/", "dynamic-pages/v3/trigger/", "dynamic-pages/mediacrm/",
    };
    public static final String[] PATH_SPLASH = {"dynamic-pages/splash"};
    public static final String[] PATH_SPECIAL_HEADER = {"landing/special-header"};
    public static final String[] PATH_INFORMERS = {"player-informers"};
    public static final String[] PATH_PB_SIMILAR = {"tracks/{trackId}/similar"};
    public static final String[] PATH_PB_ARTISTS = {"artists/blocks/player-info"};
    public static final String[] PATH_PB_CONCERTS = {"concerts/landing/artists"};
    public static final String[] PATH_PB_NONMUSIC = {
            "non-music/blocks/books/{albumId}/about", "non-music/blocks/podcasts/{albumId}/about",
            "non-music/blocks/podcasts/episodes/{trackId}/about",
    };
    public static final String[] PATH_WAVE_WORDS = {"words/cards", "words/big-cards", "words/cards/feedback"};

    /** Third-party trackers; resolved to "no such host" when telemetry is off. */
    public static final String[] TRACKER_HOSTS = {
            "appsflyer", "app.adjust.com", "crashlytics.com", "firebaselogging", "app-measurement.com",
            "graph.facebook.com", "report.appmetrica", "webvisor", "mc.yandex.ru", "log.strm.yandex.ru",
            "tracking.ott.yandex.net",
    };

    /** Notification channels used by marketing pushes (AppMetrica Push and FCM fallback). */
    public static final String[] PROMO_PUSH_CHANNELS = {
            "yandex_metrica_push_v2", "fcm_fallback_notification_channel",
    };
}
