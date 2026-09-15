package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig

/** Resolves the effective subtitle policy for one download. */
internal object SubtitleSelector {
    data class Selection(
        val writeRegular: Boolean,
        val writeAutomatic: Boolean,
        val languages: List<String>,
        val embed: Boolean,
    ) {
        val enabled: Boolean get() = writeRegular || writeAutomatic

        fun arguments(): List<String> {
            if (!enabled || languages.isEmpty()) return emptyList()
            return buildList {
                if (writeRegular) add("--write-subs")
                if (writeAutomatic) add("--write-auto-subs")
                add("--sub-langs")
                add(languages.joinToString(","))
                if (embed) add("--embed-subs")
            }
        }
    }

    fun select(config: YtDlpConfig, policy: DownloadPolicy): Selection {
        val requestedByRule = policy.downloadSubtitles
        val languages = normalizeLanguages(
            policy.subtitleLanguages.takeIf { it.isNotEmpty() }
                ?: config.subLangs
                    ?.split(',')
                    ?.takeIf { it.isNotEmpty() }
                ?: config.preferredSubtitleLanguages,
        )

        return Selection(
            writeRegular = config.writeSubs || requestedByRule,
            // A rule asking for subtitles includes generated captions too: for many
            // videos they are the only subtitles available.
            writeAutomatic = config.writeAutoSubs || requestedByRule,
            languages = languages,
            embed = config.embedSubs,
        )
    }

    internal fun normalizeLanguages(languages: List<String>): List<String> = languages
        .map { it.trim().replace('_', '-').lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
}
