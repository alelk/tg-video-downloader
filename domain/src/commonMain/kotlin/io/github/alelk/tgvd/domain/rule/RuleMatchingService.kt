package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.preview.UserOverrides
import io.github.alelk.tgvd.domain.video.VideoInfo

class RuleMatchingService(
    private val ruleRepository: RuleRepository,
) {
    suspend fun findMatchingRule(
        video: VideoInfo,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): Rule? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        val ctx = MatchContext(video, overrides)
        return rules
            .filter { it.match.matches(ctx) }
            .maxByOrNull { it.priority * 1000 + it.match.matchSpecificity() }
    }
}
