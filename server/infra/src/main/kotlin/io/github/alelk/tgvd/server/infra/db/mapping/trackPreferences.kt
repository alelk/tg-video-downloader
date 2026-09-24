package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.server.infra.db.model.TrackPreferencesPm

internal fun TrackPreferences.toPm(): TrackPreferencesPm =
    TrackPreferencesPm(
        audioLanguages = audioLanguages,
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

internal fun TrackPreferencesPm.toDomain(): TrackPreferences =
    TrackPreferences(
        audioLanguages = audioLanguages,
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )
