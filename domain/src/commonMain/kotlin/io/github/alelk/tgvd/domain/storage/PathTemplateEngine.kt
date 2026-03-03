package io.github.alelk.tgvd.domain.storage

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.video.VideoInfo

class PathTemplateEngine {

    fun render(
        template: String,
        video: VideoInfo,
        metadata: ResolvedMetadata,
        format: OutputFormat,
    ): FilePath {
        val vars = buildMap {
            put("videoId", video.videoId.value)
            put("channelId", video.channelId.value)
            put("channelName", sanitize(video.channelName))
            put("ext", format.extension)
            put("title", sanitize(metadata.title))
            put("year", metadata.year?.toString() ?: "unknown")
            put("date", metadata.releaseDate?.value ?: "unknown")

            when (metadata) {
                is ResolvedMetadata.MusicVideo -> {
                    put("artist", sanitize(metadata.artist))
                    metadata.album?.let { put("album", sanitize(it)) }
                }
                is ResolvedMetadata.SeriesEpisode -> {
                    put("seriesName", sanitize(metadata.seriesName))
                    put("season", metadata.season ?: "01")
                    put("episode", metadata.episode ?: "00")
                }
                is ResolvedMetadata.Other -> {}
            }
        }

        var result = template
        for ((key, value) in vars) {
            result = result.replace("{$key}", value)
        }

        return FilePath(result)
    }

    private fun sanitize(value: String): String =
        value.replace(UNSAFE_CHARS, "_").trim()

    fun buildContext(video: VideoInfo, metadata: ResolvedMetadata): Map<String, String> = buildMap {
        put("videoId", video.videoId.value)
        put("channelId", video.channelId.value)
        put("channelName", sanitize(video.channelName))
        put("title", sanitize(metadata.title))
        put("year", metadata.year?.toString() ?: "unknown")
        put("date", metadata.releaseDate?.value ?: "unknown")

        when (metadata) {
            is ResolvedMetadata.MusicVideo -> {
                put("artist", sanitize(metadata.artist))
                metadata.album?.let { put("album", sanitize(it)) }
            }
            is ResolvedMetadata.SeriesEpisode -> {
                put("seriesName", sanitize(metadata.seriesName))
                put("season", metadata.season ?: "01")
                put("episode", metadata.episode ?: "00")
            }
            is ResolvedMetadata.Other -> {}
        }
    }

    /**
     * Build a [StoragePlan] from a list of [OutputRule]s.
     * First output → [StoragePlan.original], rest → [StoragePlan.additional].
     */
    fun buildStoragePlan(
        outputs: List<OutputRule>,
        context: Map<String, String>,
        video: VideoInfo,
    ): StoragePlan {
        require(outputs.isNotEmpty()) { "At least one output rule is required" }

        val targets = outputs.map { output ->
            val outputContext = context + ("ext" to output.format.extension)
            OutputTarget(
                path = renderTemplate(output.pathTemplate, outputContext),
                format = output.format,
                maxQuality = output.maxQuality,
                embedThumbnail = output.embedThumbnail,
                embedMetadata = output.embedMetadata,
                embedSubtitles = output.embedSubtitles,
                normalizeAudio = output.normalizeAudio,
            )
        }

        return StoragePlan(
            original = targets.first(),
            additional = targets.drop(1),
        )
    }

    private fun renderTemplate(template: String, vars: Map<String, String>): FilePath {
        var result = template
        for ((key, value) in vars) {
            result = result.replace("{$key}", value)
        }
        return FilePath(result)
    }

    companion object {
        private val UNSAFE_CHARS = "[/\\\\:*?\"<>]".toRegex()
    }
}
