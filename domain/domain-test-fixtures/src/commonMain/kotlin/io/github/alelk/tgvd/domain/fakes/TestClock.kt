package io.github.alelk.tgvd.domain.fakes

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] that stands still until the test moves it. */
class TestClock(private var instant: Instant = Instant.parse("2026-01-01T09:00:00Z")) : Clock {
    override fun now(): Instant = instant

    fun advance(by: Duration) {
        instant += by
    }
}
