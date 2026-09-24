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
                // Subtitles are best-effort: a missing language is only an info message, but a track
                // that fails to download (e.g. an auto-translated caption rejected with HTTP 429)
                // aborts the whole video unless errors are ignored. With --ignore-errors yt-dlp
                // reports it as a warning; real download/merge failures still exit non-zero.
                add("--ignore-errors")
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
        val explicitlyDisabled = selectedLanguages != null && selectedLanguages.isEmpty()
        // null = inherit the global default; true/false = force on/off for this rule,
        // overriding the global default in either direction.
        val ruleOverride = policy.downloadSubtitles
        val languages = normalizeLanguages(
            selectedLanguages ?: policy.subtitleLanguages.takeIf { it.isNotEmpty() }
                ?: config.subLangs
                    ?.split(',')
                    ?.takeIf { it.isNotEmpty() }
                ?: config.preferredSubtitleLanguages,
        )

        // An explicit preview-time selection always wins; then a rule's explicit
        // override; otherwise fall back to the global default.
        fun resolve(globalDefault: Boolean): Boolean = when {
            explicitlyDisabled -> false
            !selectedLanguages.isNullOrEmpty() -> true
            ruleOverride != null -> ruleOverride
            else -> globalDefault
        }

        return Selection(
            writeRegular = resolve(config.writeSubs),
            writeAutomatic = resolve(config.writeAutoSubs),
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
