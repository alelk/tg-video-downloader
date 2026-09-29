package io.github.alelk.tgvd.domain.tx

/**
 * [TransactionRunner] for use-case tests on fakes: runs the block inline, without any transaction.
 * Test-only — production code always gets the real runner from DI.
 */
class NoopTransactionRunner : TransactionRunner {
    override suspend fun <T> inRoTransaction(block: suspend RoTransactionScope.() -> T): T = block(RoScope)

    override suspend fun <T> inRwTransaction(block: suspend RwTransactionScope.() -> T): T = block(RwScope)

    private object RoScope : RoTransactionScope

    private object RwScope : RwTransactionScope
}
