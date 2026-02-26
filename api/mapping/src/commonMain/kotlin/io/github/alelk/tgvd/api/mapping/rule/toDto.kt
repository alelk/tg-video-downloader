package io.github.alelk.tgvd.api.mapping.rule

import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.domain.rule.RuleMatch

fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.AllOf -> RuleMatchDto.AllOf(matches.map { it.toDto() })
    is RuleMatch.AnyOf -> RuleMatchDto.AnyOf(matches.map { it.toDto() })
    is RuleMatch.ChannelId -> RuleMatchDto.ChannelId(value)
    is RuleMatch.ChannelName -> RuleMatchDto.ChannelName(value, ignoreCase)
    is RuleMatch.TitleRegex -> RuleMatchDto.TitleRegex(pattern)
    is RuleMatch.UrlRegex -> RuleMatchDto.UrlRegex(pattern)
}

