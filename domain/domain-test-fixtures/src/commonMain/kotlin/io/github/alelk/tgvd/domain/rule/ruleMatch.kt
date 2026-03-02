package io.github.alelk.tgvd.domain.rule

import io.kotest.property.Arb
import io.kotest.property.arbitrary.*

private fun Arb.Companion.ruleMatchLeaf(): Arb<RuleMatch> = arbitrary {
    when (Arb.int(0..3).bind()) {
        0 -> RuleMatch.ChannelId(Arb.string(3..20, Codepoint.az()).bind())
        1 -> RuleMatch.ChannelName(Arb.string(3..20, Codepoint.az()).bind(), Arb.boolean().bind())
        2 -> RuleMatch.TitleRegex(Arb.element(".*test.*", "^Episode\\s+\\d+", ".*music.*", "Part \\d+").bind())
        else -> RuleMatch.UrlRegex(Arb.element(".*youtube\\.com.*", ".*rutu\\.be.*", ".*/video/.*").bind())
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

