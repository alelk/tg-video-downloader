package io.github.alelk.tgvd.api.mapping.job

import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobErrorDto
import io.github.alelk.tgvd.api.contract.job.JobProgressDto
import io.github.alelk.tgvd.api.mapping.common.toDto
import io.github.alelk.tgvd.api.mapping.metadata.toDto
import io.github.alelk.tgvd.api.mapping.storage.toDto
import io.github.alelk.tgvd.api.mapping.video.toDto
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.metadata.category
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun Job.toDto(): JobDto = JobDto(
    id = id.value.toString(),
    status = status.name.lowercase(),
    source = source.toDto(),
    ruleId = ruleId?.value?.toString(),
    category = metadata.category.toDto(),
    metadata = metadata.toDto(),
    storagePlan = storagePlan.toDto(),
    progress = phase?.let {
        JobProgressDto(phase = it.name.lowercase(), percent = progress ?: 0)
    },
    error = errorMessage?.let {
        JobErrorDto(code = "ERROR", message = it)
    },
    attempt = 1,
    createdBy = createdBy.value.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)


