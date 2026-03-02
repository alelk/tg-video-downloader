package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

fun Arb.Companion.outputTarget(
    path: Arb<FilePath> = Arb.filePath(),
    format: Arb<OutputFormat> = Arb.outputFormat(),
): Arb<OutputTarget> = arbitrary {
    OutputTarget(path = path.bind(), format = format.bind())
}

fun Arb.Companion.storagePlan(
    original: Arb<OutputTarget> = Arb.outputTarget(),
    additional: Arb<List<OutputTarget>> = Arb.list(Arb.outputTarget(), 0..2),
): Arb<StoragePlan> = arbitrary {
    StoragePlan(original = original.bind(), additional = additional.bind())
}

