package io.github.alelk.tgvd.api.mapping.job

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.SaveAsRuleDto
import io.github.alelk.tgvd.api.mapping.common.parseId
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.api.mapping.video.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.job.CreateJobRequest
import io.github.alelk.tgvd.domain.job.SaveAsRule
import kotlin.uuid.ExperimentalUuidApi

/** Parses the `jobId` path parameter. */
@OptIn(ExperimentalUuidApi::class)
fun parseJobId(raw: String): Either<DomainError.ValidationError, JobId> = parseId(raw, "jobId", ::JobId)

/**
 * Structural parsing of `POST …/jobs`: wire strings → domain types. The business checks (membership,
 * `videoInfo.videoId` = `source.videoId`, media selection, storage paths) are made by
 * [io.github.alelk.tgvd.domain.job.CreateJobUseCase].
 *
 * `category` is not read: the category follows from [CreateJobRequestDto.metadata].
 */
@OptIn(ExperimentalUuidApi::class)
fun CreateJobRequestDto.toDomainRequest(): Either<DomainError, CreateJobRequest> = either {
    val metadata = metadata.toDomain().bind()
    ensure(source.videoId.isNotBlank()) { DomainError.ValidationError("source.videoId", "Cannot be blank") }
    ensure(videoInfo.videoId.isNotBlank()) { DomainError.ValidationError("videoInfo.videoId", "Cannot be blank") }
    CreateJobRequest(
        source = source.toDomain().bind(),
        videoInfo = videoInfo.toDomain().bind(),
        ruleId = ruleId?.let { parseId(it, "ruleId", ::RuleId).bind() },
        metadata = metadata,
        metadataSource = metadataSource.toDomain(),
        storagePlan = storagePlan.toDomain().bind(),
        mediaSelection = mediaSelection?.toDomain(),
        saveAsRule = saveAsRule?.toDomain(),
    )
}

/** `includeCategory` is accepted on the wire but has no effect (known issue, see project-status). */
fun SaveAsRuleDto.toDomain(): SaveAsRule = SaveAsRule(
    matchBy = parseSaveAsRuleMatchBy(matchBy),
    includeMetadataTemplate = includeMetadataTemplate,
    includeStoragePolicy = includeStoragePolicy,
    enabled = enabled,
)

/**
 * `saveAsRule.matchBy`, case-insensitive: `channelId`/`channel_id` → [SaveAsRule.MatchBy.CHANNEL_ID],
 * `channelName`/`channel_name` → [SaveAsRule.MatchBy.CHANNEL_NAME]; any other value falls back to
 * [SaveAsRule.MatchBy.CHANNEL_ID].
 */
fun parseSaveAsRuleMatchBy(raw: String): SaveAsRule.MatchBy = when (raw.lowercase()) {
    "channelname", "channel_name" -> SaveAsRule.MatchBy.CHANNEL_NAME
    else -> SaveAsRule.MatchBy.CHANNEL_ID
}
