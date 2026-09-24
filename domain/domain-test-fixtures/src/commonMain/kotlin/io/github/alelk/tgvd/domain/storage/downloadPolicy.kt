package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.downloadPolicy(
    maxQuality: Arb<DownloadPolicy.VideoQuality> = Arb.enum<DownloadPolicy.VideoQuality>(),
    downloadSubtitles: Arb<Boolean?> = Arb.boolean().orNull(0.4),
    subtitleLanguages: Arb<List<String>> = Arb.list(Arb.element("en", "ru", "de", "fr"), 0..2),
    writeThumbnail: Arb<Boolean> = Arb.boolean(),
    audioLanguages: Arb<List<String>?> = Arb.list(Arb.element("en", "ru", "de", "fr"), 0..2).orNull(0.4),
): Arb<DownloadPolicy> = arbitrary {
    DownloadPolicy(
        maxQuality = maxQuality.bind(),
        downloadSubtitles = downloadSubtitles.bind(),
        subtitleLanguages = subtitleLanguages.bind(),
        writeThumbnail = writeThumbnail.bind(),
        audioLanguages = audioLanguages.bind(),
    )
}

