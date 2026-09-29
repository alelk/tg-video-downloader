package io.github.alelk.tgvd.server.infra.db

import arrow.core.Either
import arrow.core.left
import io.github.alelk.tgvd.domain.common.DomainError
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import java.sql.SQLException
import kotlin.coroutines.cancellation.CancellationException

/** PostgreSQL SQLSTATE of a unique-constraint (or primary-key) violation. */
internal const val PG_UNIQUE_VIOLATION = "23505"

/**
 * Runs a repository [block] on the transaction already open on the coroutine (repositories never open
 * one — `ExposedTransactionRunner` does) and maps a database failure to a [DomainError].
 *
 * - Only [SQLException] (Exposed's `ExposedSQLException` is one) is caught; [CancellationException]
 *   and every other exception propagate untouched.
 * - A failed statement leaves PostgreSQL in "current transaction is aborted". Because the failure
 *   becomes a `Left` instead of an exception, the runner would otherwise try to COMMIT that poisoned
 *   transaction. So the transaction is rolled back here first: **every write made earlier in the
 *   same transaction is discarded**, not only the failed statement.
 * - [onUniqueViolation] turns a unique violation (SQLSTATE `23505`) into an existing conflict error
 *   of the domain (e.g. [DomainError.WorkspaceSlugConflict]); it runs after the rollback, so it may
 *   read. `null` from it — and any other failure — is [DomainError.DatabaseFailed].
 */
internal inline fun <T> catchingDb(
    onUniqueViolation: (SQLException) -> DomainError? = { null },
    block: () -> Either<DomainError, T>,
): Either<DomainError, T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: SQLException) {
    TransactionManager.current().rollback()
    val conflict = if (e.sqlState == PG_UNIQUE_VIOLATION) onUniqueViolation(e) else null
    (conflict ?: DomainError.DatabaseFailed(e.describe())).left()
}

/** True when [this] is a unique violation of the index or constraint named [constraint]. */
internal fun SQLException.violatesUnique(constraint: String): Boolean =
    sqlState == PG_UNIQUE_VIOLATION && message?.contains(constraint) == true

/** SQLSTATE and message for the server log; never shown to the client. */
internal fun SQLException.describe(): String = "SQLSTATE $sqlState: ${message ?: this::class.simpleName}"
