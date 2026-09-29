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
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.resource.ApiV1.Workspaces
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpStatusDto
import io.github.alelk.tgvd.api.contract.system.YtDlpUpdateResponseDto
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceListResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Ktor implementation of [TgVideoDownloaderClient]. [initDataProvider] is read on every request
 * (Telegram may refresh `initData`); [workspaceSlug] scopes the workspace endpoints.
 */
class TgVideoDownloaderClientImpl(
    httpClient: HttpClient,
    baseUrl: String,
    private val initDataProvider: () -> String = { "" },
    var workspaceSlug: String = "default",
) : TgVideoDownloaderClient {
    private val client =
        httpClient.config {
            install(Resources)
            defaultRequest {
                url(baseUrl.removeSuffix("/"))
                contentType(ContentType.Application.Json)
            }
            // Errors are values (Either): a non-2xx status must not throw.
            expectSuccess = false
        }

    private fun HttpRequestBuilder.auth() {
        header(INIT_DATA_HEADER, initDataProvider())
    }

    private fun workspace() = Workspaces.ById(workspaceSlug = workspaceSlug)

    private fun jobs() = Workspaces.ById.Jobs(workspace())

    private fun job(id: String) = Workspaces.ById.Jobs.ById(jobs(), id)

    private fun rules() = Workspaces.ById.Rules(workspace())

    private fun rule(id: String) = Workspaces.ById.Rules.ById(rules(), id)

    private fun channels() = Workspaces.ById.Channels(workspace())

    private fun channel(id: String) = Workspaces.ById.Channels.ById(channels(), id)

    private fun ytDlp() = ApiV1.System.YtDlp(ApiV1.System())

    override suspend fun getWorkspaces(): Either<ApiError, WorkspaceListResponseDto> =
        apiCall { client.get(Workspaces()) { auth() } }

    override suspend fun createWorkspace(request: CreateWorkspaceRequestDto): Either<ApiError, WorkspaceDto> = apiCall {
        client.post(Workspaces()) {
            auth()
            setBody(request)
        }
    }

    override suspend fun preview(request: PreviewRequestDto): Either<ApiError, PreviewResponseDto> = apiCall {
        client.post(Workspaces.ById.Preview(workspace())) {
            auth()
            setBody(request)
        }
    }

    override suspend fun createJob(request: CreateJobRequestDto): Either<ApiError, JobDto> = apiCall {
        client.post(jobs()) {
            auth()
            setBody(request)
        }
    }

    override suspend fun getJobs(status: String?, limit: Int, offset: Int): Either<ApiError, JobListResponseDto> =
        apiCall {
            client.get(Workspaces.ById.Jobs(workspace(), status = status, limit = limit, offset = offset)) { auth() }
        }

    override suspend fun getJob(id: String): Either<ApiError, JobDto> = apiCall { client.get(job(id)) { auth() } }

    override suspend fun cancelJob(id: String): Either<ApiError, JobDto> =
        apiCall { client.post(Workspaces.ById.Jobs.ById.Cancel(job(id))) { auth() } }

    override suspend fun getRules(): Either<ApiError, RuleListResponseDto> = apiCall { client.get(rules()) { auth() } }

    override suspend fun createRule(request: CreateRuleRequestDto): Either<ApiError, RuleDto> = apiCall {
        client.post(rules()) {
            auth()
            setBody(request)
        }
    }

    override suspend fun getRule(id: String): Either<ApiError, RuleDto> = apiCall { client.get(rule(id)) { auth() } }

    override suspend fun updateRule(id: String, request: CreateRuleRequestDto): Either<ApiError, RuleDto> = apiCall {
        client.put(rule(id)) {
            auth()
            setBody(request)
        }
    }

    override suspend fun deleteRule(id: String): Either<ApiError, Unit> =
        apiCallNoContent { client.delete(rule(id)) { auth() } }

    // --- Channels ---

    override suspend fun getChannels(
        tag: String?,
        channelId: String?,
        extractor: String?,
    ): Either<ApiError, ChannelListResponseDto> = apiCall {
        client.get(
            Workspaces.ById.Channels(workspace(), tag = tag, channelId = channelId, extractor = extractor),
        ) { auth() }
    }

    override suspend fun getChannel(id: String): Either<ApiError, ChannelDto> =
        apiCall { client.get(channel(id)) { auth() } }

    override suspend fun createChannel(request: CreateChannelDto): Either<ApiError, ChannelDto> = apiCall {
        client.post(channels()) {
            auth()
            setBody(request)
        }
    }

    override suspend fun updateChannel(id: String, request: UpdateChannelDto): Either<ApiError, ChannelDto> = apiCall {
        client.put(channel(id)) {
            auth()
            setBody(request)
        }
    }

    override suspend fun deleteChannel(id: String): Either<ApiError, Unit> =
        apiCallNoContent { client.delete(channel(id)) { auth() } }

    override suspend fun getChannelTags(): Either<ApiError, TagListResponseDto> =
        apiCall { client.get(Workspaces.ById.Channels.Tags(channels())) { auth() } }

    // --- System ---

    override suspend fun getYtDlpStatus(): Either<ApiError, YtDlpStatusDto> =
        apiCall { client.get(ApiV1.System.YtDlp.Status(ytDlp())) { auth() } }

    override suspend fun updateYtDlp(): Either<ApiError, YtDlpUpdateResponseDto> =
        apiCall { client.post(ApiV1.System.YtDlp.Update(ytDlp())) { auth() } }

    override suspend fun getSettings(): Either<ApiError, SystemSettingsDto> =
        apiCall { client.get(ApiV1.System.Settings()) { auth() } }

    override suspend fun updateSettings(request: SystemSettingsDto): Either<ApiError, SystemSettingsDto> = apiCall {
        client.put(ApiV1.System.Settings()) {
            auth()
            setBody(request)
        }
    }

    private companion object {
        const val INIT_DATA_HEADER = "X-Telegram-Init-Data"
    }
}
