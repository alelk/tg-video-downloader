package io.github.alelk.tgvd.server.infra.db

import kotlinx.serialization.json.Json

/** Shared [Json] instance for JSONB columns in Exposed tables. */
internal val jsonb = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

