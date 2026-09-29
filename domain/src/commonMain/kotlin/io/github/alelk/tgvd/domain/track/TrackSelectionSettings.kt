package io.github.alelk.tgvd.domain.track

/**
 * The global (server-wide) settings that decide which audio and subtitle tracks a download gets.
 *
 * Rule and channel overrides ([io.github.alelk.tgvd.domain.storage.DownloadPolicy]) are applied on
 * top of these by [AudioTrackSelector] and [SubtitleSelector].
 *
 * @param preferredAudioLanguages audio languages to add next to the source-original track;
 *   empty = original track only.
 * @param maxAdditionalAudioTracks cap on non-original audio tracks picked from [preferredAudioLanguages].
 * @param originalAudioLanguage pins the language treated as the original track when the video offers it;
 *   null = trust the extractor's own "original" marker.
 * @param writeSubs download regular subtitles by default.
 * @param writeAutoSubs download automatic (generated) captions by default.
 * @param preferredSubtitleLanguages subtitle languages to download when available.
 * @param subLangs legacy comma-separated subtitle languages; when set, takes precedence over
 *   [preferredSubtitleLanguages].
 * @param embedSubs embed downloaded subtitles into the container.
 * @param sleepSubtitles seconds to sleep between subtitle requests when both regular and automatic
 *   captions are fetched; null = no sleep.
 */
data class TrackSelectionSettings(
    val preferredAudioLanguages: List<String>,
    val maxAdditionalAudioTracks: Int,
    val originalAudioLanguage: String?,
    val writeSubs: Boolean,
    val writeAutoSubs: Boolean,
    val preferredSubtitleLanguages: List<String>,
    val subLangs: String?,
    val embedSubs: Boolean,
    val sleepSubtitles: Int?,
)
