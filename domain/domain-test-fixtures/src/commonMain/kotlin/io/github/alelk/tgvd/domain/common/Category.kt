package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum

fun Arb.Companion.category(): Arb<Category> = Arb.enum<Category>()

