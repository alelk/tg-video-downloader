package io.github.alelk.tgvd.server.infra.db

import io.github.alelk.tgvd.domain.system.ReadinessProbe
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Ready when `SELECT 1` succeeds within [timeout]. A server started without a database
 * ([database] = `null`) is honestly "not ready".
 *
 * The timeout matters: with the database gone, Hikari waits up to its connection timeout (30 s) for a
 * connection — far longer than any orchestrator waits for a probe. The blocking JDBC call is
 * interrupted when the timeout fires.
 */
class DatabaseReadinessProbe(private val database: Database?, private val timeout: Duration = DEFAULT_TIMEOUT) :
    ReadinessProbe {
    @Suppress("TooGenericExceptionCaught") // nothing may escape a probe: any failure is "not ready"
    override suspend fun isReady(): Boolean {
        val db = database ?: return false
        return try {
            withTimeoutOrNull(timeout) { runInterruptible(Dispatchers.IO) { selectOne(db) } } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn { "Readiness probe failed: ${e.message}" }
            false
        }
    }

    private fun selectOne(db: Database): Boolean = transaction(db) {
        exec("SELECT 1") { it.next() } ?: false
    }

    companion object {
        val DEFAULT_TIMEOUT: Duration = 3.seconds
    }
}
