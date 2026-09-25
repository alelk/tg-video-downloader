package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.Tag

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
        val regex: Regex = compileRegex(pattern, "TitleRegex")
    }

    data class UrlRegex(val pattern: String) : RuleMatch {
        val regex: Regex = compileRegex(pattern, "UrlRegex")
    }

    /** Матчит по категории из user overrides. Если overrides == null — не матчит. */
    data class CategoryEquals(val category: Category) : RuleMatch

    /**
     * Матчит если канал видео зарегистрирован в справочнике и имеет указанный тег.
     * Матчинг: channelId + extractor из VideoInfo → поиск в ChannelRepository → проверка tag.
     */
    data class HasTag(val tag: Tag) : RuleMatch
}

private fun compileRegex(pattern: String, matchType: String): Regex {
    require(pattern.isNotBlank()) { "$matchType pattern cannot be blank" }
    return try {
        pattern.toRegex()
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid regex: $pattern", error)
    }
}
