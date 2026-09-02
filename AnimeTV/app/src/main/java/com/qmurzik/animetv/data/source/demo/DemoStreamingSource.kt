package com.qmurzik.animetv.data.source.demo

import com.qmurzik.animetv.domain.model.PlaybackSource
import com.qmurzik.animetv.domain.model.StreamVariant
import com.qmurzik.animetv.domain.source.SourceCapabilities
import com.qmurzik.animetv.domain.source.SourceResult
import com.qmurzik.animetv.domain.source.StreamingProvider
import com.qmurzik.animetv.domain.source.sourceResultOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * IMPORTANT - read before wiring a real backend, see also README "Sources & limitations".
 *
 * There is no free, key-less, ToS-compliant API that serves actual licensed anime episode
 * video - real streaming partners (Crunchyroll, Netflix, HIDIVE, ...) require a commercial
 * partner agreement and authenticated backend integration that this open-source project does
 * not have access to. Per the project brief this app must never scrape, bypass DRM, or
 * impersonate a browser against a site that forbids automated access.
 *
 * What this class *is*: a fully wired [StreamingProvider] that proves the real playback
 * pipeline end-to-end - adaptive-bitrate HLS via Media3/ExoPlayer, quality switching, resume,
 * next/previous episode, autoplay - using openly licensed (Creative Commons / publisher demo)
 * sample streams. It is the last link in the fallback chain and is intentionally flagged via
 * [PlaybackSource.isDemoContent] = true so the Player screen can show a clear "Demo source"
 * badge instead of quietly pretending this is the real episode (see task rule: never fake
 * functionality and pass it off as real).
 *
 * To go live: implement a new [StreamingProvider] against your licensed backend (see
 * README "Adding a new source") and register it in SourceModule ahead of this one in the
 * fallback order - no other code needs to change.
 */
@Singleton
class DemoStreamingSource @Inject constructor() : StreamingProvider {

    override val id: String = "demo"
    override val displayName: String = "Demo sample source"
    override val capabilities = SourceCapabilities(
        streaming = true,
        adaptiveBitrate = true,
        subtitles = true,
        multipleAudioTracks = true,
    )

    override suspend fun getStreams(
        externalId: String,
        seasonNumber: Int,
        episodeNumber: Int,
    ): SourceResult<PlaybackSource> = sourceResultOf(id) {
        val sample = SAMPLE_STREAMS[(externalId.hashCode().absoluteValue + episodeNumber) % SAMPLE_STREAMS.size]
        PlaybackSource(
            providerId = id,
            providerLabel = displayName,
            variants = listOf(
                StreamVariant(url = sample.url, qualityLabel = "Auto (adaptive)", isAdaptive = true),
            ),
            isDemoContent = true,
        )
    }

    private data class Sample(val url: String, val attribution: String)

    companion object {
        // Openly licensed / publisher demo HLS assets, used only to exercise the player.
        private val SAMPLE_STREAMS = listOf(
            Sample(
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
                attribution = "Apple HLS adaptive-streaming reference sample",
            ),
            Sample(
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                attribution = "Sintel - (CC) Blender Foundation, via Mux test streams",
            ),
        )
    }
}
