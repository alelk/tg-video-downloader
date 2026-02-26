package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class TelegramUserId(val value: Long) {
    init {
        require(value > 0) { "TelegramUserId must be positive" }
    }
}