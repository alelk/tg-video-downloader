package io.github.alelk.tgvd.server.infra.config

import io.github.alelk.tgvd.domain.track.TrackSelectionSettings

/** The part of [YtDlpConfig] that decides which audio and subtitle tracks are downloaded. */
fun YtDlpConfig.toTrackSelectionSettings(): TrackSelectionSettings = TrackSelectionSettings(
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
