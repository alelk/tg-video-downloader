package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

/**
 * Tag for grouping channels in the channel directory.
 *
 * Lowercase alphanumeric with hyphens. Examples: `music-video`, `lofi`, `tech-review`, `series`.
 */
@JvmInline
value class Tag(val value: String) {
    init {
        require(value.isNotBlank()) { "Tag cannot be blank" }
        require(value.length <= 50) { "Tag too long (max 50)" }
        require(value.matches(TAG_REGEX)) { "Tag must be lowercase alphanumeric with hyphens: $value" }
    }

    companion object {
        private val TAG_REGEX = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    }
}

