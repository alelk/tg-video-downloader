package io.github.alelk.tgvd.server.infra.db

import io.github.alelk.tgvd.domain.tx.RoTransactionScope
import io.github.alelk.tgvd.domain.tx.RwTransactionScope
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

// Singleton scope objects — allocated once, safe to share (marker interfaces carry no state).
private object RoScopeImpl : RoTransactionScope
private object RwScopeImpl : RwTransactionScope

/**
 * [TransactionRunner] implementation backed by Jetbrains Exposed.
 *
 * - Read-only transactions set `readOnly = true`, which lets PostgreSQL skip write-intent locks
 *   and enables potential use of read replicas in the future.
 * - The [dispatcher] defaults to [Dispatchers.IO] so that blocking JDBC calls do not consume
 *   threads from the main coroutine pool. Can be overridden in tests.
 * - The only production code that opens a transaction (the readiness probe's `SELECT 1` aside).
 *   Repositories run in the transaction bound to the coroutine and never open one.
 * - Nested calls join the outer transaction (no savepoint): the inner block's writes commit or roll
 *   back with the outer one (`ExposedTransactionRunnerTest`).
 * - It COMMITS whatever the block returns — a `Left` included. Rollback comes only from an exception
 *   or from `catchingDb`, which rolls back before it turns a `SQLException` into a `Left`.
 */
class ExposedTransactionRunner(
    private val db: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransactionRunner {

    override suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T = withContext(dispatcher) {
        suspendTransaction(db, readOnly = true) {
            block.invoke(RoScopeImpl)
        }
    }

    override suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T = withContext(dispatcher) {
        suspendTransaction(db, readOnly = false) {
            block.invoke(RwScopeImpl)
        }
    }
}
