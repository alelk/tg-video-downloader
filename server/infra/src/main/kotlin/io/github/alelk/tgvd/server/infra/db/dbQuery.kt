package io.github.alelk.tgvd.server.infra.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** Execute a suspending [block] inside an Exposed transaction bound to [database]. */
internal suspend fun <T> dbQuery(database: Database, block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction(db = database) { block() }
    }
