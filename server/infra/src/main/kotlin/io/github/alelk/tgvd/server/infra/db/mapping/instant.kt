package io.github.alelk.tgvd.server.infra.db.mapping

import kotlin.time.Instant

internal fun now(): Instant =
    Instant.fromEpochMilliseconds(System.currentTimeMillis())
