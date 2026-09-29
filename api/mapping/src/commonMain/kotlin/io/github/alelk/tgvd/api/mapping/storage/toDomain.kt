package io.github.alelk.tgvd.api.mapping.storage

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.storage.AudioFormatDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.EncodePresetDto
import io.github.alelk.tgvd.api.contract.storage.HwAccelDto
import io.github.alelk.tgvd.api.contract.storage.ImageFormatDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto
import io.github.alelk.tgvd.api.contract.storage.VideoCodecDto
import io.github.alelk.tgvd.api.contract.storage.VideoEncodeSettingsDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.mapping.common.parseValue
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.storage.AudioFormat
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.ImageFormat
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings

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

fun DownloadPolicyDto.toDomain(): DownloadPolicy = DownloadPolicy(
    maxQuality = maxQuality.toDomain(),
    downloadSubtitles = downloadSubtitles,
    subtitleLanguages = subtitleLanguages,
    writeThumbnail = writeThumbnail,
    audioLanguages = audioLanguages,
)

fun TrackPreferencesDto.toDomain(): TrackPreferences = TrackPreferences(
    audioLanguages = audioLanguages?.normalizedLanguages(),
    downloadSubtitles = downloadSubtitles,
    subtitleLanguages = subtitleLanguages?.normalizedLanguages()?.takeIf { it.isNotEmpty() },
)

private fun List<String>.normalizedLanguages(): List<String> = map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun OutputRuleDto.toDomain(): OutputRule = OutputRule(
    pathTemplate = pathTemplate,
    format = format.toDomain(),
    maxQuality = maxQuality?.toDomain(),
    encodeSettings = encodeSettings?.toDomain(),
    embedThumbnail = embedThumbnail,
    embedMetadata = embedMetadata,
    embedSubtitles = embedSubtitles,
    normalizeAudio = normalizeAudio,
)

/** @param field the field name used in validation errors (`storagePlan.original` → `storagePlan.original.path`). */
fun OutputTargetDto.toDomain(field: String): Either<DomainError.ValidationError, OutputTarget> = either {
    OutputTarget(
        path = parseValue("$field.path") { FilePath(path) }.bind(),
        format = format.toDomain(),
        maxQuality = maxQuality?.toDomain(),
        encodeSettings = encodeSettings?.toDomain(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )
}

fun StoragePlanDto.toDomain(): Either<DomainError.ValidationError, StoragePlan> = either {
    StoragePlan(
        original = original.toDomain("storagePlan.original").bind(),
        additional = additional.mapIndexed { i, target -> target.toDomain("storagePlan.additional[$i]").bind() },
    )
}

fun VideoEncodeSettingsDto.toDomain(): VideoEncodeSettings = VideoEncodeSettings(
    codec = codec.toDomain(),
    hwAccel = hwAccel?.toDomain(),
    preset = preset.toDomain(),
    crf = crf.coerceIn(0, 51),
    audioBitrate = audioBitrate,
    audioCodec = audioCodec,
)

fun VideoCodecDto.toDomain(): VideoEncodeSettings.VideoCodec = when (this) {
    VideoCodecDto.H264 -> VideoEncodeSettings.VideoCodec.H264
    VideoCodecDto.H265 -> VideoEncodeSettings.VideoCodec.H265
    VideoCodecDto.VP9 -> VideoEncodeSettings.VideoCodec.VP9
    VideoCodecDto.AV1 -> VideoEncodeSettings.VideoCodec.AV1
}

fun HwAccelDto.toDomain(): VideoEncodeSettings.HwAccel = when (this) {
    HwAccelDto.VIDEOTOOLBOX -> VideoEncodeSettings.HwAccel.VIDEOTOOLBOX
    HwAccelDto.NVENC -> VideoEncodeSettings.HwAccel.NVENC
    HwAccelDto.QSV -> VideoEncodeSettings.HwAccel.QSV
    HwAccelDto.VAAPI -> VideoEncodeSettings.HwAccel.VAAPI
    HwAccelDto.AMF -> VideoEncodeSettings.HwAccel.AMF
}

fun EncodePresetDto.toDomain(): VideoEncodeSettings.EncodePreset = when (this) {
    EncodePresetDto.ULTRAFAST -> VideoEncodeSettings.EncodePreset.ULTRAFAST
    EncodePresetDto.SUPERFAST -> VideoEncodeSettings.EncodePreset.SUPERFAST
    EncodePresetDto.VERYFAST -> VideoEncodeSettings.EncodePreset.VERYFAST
    EncodePresetDto.FASTER -> VideoEncodeSettings.EncodePreset.FASTER
    EncodePresetDto.FAST -> VideoEncodeSettings.EncodePreset.FAST
    EncodePresetDto.MEDIUM -> VideoEncodeSettings.EncodePreset.MEDIUM
    EncodePresetDto.SLOW -> VideoEncodeSettings.EncodePreset.SLOW
    EncodePresetDto.SLOWER -> VideoEncodeSettings.EncodePreset.SLOWER
    EncodePresetDto.VERYSLOW -> VideoEncodeSettings.EncodePreset.VERYSLOW
}
