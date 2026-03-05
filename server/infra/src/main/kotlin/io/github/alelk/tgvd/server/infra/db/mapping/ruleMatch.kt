package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.server.infra.db.model.RuleMatchPm

internal fun RuleMatch.toPm(): RuleMatchPm = when (this) {
    is RuleMatch.AllOf -> RuleMatchPm.AllOf(matches.map { it.toPm() })
    is RuleMatch.AnyOf -> RuleMatchPm.AnyOf(matches.map { it.toPm() })
    is RuleMatch.ChannelId -> RuleMatchPm.ChannelId(value)
    is RuleMatch.ChannelName -> RuleMatchPm.ChannelName(value, ignoreCase)
    is RuleMatch.TitleRegex -> RuleMatchPm.TitleRegex(pattern)
    is RuleMatch.UrlRegex -> RuleMatchPm.UrlRegex(pattern)
    is RuleMatch.CategoryEquals -> RuleMatchPm.CategoryEquals(category.toDbString())
    is RuleMatch.HasTag -> RuleMatchPm.HasTag(tag.value)
}

internal fun RuleMatchPm.toDomain(): RuleMatch = when (this) {
    is RuleMatchPm.AllOf -> RuleMatch.AllOf(matches.map { it.toDomain() })
    is RuleMatchPm.AnyOf -> RuleMatch.AnyOf(matches.map { it.toDomain() })
    is RuleMatchPm.ChannelId -> RuleMatch.ChannelId(value)
    is RuleMatchPm.ChannelName -> RuleMatch.ChannelName(value, ignoreCase)
    is RuleMatchPm.TitleRegex -> RuleMatch.TitleRegex(pattern)
    is RuleMatchPm.UrlRegex -> RuleMatch.UrlRegex(pattern)
    is RuleMatchPm.CategoryEquals -> RuleMatch.CategoryEquals(category.toCategory())
    is RuleMatchPm.HasTag -> RuleMatch.HasTag(Tag(tag))
}

