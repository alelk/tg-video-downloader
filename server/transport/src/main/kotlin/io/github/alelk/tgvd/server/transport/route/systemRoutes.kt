package io.github.alelk.tgvd.server.transport.route

import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.system.*
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.github.alelk.tgvd.server.transport.error.apiError
import io.github.alelk.tgvd.server.transport.util.correlationId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.systemRoutes() {
    val ytDlpService by inject<YtDlpService>()
    val settingsHolder by inject<SystemSettingsHolder>()

    // --- Settings ---

    get<ApiV1.System.Settings> {
        val ytDlpConfig = settingsHolder.ytDlpConfig
        val proxyConfig = settingsHolder.proxyConfig
        call.respond(
            SystemSettingsDto(
                ytDlp = YtDlpSettingsDto(
                    cookiesFromBrowser = ytDlpConfig.cookiesFromBrowser,
                    cookiesContent = null,   // never echo back — sensitive
                    cookiesFile = ytDlpConfig.cookiesFile,
                    legacyServerConnect = ytDlpConfig.legacyServerConnect,
                    noCheckCertificate = ytDlpConfig.noCheckCertificate,
                    preferredFormats = ytDlpConfig.preferredFormats,
                    formatSort = ytDlpConfig.formatSort,
                    checkFormats = ytDlpConfig.checkFormats,
                    mergeOutputFormat = ytDlpConfig.mergeOutputFormat,
                    rateLimit = ytDlpConfig.rateLimit,
                    sleepInterval = ytDlpConfig.sleepInterval,
                    maxSleepInterval = ytDlpConfig.maxSleepInterval,
                    writeSubs = ytDlpConfig.writeSubs,
                    writeAutoSubs = ytDlpConfig.writeAutoSubs,
                    subLangs = ytDlpConfig.subLangs,
                    embedSubs = ytDlpConfig.embedSubs,
                    concurrentFragments = ytDlpConfig.concurrentFragments,
                    socketTimeout = ytDlpConfig.socketTimeout,
                    youtubePlayerClient = ytDlpConfig.youtubePlayerClient,
                    extractorArgs = ytDlpConfig.extractorArgs,
                    sponsorBlockRemove = ytDlpConfig.sponsorBlockRemove,
                    userAgent = ytDlpConfig.userAgent,
                    extractorOverrides = ytDlpConfig.extractorOverrides.mapValues { (_, v) ->
                        YtDlpExtractorOverrideDto(
                            legacyServerConnect = v.legacyServerConnect,
                            noCheckCertificate = v.noCheckCertificate,
                            proxyEnabled = v.proxyEnabled,
                        )
                    },
                ),
                proxy = ProxySettingsDto(
                    enabled = proxyConfig.enabled,
                    type = proxyConfig.type.name,
                    host = proxyConfig.host,
                    port = proxyConfig.port,
                    username = proxyConfig.username,
                    password = null, // masked
                ),
            ),
        )
    }

    put<ApiV1.System.Settings> {
        val request = call.receive<SystemSettingsDto>()

        settingsHolder.updateYtDlpConfig { current ->
            current.copy(
                cookiesFromBrowser = request.ytDlp.cookiesFromBrowser,
                // cookiesContent: null in request means "keep existing"; non-null means "update"
                cookiesContent = request.ytDlp.cookiesContent ?: current.cookiesContent,
                cookiesFile = request.ytDlp.cookiesFile,
                legacyServerConnect = request.ytDlp.legacyServerConnect,
                noCheckCertificate = request.ytDlp.noCheckCertificate,
                preferredFormats = request.ytDlp.preferredFormats,
                formatSort = request.ytDlp.formatSort,
                checkFormats = request.ytDlp.checkFormats,
                mergeOutputFormat = request.ytDlp.mergeOutputFormat,
                rateLimit = request.ytDlp.rateLimit,
                sleepInterval = request.ytDlp.sleepInterval,
                maxSleepInterval = request.ytDlp.maxSleepInterval,
                writeSubs = request.ytDlp.writeSubs,
                writeAutoSubs = request.ytDlp.writeAutoSubs,
                subLangs = request.ytDlp.subLangs,
                embedSubs = request.ytDlp.embedSubs,
                concurrentFragments = request.ytDlp.concurrentFragments,
                socketTimeout = request.ytDlp.socketTimeout,
                extractorArgs = request.ytDlp.extractorArgs,
                youtubePlayerClient = request.ytDlp.youtubePlayerClient,
                sponsorBlockRemove = request.ytDlp.sponsorBlockRemove,
                userAgent = request.ytDlp.userAgent,
                extractorOverrides = request.ytDlp.extractorOverrides.mapValues { (_, v) ->
                    YtDlpExtractorOverride(
                        legacyServerConnect = v.legacyServerConnect,
                        noCheckCertificate = v.noCheckCertificate,
                        proxyEnabled = v.proxyEnabled,
                    )
                },
            )
        }

        settingsHolder.updateProxyConfig { current ->
            current.copy(
                enabled = request.proxy.enabled,
                type = ProxyConfig.ProxyType.entries
                    .find { it.name.equals(request.proxy.type, ignoreCase = true) }
                    ?: current.type,
                host = request.proxy.host,
                port = request.proxy.port,
                username = request.proxy.username,
                password = request.proxy.password ?: current.password,
            )
        }

        call.respond(HttpStatusCode.OK, request.copy(
            proxy = request.proxy.copy(password = null),
        ))
    }

    // --- yt-dlp ---

    get<ApiV1.System.YtDlp.Status> {
        call.respondEither(ytDlpService.version()) { version ->
            YtDlpStatusDto(
                currentVersion = version.version,
                latestVersion = null,
                isUpdateAvailable = false,
            )
        }
    }

    post<ApiV1.System.YtDlp.Update> {
        val ytDlpConfig = settingsHolder.ytDlpConfig
        if (!ytDlpConfig.allowUpdate) {
            call.respond(HttpStatusCode.Forbidden, apiError("UPDATE_DISABLED", "Update is disabled by administrator", call.correlationId))
            return@post
        }

        call.respondEither(ytDlpService.update(), HttpStatusCode.Accepted) { version ->
            YtDlpUpdateResponseDto(
                status = "UPDATED",
                message = "Updated to version ${version.version}",
            )
        }
    }
}

