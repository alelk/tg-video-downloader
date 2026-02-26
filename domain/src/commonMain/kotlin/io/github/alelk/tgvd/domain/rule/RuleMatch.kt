package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.video.VideoInfo

sealed interface RuleMatch {

    data class AllOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AllOf cannot be empty" }
        }
    }

    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AnyOf cannot be empty" }
        }
    }

    data class ChannelId(val value: String) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelId value cannot be blank" }
        }
    }

    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelName value cannot be blank" }
        }
    }

    data class TitleRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }

        init {
            require(pattern.isNotBlank()) { "TitleRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse {
                throw IllegalArgumentException("Invalid regex: $pattern", it)
            }
        }
    }

    data class UrlRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }

        init {
            require(pattern.isNotBlank()) { "UrlRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse {
                throw IllegalArgumentException("Invalid regex: $pattern", it)
            }
        }
    }
}

fun RuleMatch.matches(video: VideoInfo): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(video) }
    is RuleMatch.AnyOf -> matches.any { it.matches(video) }
    is RuleMatch.ChannelId -> video.channelId.value == value
    is RuleMatch.ChannelName -> video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(video.webpageUrl.value)
}

fun RuleMatch.specificity(): Int = when (this) {
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.specificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.specificity() } ?: 0
}
