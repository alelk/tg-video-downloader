package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes

/**
 * [SystemSettingsHolder] round-trip on PostgreSQL: config defaults on an empty table → update →
 * a new holder (a restart) reads the persisted values, not the config.
 */
class SystemSettingsHolderTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val configYtDlp = YtDlpConfig(path = "/opt/yt-dlp")
        val configProxy = ProxyConfig()

        test("config values -> update -> restart reads the persisted values -> update again") {
            val holder = tx.inRwTransaction { SystemSettingsHolder(configYtDlp, configProxy, db) }
            holder.ytDlpConfig shouldBe configYtDlp
            holder.proxyConfig shouldBe configProxy

            val ytDlp =
                configYtDlp.copy(
                    timeout = 45.minutes,
                    cookiesFromBrowser = "firefox",
                    legacyServerConnect = true,
                    preferredAudioLanguages = listOf("en", "de"),
                    extractorOverrides = mapOf(
                        "rutube" to YtDlpExtractorOverride(legacyServerConnect = true, proxyEnabled = false),
                    ),
                )
            val proxy = ProxyConfig(enabled = true, type = ProxyConfig.ProxyType.SOCKS5, host = "10.0.0.1", port = 1080)
            tx.inRwTransaction {
                holder.updateYtDlpConfig { ytDlp }
                holder.updateProxyConfig { proxy }
            }
            holder.ytDlpConfig shouldBe ytDlp
            holder.proxyConfig shouldBe proxy

            val restarted = tx.inRwTransaction { SystemSettingsHolder(configYtDlp, configProxy, db) }
            restarted.ytDlpConfig shouldBe ytDlp
            restarted.proxyConfig shouldBe proxy

            tx.inRwTransaction { restarted.updateProxyConfig { it.copy(username = "u", password = "p") } }
            tx.inRwTransaction { SystemSettingsHolder(configYtDlp, configProxy, db) }.proxyConfig shouldBe
                proxy.copy(username = "u", password = "p")
        }
    })
