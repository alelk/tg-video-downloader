package io.github.alelk.tgvd.domain.storage

data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    /** null = inherit the global writeSubs/writeAutoSubs default; true/false = force on/off for this rule. */
    val downloadSubtitles: Boolean? = null,
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
) {
    enum class VideoQuality { BEST, HD_1080, HD_720, SD_480 }
}
