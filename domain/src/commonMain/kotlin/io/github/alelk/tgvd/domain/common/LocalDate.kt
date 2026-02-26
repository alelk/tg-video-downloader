package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class LocalDate(val value: String) {
    init {
        require(ISO_DATE_REGEX.matches(value)) { "LocalDate must be in ISO 8601 format (YYYY-MM-DD): $value" }
    }

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()
    val day: Int get() = value.substring(8, 10).toInt()

    companion object {
        private val ISO_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()
    }
}