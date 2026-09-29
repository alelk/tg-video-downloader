package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.channel.CreateChannelUseCase
import io.github.alelk.tgvd.domain.channel.DeleteChannelUseCase
import io.github.alelk.tgvd.domain.channel.GetChannelUseCase
import io.github.alelk.tgvd.domain.channel.ListChannelTagsUseCase
import io.github.alelk.tgvd.domain.channel.ListChannelsUseCase
import io.github.alelk.tgvd.domain.channel.UpdateChannelUseCase
import io.github.alelk.tgvd.domain.job.CancelJobUseCase
import io.github.alelk.tgvd.domain.job.CreateJobUseCase
import io.github.alelk.tgvd.domain.job.GetJobUseCase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.ListJobsUseCase
import io.github.alelk.tgvd.domain.job.RetryJobUseCase
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.preview.PreviewVideoUseCase
import io.github.alelk.tgvd.domain.rule.CreateRuleUseCase
import io.github.alelk.tgvd.domain.rule.DeleteRuleUseCase
import io.github.alelk.tgvd.domain.rule.GetRuleUseCase
import io.github.alelk.tgvd.domain.rule.ListRulesUseCase
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.rule.UpdateRuleUseCase
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.system.GetSystemSettingsUseCase
import io.github.alelk.tgvd.domain.system.GetYtDlpStatusUseCase
import io.github.alelk.tgvd.domain.system.SystemSettingsStore
import io.github.alelk.tgvd.domain.system.UpdateSystemSettingsUseCase
import io.github.alelk.tgvd.domain.system.UpdateYtDlpUseCase
import io.github.alelk.tgvd.domain.system.YtDlpService
import io.github.alelk.tgvd.domain.track.TrackSelectionSettingsProvider
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.workspace.AddWorkspaceMemberUseCase
import io.github.alelk.tgvd.domain.workspace.CreateWorkspaceUseCase
import io.github.alelk.tgvd.domain.workspace.ListWorkspaceMembersUseCase
import io.github.alelk.tgvd.domain.workspace.ListWorkspacesUseCase
import io.github.alelk.tgvd.domain.workspace.RemoveWorkspaceMemberUseCase
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.Clock

internal fun domainModule() = module {
    // The one place that reads the system clock; everything else gets `Clock` injected (G9).
    single<Clock> { Clock.System }

    single { MetadataResolver() }
    single { PathTemplateEngine() }
    single { RuleMatchingService(get<RuleRepository>(), get<ChannelRepository>()) }

    single { WorkspaceAccess(get<WorkspaceRepository>()) }

    jobUseCases()
    ruleUseCases()
    channelUseCases()
    workspaceUseCases()
    systemUseCases()
    previewUseCases()
}

private fun Module.jobUseCases() {
    single {
        CreateJobUseCase(
            workspaceAccess = get<WorkspaceAccess>(),
            jobRepository = get<JobRepository>(),
            ruleRepository = get<RuleRepository>(),
            txRunner = get<TransactionRunner>(),
            clock = get<Clock>(),
        )
    }
    single { ListJobsUseCase(get<WorkspaceAccess>(), get<JobRepository>(), get<TransactionRunner>()) }
    single { GetJobUseCase(get<WorkspaceAccess>(), get<JobRepository>(), get<TransactionRunner>()) }
    single { CancelJobUseCase(get<WorkspaceAccess>(), get<JobRepository>(), get<TransactionRunner>()) }
    single { RetryJobUseCase(get<WorkspaceAccess>(), get<JobRepository>(), get<TransactionRunner>()) }
}

private fun Module.ruleUseCases() {
    single { ListRulesUseCase(get<WorkspaceAccess>(), get<RuleRepository>(), get<TransactionRunner>()) }
    single { GetRuleUseCase(get<WorkspaceAccess>(), get<RuleRepository>(), get<TransactionRunner>()) }
    single { CreateRuleUseCase(get<WorkspaceAccess>(), get<RuleRepository>(), get<TransactionRunner>(), get<Clock>()) }
    single { UpdateRuleUseCase(get<WorkspaceAccess>(), get<RuleRepository>(), get<TransactionRunner>(), get<Clock>()) }
    single { DeleteRuleUseCase(get<WorkspaceAccess>(), get<RuleRepository>(), get<TransactionRunner>()) }
}

private fun Module.channelUseCases() {
    single { ListChannelsUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>()) }
    single { ListChannelTagsUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>()) }
    single { GetChannelUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>()) }
    single {
        CreateChannelUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>(), get<Clock>())
    }
    single {
        UpdateChannelUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>(), get<Clock>())
    }
    single { DeleteChannelUseCase(get<WorkspaceAccess>(), get<ChannelRepository>(), get<TransactionRunner>()) }
}

private fun Module.workspaceUseCases() {
    single { ListWorkspacesUseCase(get<WorkspaceRepository>(), get<TransactionRunner>()) }
    single { CreateWorkspaceUseCase(get<WorkspaceRepository>(), get<TransactionRunner>(), get<Clock>()) }
    single {
        ListWorkspaceMembersUseCase(get<WorkspaceAccess>(), get<WorkspaceRepository>(), get<TransactionRunner>())
    }
    single { AddWorkspaceMemberUseCase(get<WorkspaceRepository>(), get<TransactionRunner>(), get<Clock>()) }
    single { RemoveWorkspaceMemberUseCase(get<WorkspaceRepository>(), get<TransactionRunner>()) }
}

private fun Module.systemUseCases() {
    single { GetSystemSettingsUseCase(get<SystemSettingsStore>()) }
    single { UpdateSystemSettingsUseCase(get<SystemSettingsStore>(), get<TransactionRunner>()) }
    single { GetYtDlpStatusUseCase(get<YtDlpService>(), get<Clock>()) }
    single { UpdateYtDlpUseCase(get<YtDlpService>(), get<SystemSettingsStore>()) }
}

private fun Module.previewUseCases() {
    single {
        PreviewUseCase(
            videoInfoExtractor = get<VideoInfoExtractor>(),
            videoInfoCache = get<VideoInfoCache>(),
            ruleMatchingService = get<RuleMatchingService>(),
            metadataResolver = get<MetadataResolver>(),
            llmPort = get<LlmPort>(),
            txRunner = get<TransactionRunner>(),
        )
    }
    single {
        PreviewVideoUseCase(
            workspaceAccess = get<WorkspaceAccess>(),
            previewUseCase = get<PreviewUseCase>(),
            pathTemplateEngine = get<PathTemplateEngine>(),
            channelRepository = get<ChannelRepository>(),
            jobRepository = get<JobRepository>(),
            trackSelectionSettings = get<TrackSelectionSettingsProvider>(),
            txRunner = get<TransactionRunner>(),
        )
    }
}
