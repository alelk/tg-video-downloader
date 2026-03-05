package io.github.alelk.tgvd.domain.rule

import io.github.alelk.tgvd.domain.channel.Channel

/**
 * Result of rule matching — the matched rule + optionally the channel found in the directory.
 * The channel is needed to apply channel-level metadata overrides.
 */
data class MatchResult(
    val rule: Rule,
    val channel: Channel?,
)

