package io.github.alelk.tgvd.server.infra.process

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig

/** Resolves the effective subtitle policy for one download. */
object SubtitleSelector {
    data class Selection(
        val writeRegular: Boolean,
        val writeAutomatic: Boolean,
        val languages: List<String>,
        val embed: Boolean,
        val sleepSubtitles: Int? = null,
    ) {
        val enabled: Boolean get() = languages.isNotEmpty() && (writeRegular || writeAutomatic)

        fun arguments(): List<String> {
            return buildList {
                val regular = writeRegular && languages.isNotEmpty()
                val automatic = writeAutomatic && languages.isNotEmpty()
                add(if (regular) "--write-subs" else "--no-write-subs")
                add(if (automatic) "--write-auto-subs" else "--no-write-auto-subs")
                add(if (embed && (regular || automatic)) "--embed-subs" else "--no-embed-subs")
                if (!regular && !automatic) return@buildList
                add("--sub-langs")
                add(languages.joinToString(","))
                // Regular + automatic captions for the same language are fetched as two
                // separate requests to YouTube's caption endpoint; space them out to avoid
                // tripping its rate limiter (HTTP 429).
                if (regular && automatic) {
                    sleepSubtitles?.let { add("--sleep-subtitles"); add(it.toString()) }
                }
            }
        }
    }

    fun select(config: YtDlpConfig, policy: DownloadPolicy, selectedLanguages: List<String>? = null): Selection {
        val requestedByRule = policy.downloadSubtitles
        val explicitlyDisabled = selectedLanguages != null && selectedLanguages.isEmpty()
        val languages = normalizeLanguages(
            selectedLanguages ?: policy.subtitleLanguages.takeIf { it.isNotEmpty() }
                ?: config.subLangs
                    ?.split(',')
                    ?.takeIf { it.isNotEmpty() }
                ?: config.preferredSubtitleLanguages,
        )

        return Selection(
            writeRegular = !explicitlyDisabled && (config.writeSubs || requestedByRule || !selectedLanguages.isNullOrEmpty()),
            // A rule asking for subtitles includes generated captions too: for many
            // videos they are the only subtitles available.
            writeAutomatic = !explicitlyDisabled && (config.writeAutoSubs || requestedByRule || !selectedLanguages.isNullOrEmpty()),
            languages = languages,
            embed = config.embedSubs,
            sleepSubtitles = config.sleepSubtitles,
        )
    }

    internal fun normalizeLanguages(languages: List<String>): List<String> = languages
        .map { it.trim().replace('_', '-').lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
}
