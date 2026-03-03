package io.github.alelk.tgvd.server.infra.service

import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Mutable holder for system settings that can be updated at runtime via API.
 *
 * Initial values come from config (application.yaml / env vars).
 * UI overrides are applied on top.
 */
class SystemSettingsHolder(
    initialYtDlpConfig: YtDlpConfig,
    initialProxyConfig: ProxyConfig,
) {
    private val ytDlpRef = AtomicReference(initialYtDlpConfig)
    private val proxyRef = AtomicReference(initialProxyConfig)

    val ytDlpConfig: YtDlpConfig get() = ytDlpRef.get()
    val proxyConfig: ProxyConfig get() = proxyRef.get()

    fun updateYtDlpConfig(update: (YtDlpConfig) -> YtDlpConfig) {
        val old = ytDlpRef.get()
        val new = update(old)
        ytDlpRef.set(new)
        logger.info { "YtDlpConfig updated: cookiesFromBrowser=${new.cookiesFromBrowser}, cookiesFile=${new.cookiesFile}" }
    }

    fun updateProxyConfig(update: (ProxyConfig) -> ProxyConfig) {
        val old = proxyRef.get()
        val new = update(old)
        proxyRef.set(new)
        logger.info { "ProxyConfig updated: enabled=${new.enabled}, type=${new.type}, host=${new.host}:${new.port}" }
    }
}

