package io.github.alelk.tgvd.api.client

import io.github.alelk.tgvd.api.contract.job.*
import io.github.alelk.tgvd.api.contract.preview.*
import io.github.alelk.tgvd.api.contract.rule.*
import io.github.alelk.tgvd.api.contract.system.*
import io.github.alelk.tgvd.api.contract.workspace.*

interface TgVideoDownloaderClient {

    suspend fun getWorkspaces(): WorkspaceListResponseDto

    suspend fun createWorkspace(request: CreateWorkspaceRequestDto): WorkspaceDto

    suspend fun preview(request: PreviewRequestDto): PreviewResponseDto

    suspend fun createJob(request: CreateJobRequestDto): JobDto

    suspend fun getJobs(status: String? = null, limit: Int = 20, offset: Int = 0): JobListResponseDto

    suspend fun getJob(id: String): JobDto

    suspend fun cancelJob(id: String): JobDto

    suspend fun getRules(): RuleListResponseDto

    suspend fun createRule(request: CreateRuleRequestDto): RuleDto

    suspend fun getRule(id: String): RuleDto

    suspend fun updateRule(id: String, request: CreateRuleRequestDto): RuleDto

    suspend fun deleteRule(id: String)

    suspend fun getYtDlpStatus(): YtDlpStatusDto

    suspend fun updateYtDlp(): YtDlpUpdateResponseDto
}

