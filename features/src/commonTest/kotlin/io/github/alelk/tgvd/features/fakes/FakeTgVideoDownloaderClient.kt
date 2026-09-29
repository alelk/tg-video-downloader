package io.github.alelk.tgvd.features.fakes

import arrow.core.Either
import arrow.core.right
import io.github.alelk.tgvd.api.client.ApiError
import io.github.alelk.tgvd.api.client.TgVideoDownloaderClient
import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.channel.ChannelListResponseDto
import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.channel.TagListResponseDto
import io.github.alelk.tgvd.api.contract.channel.UpdateChannelDto
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobListResponseDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceListResponseDto
import io.github.alelk.tgvd.features.fixtures.aJobDto
import kotlinx.coroutines.CompletableDeferred

/**
 * A [TgVideoDownloaderClient] for screen-model tests: every call the preview and settings screens make is
 * answered from a settable result and recorded; calls no screen model under test makes fail loudly.
 * One fresh instance per test. Set [gate] to hold the recorded calls in flight until the test completes it.
 */
class FakeTgVideoDownloaderClient : TgVideoDownloaderClient {
    var gate: CompletableDeferred<Unit>? = null

    var previewResult: (PreviewRequestDto) -> Either<ApiError, PreviewResponseDto> = { unexpected("preview") }
    var channelsResult: Either<ApiError, ChannelListResponseDto> = ChannelListResponseDto(items = emptyList()).right()
    var createJobResult: (CreateJobRequestDto) -> Either<ApiError, JobDto> = { aJobDto(it).right() }
    var ytDlpStatusResult: Either<ApiError, YtDlpStatusDto> = YtDlpStatusDto(currentVersion = "2026.01.01").right()
    var settingsResult: Either<ApiError, SystemSettingsDto> = SystemSettingsDto().right()
    var updateSettingsResult: (SystemSettingsDto) -> Either<ApiError, SystemSettingsDto> = { it.right() }
    var updateYtDlpResult: Either<ApiError, YtDlpUpdateResponseDto> =
        YtDlpUpdateResponseDto(status = "UPDATED", message = "ok").right()

    val previewRequests = mutableListOf<PreviewRequestDto>()
    val channelQueries = mutableListOf<Pair<String?, String?>>()
    val createdJobs = mutableListOf<CreateJobRequestDto>()
    val savedSettings = mutableListOf<SystemSettingsDto>()
    var settingsLoads = 0
        private set
    var ytDlpUpdates = 0
        private set

    override suspend fun preview(request: PreviewRequestDto): Either<ApiError, PreviewResponseDto> {
        previewRequests += request
        gate?.await()
        return previewResult(request)
    }

    override suspend fun getChannels(
        tag: String?,
        channelId: String?,
        extractor: String?,
    ): Either<ApiError, ChannelListResponseDto> {
        channelQueries += channelId to extractor
        return channelsResult
    }

    override suspend fun createJob(request: CreateJobRequestDto): Either<ApiError, JobDto> {
        createdJobs += request
        gate?.await()
        return createJobResult(request)
    }

    override suspend fun getYtDlpStatus(): Either<ApiError, YtDlpStatusDto> = ytDlpStatusResult

    override suspend fun getSettings(): Either<ApiError, SystemSettingsDto> {
        settingsLoads++
        return settingsResult
    }

    override suspend fun updateSettings(request: SystemSettingsDto): Either<ApiError, SystemSettingsDto> {
        savedSettings += request
        gate?.await()
        return updateSettingsResult(request)
    }

    override suspend fun updateYtDlp(): Either<ApiError, YtDlpUpdateResponseDto> {
        ytDlpUpdates++
        gate?.await()
        return updateYtDlpResult
    }

    override suspend fun getWorkspaces(): Either<ApiError, WorkspaceListResponseDto> = unexpected("getWorkspaces")

    override suspend fun createWorkspace(request: CreateWorkspaceRequestDto): Either<ApiError, WorkspaceDto> =
        unexpected("createWorkspace")

    override suspend fun getJobs(status: String?, limit: Int, offset: Int): Either<ApiError, JobListResponseDto> =
        unexpected("getJobs")

    override suspend fun getJob(id: String): Either<ApiError, JobDto> = unexpected("getJob")

    override suspend fun cancelJob(id: String): Either<ApiError, JobDto> = unexpected("cancelJob")

    override suspend fun getRules(): Either<ApiError, RuleListResponseDto> = unexpected("getRules")

    override suspend fun createRule(request: CreateRuleRequestDto): Either<ApiError, RuleDto> = unexpected("createRule")

    override suspend fun getRule(id: String): Either<ApiError, RuleDto> = unexpected("getRule")

    override suspend fun updateRule(id: String, request: CreateRuleRequestDto): Either<ApiError, RuleDto> =
        unexpected("updateRule")

    override suspend fun deleteRule(id: String): Either<ApiError, Unit> = unexpected("deleteRule")

    override suspend fun getChannel(id: String): Either<ApiError, ChannelDto> = unexpected("getChannel")

    override suspend fun createChannel(request: CreateChannelDto): Either<ApiError, ChannelDto> =
        unexpected("createChannel")

    override suspend fun updateChannel(id: String, request: UpdateChannelDto): Either<ApiError, ChannelDto> =
        unexpected("updateChannel")

    override suspend fun deleteChannel(id: String): Either<ApiError, Unit> = unexpected("deleteChannel")

    override suspend fun getChannelTags(): Either<ApiError, TagListResponseDto> = unexpected("getChannelTags")

    private fun <T> unexpected(call: String): Either<ApiError, T> = error("Unexpected client call in this test: $call")
}

/** A server error as the client reports it. */
fun anHttpError(message: String = "Boom", status: Int = 500): ApiError =
    ApiError.Http(status = status, code = "INTERNAL_ERROR", message = message, correlationId = "corr-1")
