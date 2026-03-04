package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.VideoDownloader
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.JobsConfig
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseFactory
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
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module

internal fun infraModule() = module {
    // Database
    single { DatabaseFactory(get<DbConfig>()) }
    single { get<DatabaseFactory>().create() }

    // Mutable settings holder (initial values from config, overridable via API)
    single { SystemSettingsHolder(get<YtDlpConfig>(), get<ProxyConfig>()) }

    // Repositories (domain port → infra adapter)
    single<WorkspaceRepository> { WorkspaceRepositoryImpl(get<Database>()) }
    single<RuleRepository> { RuleRepositoryImpl(get<Database>()) }
    single<JobRepository> { JobRepositoryImpl(get<Database>()) }
    single<VideoInfoCache> { VideoInfoCacheImpl(get<Database>()) }

    // External process runners
    single { YtDlpRunner(get<SystemSettingsHolder>()) }
    single<VideoInfoExtractor> { get<YtDlpRunner>() }
    single<VideoDownloader> { get<YtDlpRunner>() }
    single { FfmpegRunner(get<FfmpegConfig>()) }

    // System services
    single<YtDlpService> { YtDlpServiceImpl(get<YtDlpConfig>()) }
    single { YtDlpBootstrap(get<YtDlpConfig>()) }

    // Job processor
    single {
        JobProcessor(
            jobRepository = get<JobRepository>(),
            ruleRepository = get<RuleRepository>(),
            videoDownloader = get<VideoDownloader>(),
            ffmpegRunner = get<FfmpegRunner>(),
            config = get<JobsConfig>(),
        )
    }
}

