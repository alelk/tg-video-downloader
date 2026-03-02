package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int

fun Arb.Companion.domainLocalDate(
    year: Arb<Int> = Arb.int(1970..2099),
    month: Arb<Int> = Arb.int(1..12),
    day: Arb<Int> = Arb.int(1..28),
): Arb<LocalDate> = arbitrary {
    val y = year.bind()
    val m = month.bind().toString().padStart(2, '0')
    val d = day.bind().toString().padStart(2, '0')
    LocalDate("$y-$m-$d")
}
