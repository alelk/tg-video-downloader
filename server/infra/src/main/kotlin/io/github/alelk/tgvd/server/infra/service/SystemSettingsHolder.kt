package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.mapping.now
import io.github.alelk.tgvd.server.infra.db.table.SystemSettingsTable
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

private const val KEY_YTDLP = "ytdlp"
private const val KEY_PROXY = "proxy"

/**
 * Mutable holder for system settings that can be updated at runtime via API.
 *
 * Initial values: loaded from DB if present, otherwise fall back to config (application.yaml / env vars).
 * Every update is immediately persisted to the DB so settings survive server restarts.
 */
class SystemSettingsHolder(
    initialYtDlpConfig: YtDlpConfig,
    initialProxyConfig: ProxyConfig,
    private val database: Database,
) {
    private val ytDlpRef: AtomicReference<YtDlpConfig>
    private val proxyRef: AtomicReference<ProxyConfig>

    init {
        val (persistedYtDlp, persistedProxy) = runBlocking { loadFromDb() }
        if (persistedYtDlp != null) logger.info { "Loaded persisted yt-dlp settings from DB" }
        if (persistedProxy != null) logger.info { "Loaded persisted proxy settings from DB" }
        ytDlpRef = AtomicReference(persistedYtDlp ?: initialYtDlpConfig)
        proxyRef = AtomicReference(persistedProxy ?: initialProxyConfig)
    }

    val ytDlpConfig: YtDlpConfig get() = ytDlpRef.get()
    val proxyConfig: ProxyConfig get() = proxyRef.get()

    suspend fun updateYtDlpConfig(update: (YtDlpConfig) -> YtDlpConfig) {
        val new = update(ytDlpRef.get())
        ytDlpRef.set(new)
        persist(KEY_YTDLP, jsonb.encodeToString(new))
        logger.info { "YtDlpConfig updated: cookiesFromBrowser=${new.cookiesFromBrowser}, cookiesFile=${new.cookiesFile}, legacyServerConnect=${new.legacyServerConnect}, noCheckCertificate=${new.noCheckCertificate}" }
    }

    suspend fun updateProxyConfig(update: (ProxyConfig) -> ProxyConfig) {
        val new = update(proxyRef.get())
        proxyRef.set(new)
        persist(KEY_PROXY, jsonb.encodeToString(new))
        logger.info { "ProxyConfig updated: enabled=${new.enabled}, type=${new.type}, host=${new.host}:${new.port}" }
    }

    private suspend fun loadFromDb(): Pair<YtDlpConfig?, ProxyConfig?> = dbQuery(database) {
        fun loadKey(key: String) = SystemSettingsTable.selectAll()
            .where { SystemSettingsTable.key eq key }
            .singleOrNull()
            ?.get(SystemSettingsTable.value)

        val ytDlp = loadKey(KEY_YTDLP)?.let {
            runCatching { jsonb.decodeFromString<YtDlpConfig>(it) }
                .onFailure { e -> logger.warn(e) { "Failed to deserialize persisted YtDlpConfig, using default" } }
                .getOrNull()
        }
        val proxy = loadKey(KEY_PROXY)?.let {
            runCatching { jsonb.decodeFromString<ProxyConfig>(it) }
                .onFailure { e -> logger.warn(e) { "Failed to deserialize persisted ProxyConfig, using default" } }
                .getOrNull()
        }
        ytDlp to proxy
    }

    private suspend fun persist(key: String, value: String) = dbQuery(database) {
        SystemSettingsTable.upsert {
            it[SystemSettingsTable.key] = key
            it[SystemSettingsTable.value] = value
            it[SystemSettingsTable.updatedAt] = now()
        }
    }
}
