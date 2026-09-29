package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.job.JobOutputRepository
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.system.ReadinessProbe
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.track.TrackSelectionSettingsProvider
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoDownloader
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseReadinessProbe
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.repository.ChannelRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobOutputRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.RuleRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.VideoInfoCacheImpl
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
import io.github.alelk.tgvd.server.infra.process.YtDlpBootstrap
import io.github.alelk.tgvd.server.infra.process.YtDlpRunner
import io.github.alelk.tgvd.server.infra.process.YtDlpServiceImpl
import io.github.alelk.tgvd.server.infra.service.JobProcessor
import io.github.alelk.tgvd.server.infra.service.SystemSettingsHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module

internal fun infraModule(database: Database?) = module {
    // Database: opened (pool + Flyway) by the application before Koin starts; absent when the server
    // is booted without a database (API-surface test) — then only the readiness probe is usable.
    if (database != null) single { database }
    single<ReadinessProbe> { DatabaseReadinessProbe(database) }
    single<TransactionRunner> { ExposedTransactionRunner(db = get<Database>()) }

    // Mutable settings holder (initial values from config or DB, overridable via API; persisted across restarts)
    single { SystemSettingsHolder(get<YtDlpConfig>(), get<ProxyConfig>(), get<Database>()) }
    single<TrackSelectionSettingsProvider> { get<SystemSettingsHolder>() }

    // Repositories (domain port → infra adapter)
    single<WorkspaceRepository> { WorkspaceRepositoryImpl(get<Database>()) }
    single<RuleRepository> { RuleRepositoryImpl(get<Database>()) }
    single<ChannelRepository> { ChannelRepositoryImpl(get<Database>()) }
    single<JobRepository> { JobRepositoryImpl(get<Database>()) }
    single<JobOutputRepository> { JobOutputRepositoryImpl(get<Database>()) }
    single<VideoInfoCache> { VideoInfoCacheImpl(get<Database>()) }

    // External process runners
    single { YtDlpRunner(get<SystemSettingsHolder>()) }
    single<VideoInfoExtractor> { get<YtDlpRunner>() }
    single<VideoDownloader> { get<YtDlpRunner>() }
    single { FfmpegRunner(get<FfmpegConfig>()) }

    // Outbound HTTP client (e.g. GitHub releases API for yt-dlp update check)
    single {
        HttpClient(CIO) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
            }
        }
    }

    // System services
    single<YtDlpService> { YtDlpServiceImpl(get<YtDlpConfig>(), get<HttpClient>()) }
    single { YtDlpBootstrap(get<YtDlpConfig>()) }

    // Job processor
    single {
        JobProcessor(
            jobRepository = get<JobRepository>(),
            jobOutputRepository = get<JobOutputRepository>(),
            ruleRepository = get<RuleRepository>(),
            channelRepository = get<ChannelRepository>(),
            videoDownloader = get<VideoDownloader>(),
            videoInfoCache = get<VideoInfoCache>(),
            ffmpegRunner = get<FfmpegRunner>(),
            config = get<JobsConfig>(),
        )
    }
}
