package io.github.alelk.tgvd.api.mapping.rule

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.mapping.common.toDomain
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.rule.RuleMatch

fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = either {
    when (this@toDomain) {
        is RuleMatchDto.AllOf -> {
            ensure(matches.isNotEmpty()) { DomainError.ValidationError("matches", "Cannot be empty") }
            RuleMatch.AllOf(matches.map { it.toDomain().bind() })
        }

        is RuleMatchDto.AnyOf -> {
            ensure(matches.isNotEmpty()) { DomainError.ValidationError("matches", "Cannot be empty") }
            RuleMatch.AnyOf(matches.map { it.toDomain().bind() })
        }

        is RuleMatchDto.ChannelId -> {
            ensure(value.isNotBlank()) { DomainError.ValidationError("value", "Cannot be blank") }
            RuleMatch.ChannelId(value)
        }

        is RuleMatchDto.ChannelName -> {
            ensure(value.isNotBlank()) { DomainError.ValidationError("value", "Cannot be blank") }
            RuleMatch.ChannelName(value, ignoreCase)
        }

        is RuleMatchDto.TitleRegex -> {
            ensure(pattern.isNotBlank()) { DomainError.ValidationError("pattern", "Cannot be blank") }
            RuleMatch.TitleRegex(pattern)
        }

        is RuleMatchDto.UrlRegex -> {
            ensure(pattern.isNotBlank()) { DomainError.ValidationError("pattern", "Cannot be blank") }
            RuleMatch.UrlRegex(pattern)
        }

        is RuleMatchDto.CategoryEquals -> {
            RuleMatch.CategoryEquals(category.toDomain())
        }

        is RuleMatchDto.HasTag -> {
            ensure(tag.isNotBlank()) { DomainError.ValidationError("tag", "Cannot be blank") }
            RuleMatch.HasTag(Tag(tag))
        }
    }
}
