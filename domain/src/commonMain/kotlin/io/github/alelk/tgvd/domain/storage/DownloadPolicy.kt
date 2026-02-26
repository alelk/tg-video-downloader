package io.github.alelk.tgvd.domain.storage

data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    val preferredContainer: MediaContainer? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
) {
    enum class VideoQuality { BEST, HD_1080, HD_720, SD_480 }
}
