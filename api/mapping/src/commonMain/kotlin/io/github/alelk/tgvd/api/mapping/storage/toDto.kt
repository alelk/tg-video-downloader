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
        writeThumbnail = writeThumbnail,
    )

fun OutputRule.toDto(): OutputRuleDto =
    OutputRuleDto(
        pathTemplate = pathTemplate,
        format = format.toDto(),
        maxQuality = maxQuality?.toDto(),
        encodeSettings = encodeSettings?.toDto(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

fun OutputTarget.toDto(): OutputTargetDto =
    OutputTargetDto(
        path = path.value,
        format = format.toDto(),
        maxQuality = maxQuality?.toDto(),
        encodeSettings = encodeSettings?.toDto(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

fun StoragePlan.toDto(): StoragePlanDto =
    StoragePlanDto(original = original.toDto(), additional = additional.map { it.toDto() })

fun VideoEncodeSettings.toDto(): VideoEncodeSettingsDto =
    VideoEncodeSettingsDto(
        codec = codec.toDto(),
        hwAccel = hwAccel?.toDto(),
        preset = preset.toDto(),
        crf = crf,
        audioBitrate = audioBitrate,
        audioCodec = audioCodec,
    )

fun VideoEncodeSettings.VideoCodec.toDto(): VideoCodecDto = when (this) {
    VideoEncodeSettings.VideoCodec.H264 -> VideoCodecDto.H264
    VideoEncodeSettings.VideoCodec.H265 -> VideoCodecDto.H265
    VideoEncodeSettings.VideoCodec.VP9 -> VideoCodecDto.VP9
    VideoEncodeSettings.VideoCodec.AV1 -> VideoCodecDto.AV1
}

fun VideoEncodeSettings.HwAccel.toDto(): HwAccelDto = when (this) {
    VideoEncodeSettings.HwAccel.VIDEOTOOLBOX -> HwAccelDto.VIDEOTOOLBOX
    VideoEncodeSettings.HwAccel.NVENC -> HwAccelDto.NVENC
    VideoEncodeSettings.HwAccel.QSV -> HwAccelDto.QSV
    VideoEncodeSettings.HwAccel.VAAPI -> HwAccelDto.VAAPI
    VideoEncodeSettings.HwAccel.AMF -> HwAccelDto.AMF
}

fun VideoEncodeSettings.EncodePreset.toDto(): EncodePresetDto = when (this) {
    VideoEncodeSettings.EncodePreset.ULTRAFAST -> EncodePresetDto.ULTRAFAST
    VideoEncodeSettings.EncodePreset.SUPERFAST -> EncodePresetDto.SUPERFAST
    VideoEncodeSettings.EncodePreset.VERYFAST -> EncodePresetDto.VERYFAST
    VideoEncodeSettings.EncodePreset.FASTER -> EncodePresetDto.FASTER
    VideoEncodeSettings.EncodePreset.FAST -> EncodePresetDto.FAST
    VideoEncodeSettings.EncodePreset.MEDIUM -> EncodePresetDto.MEDIUM
    VideoEncodeSettings.EncodePreset.SLOW -> EncodePresetDto.SLOW
    VideoEncodeSettings.EncodePreset.SLOWER -> EncodePresetDto.SLOWER
    VideoEncodeSettings.EncodePreset.VERYSLOW -> EncodePresetDto.VERYSLOW
}

