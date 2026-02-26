package io.github.alelk.tgvd.api.contract.common

import kotlinx.serialization.json.Json

val apiJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}

