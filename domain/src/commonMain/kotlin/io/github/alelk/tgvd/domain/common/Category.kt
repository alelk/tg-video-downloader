package io.github.alelk.tgvd.domain.common

enum class Category {
    MUSIC_VIDEO,
    SERIES,
    OTHER;

    companion object {
        fun fromString(value: String): Category? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
