package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobListResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.job.toDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.rule.buildSaveAsRuleRequest
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.api.mapping.video.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.job.*
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.CreateRuleUseCase
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.validateStoragePaths
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.requireWorkspaceMember
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.jobRoutes() {
    val createJob by inject<CreateJobUseCase>()
    val cancelJob by inject<CancelJobUseCase>()
    val retryJob by inject<RetryJobUseCase>()
    val createRule by inject<CreateRuleUseCase>()
    val jobRepository by inject<JobRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()

    post<ApiV1.Workspaces.ById.Jobs> { res ->
        val request = call.receive<CreateJobRequestDto>()
        val user = call.telegramUser

        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val metadata = request.metadata.toDomain().bind()
            val additionalPaths =
                request.storagePlan.additional.mapIndexed { i, it ->
                    "storagePlan.additional[$i]" to it.path
                }
            validateStoragePaths(
                mapOf("storagePlan.original" to request.storagePlan.original.path) + additionalPaths
            ).bind()
            val job =
                createJob(
                    CreateJobRequest(
                        workspaceId = ws.id,
                        source = request.source.toDomain(),
                        ruleId = request.ruleId?.let { RuleId(Uuid.parse(it)) },
                        metadata = metadata,
                        metadataSource = request.metadataSource.toDomain(),
                        storagePlan = request.storagePlan.toDomain(),
                        createdBy = user.id,
                    )
                ).bind()

            // Handle saveAsRule — create a rule for this channel if requested
            request.saveAsRule?.let { saveAs ->
                val matchBy =
                    when (saveAs.matchBy.lowercase()) {
                        "channelid", "channel_id" -> RuleMatch.ChannelId(request.videoInfo.channelId)
                        "channelname", "channel_name" -> RuleMatch.ChannelName(request.videoInfo.channelName)
                        else -> RuleMatch.ChannelId(request.videoInfo.channelId)
                    }
                val ruleRequest =
                    buildSaveAsRuleRequest(
                        workspaceId = ws.id,
                        match = matchBy,
                        job = job,
                        includeCategory = saveAs.includeCategory,
                        includeMetadataTemplate = saveAs.includeMetadataTemplate,
                        includeStoragePolicy = saveAs.includeStoragePolicy,
                        enabled = saveAs.enabled,
                    )
                createRule(ruleRequest).fold(
                    ifLeft = { err ->
                        io.github.oshai.kotlinlogging.KotlinLogging.logger {}
                            .warn { "SaveAsRule failed for job ${job.id.value}: ${err.message}" }
                    },
                    ifRight = { /* rule created */ },
                )
            }

            job
        }

        call.respondEither<JobDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    get<ApiV1.Workspaces.ById.Jobs> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val jobs = jobRepository.findByWorkspace(ws.id)
            val filtered =
                if (res.status != null)
                    jobs.filter { it.status.name.equals(res.status, ignoreCase = true) }
                else jobs
            val paged = filtered.drop(res.offset).take(res.limit)
            JobListResponseDto(
                items = paged.map { it.toDto() },
                total = filtered.size,
                limit = res.limit,
                offset = res.offset,
            )
        }
        call.respondEither(result)
    }

    get<ApiV1.Workspaces.ById.Jobs.ById> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val jobId = parseId(res.id, "jobId", ::JobId).bind()
            val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
            if (job.workspaceId != ws.id) raise(DomainError.JobNotFound(jobId))
            job
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Cancel> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val jobId = parseId(res.parent.id, "jobId", ::JobId).bind()
            val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
            if (job.workspaceId != ws.id) raise(DomainError.JobNotFound(jobId))
            cancelJob(jobId).bind()
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Retry> { res ->
        val user = call.telegramUser
        val result = either {
            val slug = parseWorkspaceSlug(res.parent.parent.parent.workspaceSlug).bind()
            val ws = workspaceRepository.requireWorkspaceMember(slug, user).bind()
            val jobId = parseId(res.parent.id, "jobId", ::JobId).bind()
            val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
            if (job.workspaceId != ws.id) raise(DomainError.JobNotFound(jobId))
            retryJob(jobId).bind()
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }
}


