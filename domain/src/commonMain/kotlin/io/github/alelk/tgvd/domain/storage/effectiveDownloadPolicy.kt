package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.rule.Rule

/**
 * The policy to download with: the matched [rule]'s policy with the [channel]'s track overrides on top.
 * Anything left unset by both falls back to the global settings at download time.
 */
fun effectiveDownloadPolicy(rule: Rule?, channel: Channel?): DownloadPolicy =
    (rule?.downloadPolicy ?: DownloadPolicy()).withOverrides(channel?.trackPreferences)
