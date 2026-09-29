package io.github.alelk.tgvd.api.mapping.rule

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import io.github.alelk.tgvd.api.mapping.common.parseId
import io.github.alelk.tgvd.api.mapping.metadata.toDomain
import io.github.alelk.tgvd.api.mapping.storage.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.UpdateRuleRequest
import io.github.alelk.tgvd.domain.storage.OutputRule
import kotlin.uuid.ExperimentalUuidApi

/** Parses the `ruleId` path parameter. */
@OptIn(ExperimentalUuidApi::class)
fun parseRuleId(raw: String): Either<DomainError.ValidationError, RuleId> = parseId(raw, "ruleId", ::RuleId)

/** `POST …/rules` body → [CreateRuleRequest]; the workspace comes from the path, via the use-case. */
fun CreateRuleRequestDto.toDomainRequest(): Either<DomainError.ValidationError, CreateRuleRequest> = either {
    CreateRuleRequest(
        name = name,
        match = match.toDomain().bind(),
        metadataTemplate = metadataTemplate.toDomain(),
        downloadPolicy = downloadPolicy.toDomain(),
        outputs = outputs.toDomain().bind(),
        enabled = enabled,
        priority = priority,
    )
}

/** `PUT …/rules/{id}` body (the full rule, as the client sends it) → [UpdateRuleRequest]. */
fun CreateRuleRequestDto.toUpdateDomain(): Either<DomainError.ValidationError, UpdateRuleRequest> = either {
    UpdateRuleRequest(
        name = name,
        match = match.toDomain().bind(),
        metadataTemplate = metadataTemplate.toDomain(),
        downloadPolicy = downloadPolicy.toDomain(),
        outputs = outputs.toDomain().bind(),
        enabled = enabled,
        priority = priority,
    )
}

private fun List<OutputRuleDto>.toDomain(): Either<DomainError.ValidationError, List<OutputRule>> = either {
    mapIndexed { i, output -> output.toDomain("outputs[$i]").bind() }
}
