package io.github.alelk.tgvd.features.download.model

import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.domain.common.FileNameValidator

/**
 * Framework-independent snapshot of the editable fields on the preview screen.
 *
 * Keeping transformations here makes the rules testable without Compose and prevents the
 * request-building, validation, and server-refresh paths from interpreting the same fields
 * differently.
 */
data class PreviewEditorValues(
    val category: CategoryDto,
    val metadataType: CategoryDto,
    val title: String,
    val artist: String,
    val album: String,
    val seriesName: String,
    val season: String,
    val episode: String,
    val tags: String,
    val originalPath: String,
    val originalFormat: OutputFormatDto,
    val originalMaxQuality: VideoQualityDto?,
    val additionalOutputs: List<OutputTargetDto>,
) {
    val fieldErrors: Map<String, String>
        get() = buildMap {
            FileNameValidator.validate("title", title)?.let { put("title", it.message) }
            when (metadataType) {
                CategoryDto.MUSIC_VIDEO -> {
                    FileNameValidator.validate("artist", artist)?.let { put("artist", it.message) }
                    validateOptional("album", album)
                }
                CategoryDto.SERIES_EPISODE -> {
                    FileNameValidator.validate("seriesName", seriesName)?.let { put("seriesName", it.message) }
                    validateOptional("season", season)
                    validateOptional("episode", episode)
                }
                CategoryDto.OTHER -> Unit
            }
        }

    fun toOverrides(userEdits: Set<String>): UserOverridesDto? {
        if (userEdits.isEmpty()) return null
        return when (metadataType) {
            CategoryDto.MUSIC_VIDEO -> UserOverridesDto.MusicVideo(
                artist = artist.editedValue("artist", userEdits),
                title = title.editedValue("title", userEdits),
                album = album.editedValue("album", userEdits),
            )
            CategoryDto.SERIES_EPISODE -> UserOverridesDto.SeriesEpisode(
                seriesName = seriesName.editedValue("seriesName", userEdits),
                season = season.editedValue("season", userEdits),
                episode = episode.editedValue("episode", userEdits),
                title = title.editedValue("title", userEdits),
            )
            CategoryDto.OTHER -> UserOverridesDto.Other(
                title = title.editedValue("title", userEdits),
            )
        }
    }

    fun toMetadata(): ResolvedMetadataDto {
        val parsedTags = tags.split(',').map(String::trim).filter(String::isNotBlank)
        return when (metadataType) {
            CategoryDto.MUSIC_VIDEO -> ResolvedMetadataDto.MusicVideo(
                artist = artist,
                title = title,
                album = album.ifBlank { null },
                tags = parsedTags,
            )
            CategoryDto.SERIES_EPISODE -> ResolvedMetadataDto.SeriesEpisode(
                seriesName = seriesName,
                season = season.ifBlank { null },
                episode = episode.ifBlank { null },
                title = title,
                tags = parsedTags,
            )
            CategoryDto.OTHER -> ResolvedMetadataDto.Other(title = title, tags = parsedTags)
        }
    }

    fun toStoragePlan(originalDefaults: OutputTargetDto): StoragePlanDto = StoragePlanDto(
        original = originalDefaults.copy(
            path = originalPath,
            format = originalFormat,
            maxQuality = originalMaxQuality,
        ),
        additional = additionalOutputs,
    )

    /** Applies server-owned values while retaining fields explicitly edited by the user. */
    fun mergeServerResponse(response: PreviewResponseDto, userEdits: Set<String>): PreviewEditorValues {
        val server = from(response)
        return server.copy(
            category = category.takeIf { "category" in userEdits } ?: server.category,
            metadataType = metadataType.takeIf { "category" in userEdits } ?: server.metadataType,
            title = title.takeIf { "title" in userEdits } ?: server.title,
            artist = artist.takeIf { "artist" in userEdits } ?: server.artist,
            album = album.takeIf { "album" in userEdits } ?: server.album,
            seriesName = seriesName.takeIf { "seriesName" in userEdits } ?: server.seriesName,
            season = season.takeIf { "season" in userEdits } ?: server.season,
            episode = episode.takeIf { "episode" in userEdits } ?: server.episode,
            tags = tags.takeIf { "tags" in userEdits } ?: server.tags,
        )
    }

    private fun MutableMap<String, String>.validateOptional(field: String, value: String) {
        if (value.isNotBlank()) FileNameValidator.validate(field, value)?.let { put(field, it.message) }
    }

    companion object {
        fun from(response: PreviewResponseDto): PreviewEditorValues {
            val metadata = response.metadata
            return PreviewEditorValues(
                category = response.category,
                metadataType = metadata.category,
                title = metadata.title,
                artist = (metadata as? ResolvedMetadataDto.MusicVideo)?.artist.orEmpty(),
                album = (metadata as? ResolvedMetadataDto.MusicVideo)?.album.orEmpty(),
                seriesName = (metadata as? ResolvedMetadataDto.SeriesEpisode)?.seriesName.orEmpty(),
                season = (metadata as? ResolvedMetadataDto.SeriesEpisode)?.season.orEmpty(),
                episode = (metadata as? ResolvedMetadataDto.SeriesEpisode)?.episode.orEmpty(),
                tags = metadata.tags.joinToString(", "),
                originalPath = response.storagePlan.original.path,
                originalFormat = response.storagePlan.original.format,
                originalMaxQuality = response.storagePlan.original.maxQuality,
                additionalOutputs = response.storagePlan.additional,
            )
        }
    }
}

private val ResolvedMetadataDto.category: CategoryDto
    get() = when (this) {
        is ResolvedMetadataDto.MusicVideo -> CategoryDto.MUSIC_VIDEO
        is ResolvedMetadataDto.SeriesEpisode -> CategoryDto.SERIES_EPISODE
        is ResolvedMetadataDto.Other -> CategoryDto.OTHER
    }

private fun String.editedValue(field: String, userEdits: Set<String>): String? =
    takeIf { field in userEdits }
