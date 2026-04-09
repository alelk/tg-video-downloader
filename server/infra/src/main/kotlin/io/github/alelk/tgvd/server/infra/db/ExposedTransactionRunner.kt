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
 */
class ExposedTransactionRunner(
    private val db: Database,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TransactionRunner {

    override suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T =
        withContext(dispatcher) {
            suspendTransaction(db, readOnly = true) {
                block.invoke(RoScopeImpl)
            }
        }

    override suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T =
        withContext(dispatcher) {
            suspendTransaction(db, readOnly = false) {
                block.invoke(RwScopeImpl)
            }
        }
}


