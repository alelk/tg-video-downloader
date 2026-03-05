package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.channel.ChannelRepository
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.preview.UserOverrides
import io.github.alelk.tgvd.domain.video.VideoInfo

class RuleMatchingService(
    private val ruleRepository: RuleRepository,
    private val channelRepository: ChannelRepository,
) {
    suspend fun findMatchingRule(
        video: VideoInfo,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): MatchResult? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        val channel = channelRepository.findByChannelId(workspaceId, video.channelId, video.extractor)
        val ctx = MatchContext(video, overrides, channel)
        val rule = rules
            .filter { it.match.matches(ctx) }
            .maxByOrNull { it.priority * 1000 + it.match.matchSpecificity() }
            ?: return null
        return MatchResult(rule, channel)
    }
}
