package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.channel.CreateChannelUseCase
import io.github.alelk.tgvd.domain.channel.DeleteChannelUseCase
import io.github.alelk.tgvd.domain.channel.UpdateChannelUseCase
import io.github.alelk.tgvd.domain.job.CancelJobUseCase
import io.github.alelk.tgvd.domain.job.CreateJobUseCase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.job.RetryJobUseCase
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.rule.CreateRuleUseCase
import io.github.alelk.tgvd.domain.rule.DeleteRuleUseCase
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.rule.UpdateRuleUseCase
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.tx.TransactionRunner
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.workspace.AddWorkspaceMemberUseCase
import io.github.alelk.tgvd.domain.workspace.CreateWorkspaceUseCase
import io.github.alelk.tgvd.domain.workspace.RemoveWorkspaceMemberUseCase
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.infra.config.LlmConfig
import org.koin.dsl.module

internal fun domainModule() = module {
    single { MetadataResolver() }
    single { PathTemplateEngine() }
    single { RuleMatchingService(get<RuleRepository>(), get<ChannelRepository>()) }

    // Job use cases
    single { CreateJobUseCase(get<JobRepository>(), get<TransactionRunner>()) }
    single { CancelJobUseCase(get<JobRepository>(), get<TransactionRunner>()) }
    single { RetryJobUseCase(get<JobRepository>(), get<TransactionRunner>()) }

    // Rule use cases
    single { CreateRuleUseCase(get<RuleRepository>(), get<TransactionRunner>()) }
    single { UpdateRuleUseCase(get<RuleRepository>(), get<TransactionRunner>()) }
    single { DeleteRuleUseCase(get<RuleRepository>(), get<TransactionRunner>()) }

    // Channel use cases
    single { CreateChannelUseCase(get<ChannelRepository>(), get<TransactionRunner>()) }
    single { UpdateChannelUseCase(get<ChannelRepository>(), get<TransactionRunner>()) }
    single { DeleteChannelUseCase(get<ChannelRepository>(), get<TransactionRunner>()) }

    // Workspace use cases
    single { CreateWorkspaceUseCase(get<WorkspaceRepository>(), get<TransactionRunner>()) }
    single { AddWorkspaceMemberUseCase(get<WorkspaceRepository>(), get<TransactionRunner>()) }
    single { RemoveWorkspaceMemberUseCase(get<WorkspaceRepository>(), get<TransactionRunner>()) }

    // Preview use case
    single {
        PreviewUseCase(
            videoInfoExtractor = get<VideoInfoExtractor>(),
            videoInfoCache = get<VideoInfoCache>(),
            ruleMatchingService = get<RuleMatchingService>(),
            metadataResolver = get<MetadataResolver>(),
            llmPort = resolveLlmPort(get<LlmConfig>()),
            txRunner = get<TransactionRunner>(),
        )
    }
}

/** Resolve [LlmPort] adapter based on configuration. Returns `null` when LLM is not configured. */
private fun resolveLlmPort(config: LlmConfig): LlmPort? {
    if (config.provider == LlmConfig.LlmProvider.NONE || config.apiKey.isNullOrBlank()) return null
    // TODO: implement GeminiLlmAdapter / OpenAiLlmAdapter based on config.provider
    return null
}
