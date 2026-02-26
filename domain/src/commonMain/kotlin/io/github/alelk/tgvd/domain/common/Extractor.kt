package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class Extractor(val value: String) {
    init {
        require(value.isNotBlank()) { "Extractor cannot be blank" }
    }

    companion object {
        val YOUTUBE = Extractor("youtube")
        val RUTUBE = Extractor("rutube")
        val VK = Extractor("vk")
        val GENERIC = Extractor("generic")
    }
}