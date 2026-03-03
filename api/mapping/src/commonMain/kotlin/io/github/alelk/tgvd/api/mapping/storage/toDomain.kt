package io.github.alelk.tgvd.api.mapping.storage

import io.github.alelk.tgvd.api.contract.storage.*
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.storage.*

fun OutputFormatDto.toDomain(): OutputFormat = when (this) {
    is OutputFormatDto.OriginalVideo -> OutputFormat.OriginalVideo(container.toDomain())
    is OutputFormatDto.ConvertedVideo -> OutputFormat.ConvertedVideo(container.toDomain())
    is OutputFormatDto.Audio -> OutputFormat.Audio(format.toDomain())
    is OutputFormatDto.Thumbnail -> OutputFormat.Thumbnail(format.toDomain())
}

fun MediaContainerDto.toDomain(): MediaContainer = when (this) {
    MediaContainerDto.MP4 -> MediaContainer.MP4
    MediaContainerDto.MKV -> MediaContainer.MKV
    MediaContainerDto.WEBM -> MediaContainer.WEBM
    MediaContainerDto.AVI -> MediaContainer.AVI
    MediaContainerDto.MOV -> MediaContainer.MOV
}

fun AudioFormatDto.toDomain(): AudioFormat = when (this) {
    AudioFormatDto.M4A -> AudioFormat.M4A
    AudioFormatDto.MP3 -> AudioFormat.MP3
    AudioFormatDto.OPUS -> AudioFormat.OPUS
    AudioFormatDto.FLAC -> AudioFormat.FLAC
    AudioFormatDto.WAV -> AudioFormat.WAV
}

fun ImageFormatDto.toDomain(): ImageFormat = when (this) {
    ImageFormatDto.JPG -> ImageFormat.JPG
    ImageFormatDto.PNG -> ImageFormat.PNG
    ImageFormatDto.WEBP -> ImageFormat.WEBP
}

fun VideoQualityDto.toDomain(): DownloadPolicy.VideoQuality = when (this) {
    VideoQualityDto.BEST -> DownloadPolicy.VideoQuality.BEST
    VideoQualityDto.HD_1080 -> DownloadPolicy.VideoQuality.HD_1080
    VideoQualityDto.HD_720 -> DownloadPolicy.VideoQuality.HD_720
    VideoQualityDto.SD_480 -> DownloadPolicy.VideoQuality.SD_480
}

fun DownloadPolicyDto.toDomain(): DownloadPolicy =
    DownloadPolicy(
        maxQuality = maxQuality.toDomain(),
        preferredContainer = preferredContainer?.toDomain(),
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
        writeThumbnail = writeThumbnail,
    )

fun OutputRuleDto.toDomain(): OutputRule =
    OutputRule(
        pathTemplate = pathTemplate,
        format = format.toDomain(),
        maxQuality = maxQuality?.toDomain(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

fun OutputTargetDto.toDomain(): OutputTarget =
    OutputTarget(
        path = FilePath(path),
        format = format.toDomain(),
        maxQuality = maxQuality?.toDomain(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

fun StoragePlanDto.toDomain(): StoragePlan =
    StoragePlan(original = original.toDomain(), additional = additional.map { it.toDomain() })
