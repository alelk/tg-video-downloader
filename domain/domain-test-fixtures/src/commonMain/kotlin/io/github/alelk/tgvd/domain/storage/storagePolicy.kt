package io.github.alelk.tgvd.domain.storage

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.storagePolicy(
    originalTemplate: Arb<String> = Arb.element(
        "~/Downloads/{channelName}/{title} [{videoId}].{ext}",
        "~/Media/{artist}/{title}.{ext}",
        "~/Videos/{year}/{date} {title}.{ext}",
    ),
    additionalOutputs: Arb<List<OutputTemplate>> = Arb.list(Arb.outputTemplate(), 0..2),
): Arb<StoragePolicy> = arbitrary {
    StoragePolicy(
        originalTemplate = originalTemplate.bind(),
        additionalOutputs = additionalOutputs.bind(),
    )
}

fun Arb.Companion.outputTemplate(
    pathTemplate: Arb<String> = Arb.element(
        "/media/converted/{title}.mp4",
        "/media/audio/{title}.mp3",
    ),
    format: Arb<OutputFormat> = Arb.outputFormat(),
): Arb<OutputTemplate> = arbitrary {
    OutputTemplate(pathTemplate = pathTemplate.bind(), format = format.bind())
}

