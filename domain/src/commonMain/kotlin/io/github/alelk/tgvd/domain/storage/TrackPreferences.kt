package io.github.alelk.tgvd.domain.storage

/**
 * Optional overrides of which audio and subtitle tracks to download.
 *
 * Levels are layered from lowest to highest priority: global settings → rule ([DownloadPolicy]) → channel.
 * A `null` field inherits the value from the lower-priority level.
 *
 * Requested languages are best-effort: a language the video doesn't offer is silently skipped.
 *
 * @param audioLanguages additional (dubbed/translated) audio languages to add when available.
 *   The source-original track is always downloaded; empty = original track only.
 * @param downloadSubtitles force subtitles on (`true`) or off (`false`).
 * @param subtitleLanguages subtitle languages (including auto-generated/auto-translated captions).
 *   An empty list is treated as `null` (inherit).
 */
data class TrackPreferences(
    val audioLanguages: List<String>? = null,
    val downloadSubtitles: Boolean? = null,
    val subtitleLanguages: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = audioLanguages == null && downloadSubtitles == null && subtitleLanguages.isNullOrEmpty()

    /** Returns these preferences with every set field of [override] taking precedence. */
    fun overriddenBy(override: TrackPreferences?): TrackPreferences =
        if (override == null) this
        else TrackPreferences(
            audioLanguages = override.audioLanguages ?: audioLanguages,
            downloadSubtitles = override.downloadSubtitles ?: downloadSubtitles,
            subtitleLanguages = override.subtitleLanguages?.takeIf { it.isNotEmpty() } ?: subtitleLanguages,
        )
}
