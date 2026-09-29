package io.github.alelk.tgvd.api.client

import arrow.core.Either
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

/**
 * Typed HTTP client of the server API. Every call returns its failure as a value ([ApiError]) and
 * never throws, except for coroutine cancellation.
 */
interface TgVideoDownloaderClient {
    suspend fun getWorkspaces(): Either<ApiError, WorkspaceListResponseDto>

    suspend fun createWorkspace(request: CreateWorkspaceRequestDto): Either<ApiError, WorkspaceDto>

    suspend fun preview(request: PreviewRequestDto): Either<ApiError, PreviewResponseDto>

    suspend fun createJob(request: CreateJobRequestDto): Either<ApiError, JobDto>

    suspend fun getJobs(status: String? = null, limit: Int = 20, offset: Int = 0): Either<ApiError, JobListResponseDto>

    suspend fun getJob(id: String): Either<ApiError, JobDto>

    suspend fun cancelJob(id: String): Either<ApiError, JobDto>

    suspend fun getRules(): Either<ApiError, RuleListResponseDto>

    suspend fun createRule(request: CreateRuleRequestDto): Either<ApiError, RuleDto>

    suspend fun getRule(id: String): Either<ApiError, RuleDto>

    suspend fun updateRule(id: String, request: CreateRuleRequestDto): Either<ApiError, RuleDto>

    suspend fun deleteRule(id: String): Either<ApiError, Unit>

    // --- Channels ---

    suspend fun getChannels(
        tag: String? = null,
        channelId: String? = null,
        extractor: String? = null,
    ): Either<ApiError, ChannelListResponseDto>

    suspend fun getChannel(id: String): Either<ApiError, ChannelDto>

    suspend fun createChannel(request: CreateChannelDto): Either<ApiError, ChannelDto>

    suspend fun updateChannel(id: String, request: UpdateChannelDto): Either<ApiError, ChannelDto>

    suspend fun deleteChannel(id: String): Either<ApiError, Unit>

    suspend fun getChannelTags(): Either<ApiError, TagListResponseDto>

    // --- System ---

    suspend fun getYtDlpStatus(): Either<ApiError, YtDlpStatusDto>

    suspend fun updateYtDlp(): Either<ApiError, YtDlpUpdateResponseDto>

    suspend fun getSettings(): Either<ApiError, SystemSettingsDto>

    suspend fun updateSettings(request: SystemSettingsDto): Either<ApiError, SystemSettingsDto>
}
