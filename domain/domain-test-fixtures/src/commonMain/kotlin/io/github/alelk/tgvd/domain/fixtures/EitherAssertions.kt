package io.github.alelk.tgvd.domain.fixtures

import arrow.core.Either

/** Asserts [Either.Right] and returns its value (kotest-assertions-arrow is not on the classpath). */
fun <A, B> Either<A, B>.shouldBeRight(): B = when (this) {
    is Either.Right -> value
    is Either.Left -> throw AssertionError("Expected Either.Right but was Left($value)")
}

/** Asserts [Either.Left] and returns its value. */
fun <A, B> Either<A, B>.shouldBeLeft(): A = when (this) {
    is Either.Left -> value
    is Either.Right -> throw AssertionError("Expected Either.Left but was Right($value)")
}
