package io.github.alelk.tgvd.domain.tx

/**
 * Abstraction over transactional execution.
 *
 * Use cases depend on this interface to coordinate multiple repository calls
 * within a single atomic unit. The actual implementation (Exposed, in-memory, no-op)
 * lives in the infrastructure layer and is injected via DI.
 *
 * Two scopes are intentionally separate so that static analysis or future tooling
 * can distinguish read-only from read-write access patterns.
 */
interface TransactionRunner {
    /** Execute [block] in a read-only transaction; return its result or propagate exception. */
    suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T

    /** Execute [block] in a read-write transaction; return its result or propagate exception. */
    suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T
}

/** Marker scope injected into a read-only transaction block. */
interface RoTransactionScope

/** Marker scope injected into a read-write transaction block. Extends [RoTransactionScope]. */
interface RwTransactionScope : RoTransactionScope
