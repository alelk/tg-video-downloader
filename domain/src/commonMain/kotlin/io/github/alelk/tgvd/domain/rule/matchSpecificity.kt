package io.github.alelk.tgvd.domain.rule

fun RuleMatch.matchSpecificity(): Int = when (this) {
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.matchSpecificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.matchSpecificity() } ?: 0
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.CategoryEquals -> 20
}