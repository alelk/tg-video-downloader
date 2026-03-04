package io.github.alelk.tgvd.server.di

import io.github.alelk.tgvd.domain.job.CreateJobUseCase
import io.github.alelk.tgvd.domain.job.JobRepository
import io.github.alelk.tgvd.domain.metadata.LlmPort
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.preview.PreviewUseCase
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.rule.RuleRepository
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.server.infra.config.LlmConfig
import org.koin.dsl.module

internal fun domainModule() = module {
    single { MetadataResolver() }
    single { PathTemplateEngine() }
    single { RuleMatchingService(get<RuleRepository>()) }
    single { CreateJobUseCase(get<JobRepository>()) }

    single {
        PreviewUseCase(
            videoInfoExtractor = get<VideoInfoExtractor>(),
            videoInfoCache = get<VideoInfoCache>(),
            ruleMatchingService = get<RuleMatchingService>(),
            metadataResolver = get<MetadataResolver>(),
            llmPort = resolveLlmPort(get<LlmConfig>()),
        )
    }
}

/** Resolve [LlmPort] adapter based on configuration. Returns `null` when LLM is not configured. */
private fun resolveLlmPort(config: LlmConfig): LlmPort? {
    if (config.provider == LlmConfig.LlmProvider.NONE || config.apiKey.isNullOrBlank()) return null
    // TODO: implement GeminiLlmAdapter / OpenAiLlmAdapter based on config.provider
    return null
}
