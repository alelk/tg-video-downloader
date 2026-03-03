package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.downloadPolicy(
    maxQuality: Arb<DownloadPolicy.VideoQuality> = Arb.enum<DownloadPolicy.VideoQuality>(),
    preferredContainer: Arb<MediaContainer?> = Arb.mediaContainer().orNull(0.4),
    downloadSubtitles: Arb<Boolean> = Arb.boolean(),
    subtitleLanguages: Arb<List<String>> = Arb.list(Arb.element("en", "ru", "de", "fr"), 0..2),
    writeThumbnail: Arb<Boolean> = Arb.boolean(),
): Arb<DownloadPolicy> = arbitrary {
    DownloadPolicy(
        maxQuality = maxQuality.bind(),
        preferredContainer = preferredContainer.bind(),
        downloadSubtitles = downloadSubtitles.bind(),
        subtitleLanguages = subtitleLanguages.bind(),
        writeThumbnail = writeThumbnail.bind(),
    )
}

