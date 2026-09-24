package io.github.alelk.tgvd.domain.storage

data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    /** null = inherit the global writeSubs/writeAutoSubs default; true/false = force on/off for this rule. */
    val downloadSubtitles: Boolean? = null,
    /** Empty = inherit the global subtitle languages. */
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
    /** Additional audio languages. null = inherit the global setting; empty = original track only. */
    val audioLanguages: List<String>? = null,
) {
    enum class VideoQuality { BEST, HD_1080, HD_720, SD_480 }

    val trackPreferences: TrackPreferences
        get() = TrackPreferences(audioLanguages, downloadSubtitles, subtitleLanguages.takeIf { it.isNotEmpty() })

    /** Applies higher-priority track [overrides] (e.g. from a channel) on top of this policy. */
    fun withOverrides(overrides: TrackPreferences?): DownloadPolicy {
        if (overrides == null) return this
        val merged = trackPreferences.overriddenBy(overrides)
        return copy(
            audioLanguages = merged.audioLanguages,
            downloadSubtitles = merged.downloadSubtitles,
            subtitleLanguages = merged.subtitleLanguages.orEmpty(),
        )
    }
}
