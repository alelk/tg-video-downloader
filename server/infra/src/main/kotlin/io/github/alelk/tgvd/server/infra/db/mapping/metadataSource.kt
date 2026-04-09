package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.metadata.MetadataSource

internal fun MetadataSource.toDbString(): String = name.lowercase()

internal fun String.toMetadataSource(): MetadataSource = when (this.lowercase()) {
    "rule" -> MetadataSource.RULE
    "llm" -> MetadataSource.LLM
    "fallback" -> MetadataSource.FALLBACK
    "manual" -> MetadataSource.FALLBACK  // legacy alias
    else -> MetadataSource.RULE
}


