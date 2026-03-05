package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.preview.category

fun RuleMatch.matches(ctx: MatchContext): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(ctx) }
    is RuleMatch.AnyOf -> matches.any { it.matches(ctx) }
    is RuleMatch.ChannelId -> ctx.video.channelId.value == value
    is RuleMatch.ChannelName -> ctx.video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(ctx.video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(ctx.video.webpageUrl.value)
    is RuleMatch.CategoryEquals -> ctx.overrides != null && ctx.overrides.category == category
    is RuleMatch.HasTag -> ctx.channel != null && tag in ctx.channel.tags
}