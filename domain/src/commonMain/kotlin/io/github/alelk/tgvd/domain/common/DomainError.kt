package io.github.alelk.tgvd.domain.common

import io.github.alelk.tgvd.domain.job.JobStatus
import kotlin.uuid.ExperimentalUuidApi

sealed interface DomainError {
    val message: String

    // === Validation ===
    data class ValidationError(val field: String, override val message: String) : DomainError
    data class InvalidUrl(val url: Url, override val message: String = "Invalid URL: ${url.value}") : DomainError

    // === Not Found ===
    @OptIn(ExperimentalUuidApi::class)
    data class RuleNotFound(val id: RuleId, override val message: String = "Rule not found: ${id.value}") : DomainError
    @OptIn(ExperimentalUuidApi::class)
    data class JobNotFound(val id: JobId, override val message: String = "Job not found: ${id.value}") : DomainError

    // === Video ===
    data class VideoUnavailable(val videoId: VideoId, val reason: String, override val message: String = "Video unavailable: ${videoId.value} - $reason") : DomainError
    data class VideoExtractionFailed(val url: Url, val cause: String, override val message: String = "Failed to extract video info: $cause") : DomainError

    // === Job ===
    data class JobAlreadyExists(val videoId: VideoId, val existingJobId: JobId, override val message: String = "Job already exists for video ${videoId.value}") : DomainError
    data class JobCannotBeCancelled(val id: JobId, val currentStatus: JobStatus, override val message: String = "Cannot cancel job in status $currentStatus") : DomainError
    data class DownloadFailed(val jobId: JobId, val cause: String, override val message: String = "Download failed: $cause") : DomainError
    data class PostProcessingFailed(val jobId: JobId, val phase: io.github.alelk.tgvd.domain.job.JobPhase, val cause: String, override val message: String = "Post-processing failed at $phase: $cause") : DomainError

    // === Storage ===
    data class PathTraversalAttempt(val path: FilePath, override val message: String = "Path traversal attempt: ${path.value}") : DomainError
    data class StorageFailed(val path: FilePath, val cause: String, override val message: String = "Storage failed for ${path.value}: $cause") : DomainError

    // === Auth ===
    data class Unauthorized(override val message: String = "Unauthorized") : DomainError
    data class Forbidden(val userId: TelegramUserId, override val message: String = "User ${userId.value} not allowed") : DomainError

    // === LLM ===
    data class LlmError(val provider: String, override val message: String, val statusCode: Int? = null) : DomainError
}
