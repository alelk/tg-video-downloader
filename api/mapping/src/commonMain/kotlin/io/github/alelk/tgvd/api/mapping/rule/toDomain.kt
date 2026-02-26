package io.github.alelk.tgvd.api.mapping.rule

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.traverse
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.rule.RuleMatch

fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.AllOf ->
        if (matches.isEmpty()) DomainError.ValidationError("matches", "Cannot be empty").left()
        else matches.traverse { it.toDomain() }.map { RuleMatch.AllOf(it) }
    is RuleMatchDto.AnyOf ->
        if (matches.isEmpty()) DomainError.ValidationError("matches", "Cannot be empty").left()
        else matches.traverse { it.toDomain() }.map { RuleMatch.AnyOf(it) }
    is RuleMatchDto.ChannelId ->
        if (value.isBlank()) DomainError.ValidationError("value", "Cannot be blank").left()
        else RuleMatch.ChannelId(value).right()
    is RuleMatchDto.ChannelName ->
        if (value.isBlank()) DomainError.ValidationError("value", "Cannot be blank").left()
        else RuleMatch.ChannelName(value, ignoreCase).right()
    is RuleMatchDto.TitleRegex ->
        if (pattern.isBlank()) DomainError.ValidationError("pattern", "Cannot be blank").left()
        else RuleMatch.TitleRegex(pattern).right()
    is RuleMatchDto.UrlRegex ->
        if (pattern.isBlank()) DomainError.ValidationError("pattern", "Cannot be blank").left()
        else RuleMatch.UrlRegex(pattern).right()
}
