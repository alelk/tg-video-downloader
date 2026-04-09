package io.github.alelk.tgvd.api.mapping.rule

import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.UpdateRuleRequest
import arrow.core.Either
import arrow.core.raise.either

fun CreateRuleRequestDto.toDomainRequest(workspaceId: WorkspaceId): Either<DomainError.ValidationError, CreateRuleRequest> =
    either {
        CreateRuleRequest(
            workspaceId = workspaceId,
            name = name,
            match = match.toDomain().bind(),
            metadataTemplate = metadataTemplate.toDomain(),
            downloadPolicy = downloadPolicy.toDomain(),
            outputs = outputs.map { it.toDomain() },
            enabled = enabled,
            priority = priority,
        )
    }

fun CreateRuleRequestDto.toUpdateDomain(): Either<DomainError.ValidationError, UpdateRuleRequest> =
    either {
        UpdateRuleRequest(
            name = name,
            match = match.toDomain().bind(),
            metadataTemplate = metadataTemplate.toDomain(),
            downloadPolicy = downloadPolicy.toDomain(),
            outputs = outputs.map { it.toDomain() },
            enabled = enabled,
            priority = priority,
        )
    }


