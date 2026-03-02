package io.github.alelk.tgvd.domain.metadata

import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum

fun Arb.Companion.metadataSource(): Arb<MetadataSource> = Arb.enum<MetadataSource>()

