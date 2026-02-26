package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.video.VideoInfo

class RuleMatchingService(
    private val ruleRepository: RuleRepository,
) {
    suspend fun findMatchingRule(video: VideoInfo): Rule? {
        val rules = ruleRepository.findAllEnabled()
        return rules
            .filter { it.match.matches(video) }
            .maxByOrNull { it.priority * 1000 + it.match.specificity() }
    }
}
