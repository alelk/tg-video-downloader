package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.trackPreferences(
    audioLanguages: Arb<List<String>?> = Arb.list(Arb.element("en", "ru", "de", "fr"), 0..2).orNull(0.4),
    downloadSubtitles: Arb<Boolean?> = Arb.boolean().orNull(0.4),
    subtitleLanguages: Arb<List<String>?> = Arb.list(Arb.element("en", "ru", "de", "fr"), 1..2).orNull(0.4),
): Arb<TrackPreferences> = arbitrary {
    TrackPreferences(
        audioLanguages = audioLanguages.bind(),
        downloadSubtitles = downloadSubtitles.bind(),
        subtitleLanguages = subtitleLanguages.bind(),
    )
}
