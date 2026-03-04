package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.Category
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

private fun Arb.Companion.ruleMatchLeaf(): Arb<RuleMatch> = arbitrary {
    when (Arb.int(0..4).bind()) {
        0 -> RuleMatch.ChannelId(Arb.string(3..20, Codepoint.az()).bind())
        1 -> RuleMatch.ChannelName(Arb.string(3..20, Codepoint.az()).bind(), Arb.boolean().bind())
        2 -> RuleMatch.TitleRegex(Arb.element(".*test.*", "^Episode\\s+\\d+", ".*music.*", "Part \\d+").bind())
        3 -> RuleMatch.UrlRegex(Arb.element(".*youtube\\.com.*", ".*rutu\\.be.*", ".*/video/.*").bind())
        else -> RuleMatch.CategoryEquals(Arb.element(Category.MUSIC_VIDEO, Category.SERIES, Category.OTHER).bind())
    }
}

fun Arb.Companion.ruleMatch(maxDepth: Int = 2): Arb<RuleMatch> = arbitrary {
    if (maxDepth <= 0) {
        Arb.ruleMatchLeaf().bind()
    } else {
        when (Arb.int(0..5).bind()) {
            0 -> RuleMatch.AllOf(Arb.list(Arb.ruleMatch(maxDepth - 1), 2..3).bind())
            1 -> RuleMatch.AnyOf(Arb.list(Arb.ruleMatch(maxDepth - 1), 2..3).bind())
            else -> Arb.ruleMatchLeaf().bind()
        }
    }
}

