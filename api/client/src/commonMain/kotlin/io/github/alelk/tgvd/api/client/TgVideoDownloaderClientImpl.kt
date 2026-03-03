package io.github.alelk.tgvd.api.client

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.job.*
import io.github.alelk.tgvd.api.contract.preview.*
import io.github.alelk.tgvd.api.contract.rule.*
import io.github.alelk.tgvd.api.contract.system.*
import io.github.alelk.tgvd.api.contract.workspace.*
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.contract.resource.ApiV1.Workspaces
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class TgVideoDownloaderClientImpl(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val initDataProvider: () -> String = { "" },
    var workspaceSlug: String = "default",
) : TgVideoDownloaderClient {

    private val client = httpClient.config {
        install(Resources)
        defaultRequest {
            url(baseUrl.removeSuffix("/"))
            contentType(ContentType.Application.Json)
        }
        expectSuccess = false
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Telegram-Init-Data", initDataProvider())
    }

    private suspend inline fun <reified T> HttpResponse.checkAndBody(): T {
        if (status.isSuccess()) {
            if (T::class == Unit::class) @Suppress("UNCHECKED_CAST") return Unit as T
            return body<T>()
        }
        val apiError = try {
            body<ApiErrorDto>()
        } catch (_: Exception) {
            ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "HTTP_${status.value}",
                    message = "${status.value} ${status.description}: ${bodyAsText().take(500)}",
                    correlationId = "",
                ),
            )
        }
        throw ApiException(apiError, status.value)
    }

    private fun workspace() = Workspaces.ById(workspaceSlug = workspaceSlug)

    override suspend fun getWorkspaces(): WorkspaceListResponseDto =
        client.get(Workspaces()) {
            auth()
        }.checkAndBody()

    override suspend fun createWorkspace(request: CreateWorkspaceRequestDto): WorkspaceDto =
        client.post(Workspaces()) {
            auth()
            setBody(request)
        }.checkAndBody()

    override suspend fun preview(request: PreviewRequestDto): PreviewResponseDto =
        client.post(Workspaces.ById.Preview(workspace())) {
            auth()
            setBody(request)
        }.checkAndBody()

    override suspend fun createJob(request: CreateJobRequestDto): JobDto =
        client.post(Workspaces.ById.Jobs(workspace())) {
            auth()
            setBody(request)
        }.checkAndBody()

    override suspend fun getJobs(status: String?, limit: Int, offset: Int): JobListResponseDto =
        client.get(Workspaces.ById.Jobs(workspace(), status = status, limit = limit, offset = offset)) {
            auth()
        }.checkAndBody()

    override suspend fun getJob(id: String): JobDto =
        client.get(Workspaces.ById.Jobs.ById(Workspaces.ById.Jobs(workspace()), id)) {
            auth()
        }.checkAndBody()

    override suspend fun cancelJob(id: String): JobDto =
        client.post(Workspaces.ById.Jobs.ById.Cancel(Workspaces.ById.Jobs.ById(Workspaces.ById.Jobs(workspace()), id))) {
            auth()
        }.checkAndBody()

    override suspend fun getRules(): RuleListResponseDto =
        client.get(Workspaces.ById.Rules(workspace())) {
            auth()
        }.checkAndBody()

    override suspend fun createRule(request: CreateRuleRequestDto): RuleDto =
        client.post(Workspaces.ById.Rules(workspace())) {
            auth()
            setBody(request)
        }.checkAndBody()

    override suspend fun getRule(id: String): RuleDto =
        client.get(Workspaces.ById.Rules.ById(Workspaces.ById.Rules(workspace()), id)) {
            auth()
        }.checkAndBody()

    override suspend fun updateRule(id: String, request: CreateRuleRequestDto): RuleDto =
        client.put(Workspaces.ById.Rules.ById(Workspaces.ById.Rules(workspace()), id)) {
            auth()
            setBody(request)
        }.checkAndBody()

    override suspend fun deleteRule(id: String) {
        client.delete(Workspaces.ById.Rules.ById(Workspaces.ById.Rules(workspace()), id)) {
            auth()
        }.checkAndBody<Unit>()
    }

    override suspend fun getYtDlpStatus(): YtDlpStatusDto =
        client.get(ApiV1.System.YtDlp.Status(ApiV1.System.YtDlp(ApiV1.System()))) {
            auth()
        }.checkAndBody()

    override suspend fun updateYtDlp(): YtDlpUpdateResponseDto =
        client.post(ApiV1.System.YtDlp.Update(ApiV1.System.YtDlp(ApiV1.System()))) {
            auth()
        }.checkAndBody()

    override suspend fun getSettings(): SystemSettingsDto =
        client.get(ApiV1.System.Settings()) {
            auth()
        }.checkAndBody()

    override suspend fun updateSettings(request: SystemSettingsDto): SystemSettingsDto =
        client.put(ApiV1.System.Settings()) {
            auth()
            setBody(request)
        }.checkAndBody()
}
