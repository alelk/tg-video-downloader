package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.video.VideoInfo

class RuleMatchingService(
    private val ruleRepository: RuleRepository,
) {
    suspend fun findMatchingRule(video: VideoInfo, workspaceId: WorkspaceId): Rule? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        return rules
            .filter { it.match.matchesVideo(video) }
            .maxByOrNull { it.priority * 1000 + it.match.matchSpecificity() }
    }
}
