package io.github.alelk.tgvd.domain.common

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.long

fun Arb.Companion.telegramUserId(
    value: Arb<Long> = Arb.long(1..999_999_999L),
): Arb<TelegramUserId> = arbitrary { TelegramUserId(value.bind()) }

