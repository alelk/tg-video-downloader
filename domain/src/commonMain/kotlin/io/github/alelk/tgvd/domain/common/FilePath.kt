package io.github.alelk.tgvd.domain.common

import kotlin.jvm.JvmInline

@JvmInline
value class FilePath(val value: String) {
    init {
        require(value.isNotBlank()) { "FilePath cannot be blank" }
    }

    val fileName: String get() = value.substringAfterLast('/')
    val parent: String get() = value.substringBeforeLast('/', "")
    val extension: String get() = fileName.substringAfterLast('.', "")
}
