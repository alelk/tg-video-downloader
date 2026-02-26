package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class Url(val value: String) {
    init {
        require(value.isNotBlank()) { "URL cannot be blank" }
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "URL must start with http:// or https://"
        }
    }
}