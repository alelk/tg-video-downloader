package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.video.VideoInfo

fun RuleMatch.matchesVideo(video: VideoInfo): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matchesVideo(video) }
    is RuleMatch.AnyOf -> matches.any { it.matchesVideo(video) }
    is RuleMatch.ChannelId -> video.channelId.value == value
    is RuleMatch.ChannelName -> video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(video.webpageUrl.value)
}