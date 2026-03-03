package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.outputRule(
    pathTemplate: Arb<String> = Arb.element(
        "~/Downloads/{channelName}/{title} [{videoId}].{ext}",
        "~/Media/{artist}/{title}.{ext}",
        "~/Videos/{year}/{date} {title}.{ext}",
        "/media/converted/{title}.mp4",
    ),
    format: Arb<OutputFormat> = Arb.outputFormat(),
    maxQuality: Arb<DownloadPolicy.VideoQuality?> = Arb.element(null, *DownloadPolicy.VideoQuality.entries.toTypedArray()),
    embedThumbnail: Arb<Boolean> = Arb.boolean(),
    embedMetadata: Arb<Boolean> = Arb.boolean(),
    embedSubtitles: Arb<Boolean> = Arb.boolean(),
    normalizeAudio: Arb<Boolean> = Arb.boolean(),
): Arb<OutputRule> = arbitrary {
    OutputRule(
        pathTemplate = pathTemplate.bind(),
        format = format.bind(),
        maxQuality = maxQuality.bind(),
        embedThumbnail = embedThumbnail.bind(),
        embedMetadata = embedMetadata.bind(),
        embedSubtitles = embedSubtitles.bind(),
        normalizeAudio = normalizeAudio.bind(),
    )
}
