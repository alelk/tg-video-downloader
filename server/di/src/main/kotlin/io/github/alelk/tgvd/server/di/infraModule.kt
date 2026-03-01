package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.VideoDownloader
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.infra.config.DbConfig
import io.github.alelk.tgvd.server.infra.config.FfmpegConfig
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.db.DatabaseFactory
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.RuleRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.process.FfmpegRunner
import io.github.alelk.tgvd.server.infra.process.YtDlpRunner
import io.github.alelk.tgvd.server.infra.process.YtDlpServiceImpl
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.dsl.module

internal fun infraModule() = module {
    // Database
    single { DatabaseFactory(get<DbConfig>()) }
    single { get<DatabaseFactory>().create() }

    // Repositories (domain port → infra adapter)
    single<WorkspaceRepository> { WorkspaceRepositoryImpl(get<Database>()) }
    single<RuleRepository> { RuleRepositoryImpl(get<Database>()) }
    single<JobRepository> { JobRepositoryImpl(get<Database>()) }

    // External process runners
    single { YtDlpRunner(get<YtDlpConfig>(), get<ProxyConfig>()) }
    single<VideoInfoExtractor> { get<YtDlpRunner>() }
    single<VideoDownloader> { get<YtDlpRunner>() }
    single { FfmpegRunner(get<FfmpegConfig>()) }

    // System services
    single<YtDlpService> { YtDlpServiceImpl(get<YtDlpConfig>()) }
}

