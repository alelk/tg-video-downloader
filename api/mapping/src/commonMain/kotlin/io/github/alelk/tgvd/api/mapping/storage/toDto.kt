package io.github.alelk.tgvd.api.mapping.storage

import io.github.alelk.tgvd.api.contract.storage.*
import io.github.alelk.tgvd.domain.storage.*

fun OutputFormat.toDto(): OutputFormatDto = when (this) {
    is OutputFormat.OriginalVideo -> OutputFormatDto.OriginalVideo(container.toDto())
    is OutputFormat.ConvertedVideo -> OutputFormatDto.ConvertedVideo(container.toDto())
    is OutputFormat.Audio -> OutputFormatDto.Audio(format.toDto())
    is OutputFormat.Thumbnail -> OutputFormatDto.Thumbnail(format.toDto())
}

fun MediaContainer.toDto(): MediaContainerDto = when (this) {
    MediaContainer.MP4 -> MediaContainerDto.MP4
    MediaContainer.MKV -> MediaContainerDto.MKV
    MediaContainer.WEBM -> MediaContainerDto.WEBM
    MediaContainer.AVI -> MediaContainerDto.AVI
    MediaContainer.MOV -> MediaContainerDto.MOV
}

fun AudioFormat.toDto(): AudioFormatDto = when (this) {
    AudioFormat.M4A -> AudioFormatDto.M4A
    AudioFormat.MP3 -> AudioFormatDto.MP3
    AudioFormat.OPUS -> AudioFormatDto.OPUS
    AudioFormat.FLAC -> AudioFormatDto.FLAC
    AudioFormat.WAV -> AudioFormatDto.WAV
}

fun ImageFormat.toDto(): ImageFormatDto = when (this) {
    ImageFormat.JPG -> ImageFormatDto.JPG
    ImageFormat.PNG -> ImageFormatDto.PNG
    ImageFormat.WEBP -> ImageFormatDto.WEBP
}

fun DownloadPolicy.VideoQuality.toDto(): VideoQualityDto = when (this) {
    DownloadPolicy.VideoQuality.BEST -> VideoQualityDto.BEST
    DownloadPolicy.VideoQuality.HD_1080 -> VideoQualityDto.HD_1080
    DownloadPolicy.VideoQuality.HD_720 -> VideoQualityDto.HD_720
    DownloadPolicy.VideoQuality.SD_480 -> VideoQualityDto.SD_480
}

fun DownloadPolicy.toDto(): DownloadPolicyDto =
    DownloadPolicyDto(
        maxQuality = maxQuality.toDto(),
        preferredContainer = preferredContainer?.toDto(),
        downloadSubtitles = downloadSubtitles,
        subtitleLanguages = subtitleLanguages,
    )

fun OutputTemplate.toDto(): OutputTemplateDto =
    OutputTemplateDto(pathTemplate = pathTemplate, format = format.toDto())

fun StoragePolicy.toDto(): StoragePolicyDto =
    StoragePolicyDto(
        originalTemplate = originalTemplate,
        additionalOutputs = additionalOutputs.map { it.toDto() },
    )

fun PostProcessPolicy.toDto(): PostProcessPolicyDto =
    PostProcessPolicyDto(embedThumbnail, embedMetadata, normalizeAudio)

fun OutputTarget.toDto(): OutputTargetDto =
    OutputTargetDto(path = path.value, format = format.toDto())

fun StoragePlan.toDto(): StoragePlanDto =
    StoragePlanDto(original = original.toDto(), additional = additional.map { it.toDto() })
