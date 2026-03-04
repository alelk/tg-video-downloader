package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.Category

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

    /** Матчит по категории из user overrides. Если overrides == null — не матчит. */
    data class CategoryEquals(val category: Category) : RuleMatch
}
