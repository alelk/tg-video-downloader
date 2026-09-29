package io.github.alelk.tgvd.domain.track

/**
 * Object mother for [TrackSelectionSettings]. The defaults are the server's built-in defaults
 * (`YtDlpConfig` in `server:infra`), so a test names only the settings it is about.
 */
fun aTrackSelectionSettings(
    preferredAudioLanguages: List<String> = emptyList(),
    maxAdditionalAudioTracks: Int = 2,
    originalAudioLanguage: String? = null,
    writeSubs: Boolean = true,
    writeAutoSubs: Boolean = true,
    preferredSubtitleLanguages: List<String> = listOf("ru", "en"),
    subLangs: String? = null,
    embedSubs: Boolean = false,
    sleepSubtitles: Int? = 3,
): TrackSelectionSettings = TrackSelectionSettings(
    preferredAudioLanguages = preferredAudioLanguages,
    maxAdditionalAudioTracks = maxAdditionalAudioTracks,
    originalAudioLanguage = originalAudioLanguage,
    writeSubs = writeSubs,
    writeAutoSubs = writeAutoSubs,
    preferredSubtitleLanguages = preferredSubtitleLanguages,
    subLangs = subLangs,
    embedSubs = embedSubs,
    sleepSubtitles = sleepSubtitles,
)
