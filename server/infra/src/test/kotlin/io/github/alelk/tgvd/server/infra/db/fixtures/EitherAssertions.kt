package io.github.alelk.tgvd.server.infra.db.fixtures

import arrow.core.Either
import io.kotest.assertions.fail

/** Asserts [Either.Right] and returns its value (kotest-assertions-arrow is not on the classpath). */
fun <A, B> Either<A, B>.shouldBeRight(): B = when (this) {
    is Either.Right -> value
    is Either.Left -> fail("Expected Either.Right but was Left($value)")
}

/** Asserts [Either.Left] and returns its value. */
fun <A, B> Either<A, B>.shouldBeLeft(): A = when (this) {
    is Either.Left -> value
    is Either.Right -> fail("Expected Either.Left but was Right($value)")
}
