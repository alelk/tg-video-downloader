package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.domain.system.ProxySettings
import io.github.alelk.tgvd.domain.system.ProxyType
import io.github.alelk.tgvd.domain.system.SystemSettings
import io.github.alelk.tgvd.domain.system.YtDlpSettings
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import io.github.alelk.tgvd.domain.system.YtDlpExtractorOverride as ExtractorOverride

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
            val holder = SystemSettingsHolder(configYtDlp, configProxy, tx, Clock.System)
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

            val restarted = SystemSettingsHolder(configYtDlp, configProxy, tx, Clock.System)
            restarted.ytDlpConfig shouldBe ytDlp
            restarted.proxyConfig shouldBe proxy

            tx.inRwTransaction { restarted.updateProxyConfig { it.copy(username = "u", password = "p") } }
            SystemSettingsHolder(configYtDlp, configProxy, tx, Clock.System).proxyConfig shouldBe
                proxy.copy(username = "u", password = "p")
        }

        test("as SystemSettingsStore: save keeps the deployment-only values and survives a restart") {
            val deployment = configYtDlp.copy(allowUpdate = false, retries = 9, timeout = 5.minutes)
            val fresh = PostgresTestContainer.newMigratedDatabase().database
            val freshTx = ExposedTransactionRunner(fresh)
            val holder = SystemSettingsHolder(deployment, configProxy, freshTx, Clock.System)
            holder.current() shouldBe SystemSettings(YtDlpSettings(), ProxySettings())
            holder.isYtDlpUpdateAllowed() shouldBe false

            val settings =
                SystemSettings(
                    ytDlp =
                    YtDlpSettings(
                        cookiesContent = "# cookies",
                        preferredAudioLanguages = listOf("en"),
                        subLangs = "ru",
                        extractorOverrides = mapOf("vk" to ExtractorOverride(noCheckCertificate = true)),
                    ),
                    proxy = ProxySettings(true, ProxyType.SOCKS5, "10.0.0.2", 1081, "u", "p"),
                )
            freshTx.inRwTransaction { holder.save(settings) }

            holder.current() shouldBe settings
            holder.ytDlpConfig.path shouldBe deployment.path
            holder.ytDlpConfig.retries shouldBe 9
            holder.ytDlpConfig.timeout shouldBe 5.minutes
            holder.isYtDlpUpdateAllowed() shouldBe false
            holder.proxyConfig shouldBe
                ProxyConfig(true, ProxyConfig.ProxyType.SOCKS5, "10.0.0.2", 1081, "u", "p")

            val restarted = SystemSettingsHolder(configYtDlp, configProxy, freshTx, Clock.System)
            restarted.current() shouldBe settings
        }
    })
