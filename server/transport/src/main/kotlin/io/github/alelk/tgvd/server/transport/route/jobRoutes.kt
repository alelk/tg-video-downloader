package io.github.alelk.tgvd.server.transport.route

import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobListResponseDto
import io.github.alelk.tgvd.api.contract.resource.ApiV1
import io.github.alelk.tgvd.api.mapping.job.toDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.api.mapping.video.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.job.*
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.workspace.WorkspaceRepository
import io.github.alelk.tgvd.server.transport.auth.parseWorkspaceSlug
import io.github.alelk.tgvd.server.transport.auth.telegramUser
import io.github.alelk.tgvd.server.transport.util.parseId
import io.github.alelk.tgvd.server.transport.util.respondEither
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.jobRoutes() {
    val createJobUseCase by inject<CreateJobUseCase>()
    val jobRepository by inject<JobRepository>()
    val workspaceRepository by inject<WorkspaceRepository>()

    post<ApiV1.Workspaces.ById.Jobs> { res ->
        val request = call.receive<CreateJobRequestDto>()
        val user = call.telegramUser

        val result = either {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val metadata = request.metadata.toDomain().bind()
            validateStoragePaths(request.storagePlan.original.path, request.storagePlan.additional.map { it.path }).bind()
            val createRequest = CreateJobUseCase.CreateJobRequest(
                workspaceId = ws.id,
                source = request.source.toDomain(),
                ruleId = request.ruleId?.let { RuleId(Uuid.parse(it)) },
                metadata = metadata,
                metadataSource = MetadataSource.RULE,
                storagePlan = request.storagePlan.toDomain(),
                createdBy = user.id,
            )
            createJobUseCase.execute(createRequest).bind()
        }

        call.respondEither<JobDto, _>(result, HttpStatusCode.Created) { it.toDto() }
    }

    get<ApiV1.Workspaces.ById.Jobs> { res ->
        val result = either<DomainError, JobListResponseDto> {
            val slug = parseWorkspaceSlug(res.parent.workspaceSlug).bind()
            val ws = workspaceRepository.findBySlug(slug) ?: raise(DomainError.WorkspaceNotFoundBySlug(slug))
            val jobs = jobRepository.findByWorkspace(ws.id)

            val filtered = if (res.status != null) {
                jobs.filter { it.status.name.equals(res.status, ignoreCase = true) }
            } else jobs

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
        val result = either {
            val jobId = parseId(res.id, "jobId", ::JobId).bind()
            jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Cancel> { res ->
        val result = either {
            val jobId = parseId(res.parent.id, "jobId", ::JobId).bind()
            val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
            if (!job.status.isCancellable) raise(DomainError.JobCannotBeCancelled(jobId, job.status))
            jobRepository.updateStatus(jobId, JobStatus.CANCELLED).bind()
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }

    post<ApiV1.Workspaces.ById.Jobs.ById.Retry> { res ->
        val result = either {
            val jobId = parseId(res.parent.id, "jobId", ::JobId).bind()
            val job = jobRepository.findById(jobId) ?: raise(DomainError.JobNotFound(jobId))
            if (!job.status.isRetryable) raise(DomainError.JobCannotBeRetried(jobId, job.status))
            jobRepository.updateStatus(jobId, JobStatus.PENDING).bind()
        }
        call.respondEither<JobDto, _>(result) { it.toDto() }
    }
}

/**
 * Validate that file paths do not contain path traversal sequences.
 * Individual path segments (file names) are checked for unsafe characters.
 * The directory separators / and \ are allowed in the full path string.
 */
private fun validateStoragePaths(
    originalPath: String,
    additionalPaths: List<String>,
): arrow.core.Either<DomainError.ValidationError, Unit> = arrow.core.Either.catch {
    val allPaths = listOf("storagePlan.original" to originalPath) +
        additionalPaths.mapIndexed { i, p -> "storagePlan.additional[$i]" to p }

    for ((field, path) in allPaths) {
        // Reject path traversal in any segment
        if (path.contains("..")) {
            return arrow.core.Either.Left(
                DomainError.ValidationError(field, "Path traversal ('..') is not allowed in '$field'")
            )
        }
        // Check each filename segment (split by / and \)
        val segments = path.split("/", "\\").filter { it.isNotBlank() }
        for (segment in segments) {
            // Only check for characters that are unsafe in a file name, not path separators
            val unsafeFileNameChars = "[:*?\"<>|]".toRegex()
            val found = unsafeFileNameChars.find(segment)?.value
            if (found != null) {
                return arrow.core.Either.Left(
                    DomainError.ValidationError(
                        field,
                        "Path segment '$segment' in '$field' contains forbidden character '$found'"
                    )
                )
            }
        }
    }
}.mapLeft { DomainError.ValidationError("storagePlan", it.message ?: "Path validation failed") }
    .map { }

