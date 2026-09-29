package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobListResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.job.parseJobId
import io.github.alelk.tgvd.api.mapping.job.toDomainRequest
import io.github.alelk.tgvd.api.mapping.job.toDto
import io.github.alelk.tgvd.domain.job.CancelJobUseCase
import io.github.alelk.tgvd.domain.job.CreateJobUseCase
import io.github.alelk.tgvd.domain.job.GetJobUseCase
import io.github.alelk.tgvd.domain.job.ListJobsUseCase
import io.github.alelk.tgvd.domain.job.RetryJobUseCase
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi

private val logger = KotlinLogging.logger {}

fun Route.jobRoutes() {
    jobCollectionRoutes()
    jobByIdRoutes()
}

/** `POST …/jobs` and `GET …/jobs`. */
@OptIn(ExperimentalUuidApi::class)
private fun Route.jobCollectionRoutes() {
    val createJob by inject<CreateJobUseCase>()
    val listJobs by inject<ListJobsUseCase>()

    post<ApiV1.Workspaces.ById.Jobs> { res ->
        val body = call.receive<CreateJobRequestDto>()
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                val request = body.toDomainRequest().bind()
                createJob(slug, call.telegramUser.id, request).bind()
            }
        result.onRight { created ->
            created.saveAsRuleError?.let { error ->
                logger.warn { "SaveAsRule failed for job ${created.job.id.value}: ${error.message}" }
            }
        }
        call.respondEither<JobDto, _>(result, HttpStatusCode.Created) { it.job.toDto() }
    }

    get<ApiV1.Workspaces.ById.Jobs> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
                listJobs(slug, call.telegramUser.id, res.status, res.offset, res.limit).bind()
            }
        call.respondEither<JobListResponseDto, _>(result) { page ->
            JobListResponseDto(
                items = page.items.map { it.toDto() },
                total = page.total,
                limit = res.limit,
                offset = res.offset,
            )
        }
    }
}

/** `GET …/jobs/{id}`, `POST …/jobs/{id}/cancel`, `POST …/jobs/{id}/retry`. */
private fun Route.jobByIdRoutes() {
    val getJob by inject<GetJobUseCase>()
    val cancelJob by inject<CancelJobUseCase>()
    val retryJob by inject<RetryJobUseCase>()

    get<ApiV1.Workspaces.ById.Jobs.ById> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.workspaceSlug).bind()
                val jobId = parseJobId(res.id).bind()
                getJob(slug, call.telegramUser.id, jobId).bind()
            }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Cancel> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.parent.workspaceSlug).bind()
                val jobId = parseJobId(res.parent.id).bind()
                cancelJob(slug, call.telegramUser.id, jobId).bind()
            }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Retry> { res ->
        val result =
            either {
                val slug = parseWorkspaceSlug(res.parent.parent.parent.workspaceSlug).bind()
                val jobId = parseJobId(res.parent.id).bind()
                retryJob(slug, call.telegramUser.id, jobId).bind()
            }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }
}
