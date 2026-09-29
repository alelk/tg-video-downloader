package io.github.alelk.tgvd.domain.preview

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.track.AudioTrackSelector
import io.github.alelk.tgvd.domain.track.SubtitleSelector
import io.github.alelk.tgvd.domain.track.TrackSelectionSettings
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo

/**
 * The tracks pre-selected on the preview screen: what a download with [policy] and [settings] would
 * pick automatically.
 *
 * - `audioFormatIds` — the automatically selected audio tracks; null when there is nothing to pick
 *   (no separate audio tracks).
 * - `subtitleLanguages` — the languages the video offers that the effective subtitle policy asks
 *   for (a wanted `en` also matches an offered `en-US`); empty when subtitles are off.
 */
fun defaultMediaSelection(
    videoInfo: VideoInfo,
    policy: DownloadPolicy,
    settings: TrackSelectionSettings,
): MediaSelection {
    val audio =
        AudioTrackSelector
            .select(videoInfo.availableFormats, policy, settings)
            .audioTracks
            .map { it.formatId }
    val subtitles = SubtitleSelector.select(settings, policy)
    val subtitleLanguages =
        if (subtitles.enabled) {
            videoInfo.subtitleTracks
                .map { it.language }
                .distinct()
                .filter { available ->
                    subtitles.languages.any { wanted ->
                        available.equals(wanted, ignoreCase = true) ||
                            available.startsWith("$wanted-", ignoreCase = true)
                    }
                }
        } else {
            emptyList()
        }
    return MediaSelection(audioFormatIds = audio.takeIf { it.isNotEmpty() }, subtitleLanguages = subtitleLanguages)
}
