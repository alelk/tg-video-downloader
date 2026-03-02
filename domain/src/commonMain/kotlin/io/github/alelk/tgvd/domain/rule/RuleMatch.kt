package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.video.VideoInfo

sealed interface RuleMatch {

    fun matchesVideo(video: VideoInfo): Boolean

    fun matchSpecificity(): Int

    data class AllOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AllOf cannot be empty" }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = matches.all { it.matchesVideo(video) }
        override fun matchSpecificity(): Int = matches.maxOfOrNull { it.matchSpecificity() } ?: 0
    }

    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AnyOf cannot be empty" }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = matches.any { it.matchesVideo(video) }
        override fun matchSpecificity(): Int = matches.minOfOrNull { it.matchSpecificity() } ?: 0
    }

    data class ChannelId(val value: String) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelId value cannot be blank" }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = video.channelId.value == value
        override fun matchSpecificity(): Int = 100
    }

    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelName value cannot be blank" }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = video.channelName.equals(value, ignoreCase = ignoreCase)
        override fun matchSpecificity(): Int = 80
    }

    data class TitleRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }

        init {
            require(pattern.isNotBlank()) { "TitleRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse {
                throw IllegalArgumentException("Invalid regex: $pattern", it)
            }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = regex.containsMatchIn(video.title)
        override fun matchSpecificity(): Int = 40
    }

    data class UrlRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }

        init {
            require(pattern.isNotBlank()) { "UrlRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse {
                throw IllegalArgumentException("Invalid regex: $pattern", it)
            }
        }

        override fun matchesVideo(video: VideoInfo): Boolean = regex.containsMatchIn(video.webpageUrl.value)
        override fun matchSpecificity(): Int = 60
    }
}
