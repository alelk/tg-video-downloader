package io.github.alelk.tgvd.api.client

import io.github.alelk.tgvd.api.contract.job.*
import io.github.alelk.tgvd.api.contract.preview.*
import io.github.alelk.tgvd.api.contract.rule.*
import io.github.alelk.tgvd.api.contract.system.*
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.*
import io.ktor.http.*

class TgVideoDownloaderClientImpl(
    private val httpClient: HttpClient,
    baseUrl: String,
    private val initDataProvider: () -> String = { "" },
) : TgVideoDownloaderClient {

    private val client = httpClient.config {
        install(Resources)
        defaultRequest {
            url(baseUrl.removeSuffix("/"))
            contentType(ContentType.Application.Json)
        }
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Telegram-Init-Data", initDataProvider())
    }

    override suspend fun preview(request: PreviewRequestDto): PreviewResponseDto =
        client.post(ApiV1.Preview()) {
            auth()
            setBody(request)
        }.body()

    override suspend fun createJob(request: CreateJobRequestDto): JobDto =
        client.post(ApiV1.Jobs()) {
            auth()
            setBody(request)
        }.body()

    override suspend fun getJobs(status: String?, limit: Int, offset: Int): JobListResponseDto =
        client.get(ApiV1.Jobs(status = status, limit = limit, offset = offset)) {
            auth()
        }.body()

    override suspend fun getJob(id: String): JobDto =
        client.get(ApiV1.Jobs.ById(ApiV1.Jobs(), id)) {
            auth()
        }.body()

    override suspend fun cancelJob(id: String): JobDto =
        client.post(ApiV1.Jobs.ById.Cancel(ApiV1.Jobs.ById(ApiV1.Jobs(), id))) {
            auth()
        }.body()

    override suspend fun getRules(): RuleListResponseDto =
        client.get(ApiV1.Rules()) {
            auth()
        }.body()

    override suspend fun createRule(request: CreateRuleRequestDto): RuleDto =
        client.post(ApiV1.Rules()) {
            auth()
            setBody(request)
        }.body()

    override suspend fun getRule(id: String): RuleDto =
        client.get(ApiV1.Rules.ById(ApiV1.Rules(), id)) {
            auth()
        }.body()

    override suspend fun updateRule(id: String, request: CreateRuleRequestDto): RuleDto =
        client.put(ApiV1.Rules.ById(ApiV1.Rules(), id)) {
            auth()
            setBody(request)
        }.body()

    override suspend fun deleteRule(id: String) {
        client.delete(ApiV1.Rules.ById(ApiV1.Rules(), id)) {
            auth()
        }
    }

    override suspend fun getYtDlpStatus(): YtDlpStatusDto =
        client.get(ApiV1.System.YtDlp.Status(ApiV1.System.YtDlp(ApiV1.System()))) {
            auth()
        }.body()

    override suspend fun updateYtDlp(): YtDlpUpdateResponseDto =
        client.post(ApiV1.System.YtDlp.Update(ApiV1.System.YtDlp(ApiV1.System()))) {
            auth()
        }.body()
}

