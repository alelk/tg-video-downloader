package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig

/** Pure format selection policy. Language decides identity; bitrate only ranks variants of that language. */
object AudioTrackSelector {
    data class Selection(
        val video: VideoInfo.Format?,
        val originalAudio: VideoInfo.Format?,
        val additionalAudio: List<VideoInfo.Format>,
        val combined: VideoInfo.Format? = null,
    ) {
        val audioTracks: List<VideoInfo.Format> = listOfNotNull(originalAudio) + additionalAudio
        val formatSelector: String? = when {
            video != null && audioTracks.isNotEmpty() ->
                (listOf(video.formatId) + audioTracks.map { it.formatId }).joinToString("+")
            combined != null -> combined.formatId
            video != null -> video.formatId
            originalAudio != null -> originalAudio.formatId
            else -> null
        }
    }

    /**
     * Automatic selection for a download [policy]. Its `audioLanguages` (the rule/channel override)
     * replaces the global `preferredAudioLanguages` and is not capped by `maxAdditionalAudioTracks`.
     * Only tracks the video actually offers are selected, so a missing language is simply skipped.
     */
    fun select(formats: List<VideoInfo.Format>, policy: DownloadPolicy, config: YtDlpConfig): Selection {
        val override = policy.audioLanguages
        return select(
            formats = formats,
            quality = policy.maxQuality,
            preferredLanguages = override ?: config.preferredAudioLanguages,
            maxAdditionalTracks = override?.size ?: config.maxAdditionalAudioTracks,
            assumedOriginalLanguage = config.originalAudioLanguage,
        )
    }

    fun select(
        formats: List<VideoInfo.Format>,
        quality: DownloadPolicy.VideoQuality,
        preferredLanguages: List<String>,
        maxAdditionalTracks: Int,
        assumedOriginalLanguage: String? = null,
    ): Selection {
        val maxHeight = when (quality) {
            DownloadPolicy.VideoQuality.BEST -> Int.MAX_VALUE
            DownloadPolicy.VideoQuality.HD_1080 -> 1080
            DownloadPolicy.VideoQuality.HD_720 -> 720
            DownloadPolicy.VideoQuality.SD_480 -> 480
        }
        val videoOnly = formats.filter { it.hasVideo && !it.hasAudio }
        val audioOnly = formats.filter { it.hasAudio && !it.hasVideo }
        val combined = formats.filter { it.hasVideo && it.hasAudio }

        val videoComparator = compareByDescending<VideoInfo.Format> { it.height ?: 0 }
            .thenByDescending { it.width ?: 0 }
            .thenByDescending { it.tbr ?: 0.0 }
            .thenByDescending { it.fps ?: 0.0 }
        val bestVideo = videoOnly.filter { (it.height ?: 0) <= maxHeight }
            .minWithOrNull(videoComparator)
            ?: videoOnly.minByOrNull { it.height ?: 0 }

        val audioComparator = compareByDescending<VideoInfo.Format> { it.languagePreference ?: Int.MIN_VALUE }
            .thenByDescending { it.tbr ?: 0.0 }
            .thenByDescending { it.audioChannels ?: 0 }
        val languagePreferences = audioOnly.mapNotNull { it.languagePreference }.distinct()

        // A pinned language always wins when a matching track exists. yt-dlp's own
        // is_original/is_default signal for YouTube can be wrong — YouTube may report a dub as
        // "default" depending on the requester's account/locale — so this is the user's escape
        // hatch to guarantee the correct track is never silently swapped for a translation.
        val pinnedOriginal = assumedOriginalLanguage.normalizedLanguage()?.let { pinned ->
            audioOnly.filter { candidate -> candidate.language.normalizedLanguage()?.let { languageMatches(it, pinned) } == true }
                .minWithOrNull(audioComparator)
        }

        val original = pinnedOriginal
            ?: audioOnly.filter { it.isOriginalAudio }.minWithOrNull(audioComparator)
            ?: if (languagePreferences.size > 1) {
                val bestPreference = languagePreferences.max()
                audioOnly.filter { it.languagePreference == bestPreference }.minWithOrNull(audioComparator)
            } else {
                // With no useful source preference, format order is a safer proxy for
                // the source-primary track than bitrate (which may favour a dub).
                audioOnly.firstOrNull()
            }

        val originalLanguage = original?.language.normalizedLanguage()
        val selectedIds = mutableSetOf<String>()
        original?.let { selectedIds += it.formatId }
        val additional = preferredLanguages
            .asSequence()
            .mapNotNull { it.normalizedLanguage() }
            .distinct()
            .filter { wanted -> originalLanguage == null || !languageMatches(originalLanguage, wanted) }
            .mapNotNull { wanted ->
                audioOnly.asSequence()
                    .filter { it.formatId !in selectedIds }
                    .filter { candidate -> candidate.language.normalizedLanguage()?.let { languageMatches(it, wanted) } == true }
                    .minWithOrNull(audioComparator)
                    ?.also { selectedIds += it.formatId }
            }
            .take(maxAdditionalTracks.coerceAtLeast(0))
            .toList()

        val bestCombined = combined.filter { (it.height ?: 0) <= maxHeight }.minWithOrNull(videoComparator)
            ?: combined.minByOrNull { it.height ?: 0 }
        return Selection(bestVideo, original, additional, combined = if (bestVideo == null || original == null) bestCombined else null)
    }

    private val VideoInfo.Format.hasVideo: Boolean get() = !vcodec.isNullOrBlank() && vcodec != "none"
    private val VideoInfo.Format.hasAudio: Boolean get() = !acodec.isNullOrBlank() && acodec != "none"

    private fun String?.normalizedLanguage(): String? = this
        ?.trim()
        ?.replace('_', '-')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() && it != "und" }

    private fun languageMatches(actual: String, preferred: String): Boolean =
        actual == preferred || actual.startsWith("$preferred-") || preferred.startsWith("$actual-")
}
