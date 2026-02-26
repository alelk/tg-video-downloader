package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class VideoId(val value: String) {
    init {
        require(value.isNotBlank()) { "VideoId cannot be blank" }
        require(value.length <= 64) { "VideoId too long" }
    }
}