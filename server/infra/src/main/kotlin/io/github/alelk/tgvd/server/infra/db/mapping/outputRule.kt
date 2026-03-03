package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings
import io.github.alelk.tgvd.server.infra.db.model.OutputRulePm
import io.github.alelk.tgvd.server.infra.db.model.VideoEncodeSettingsPm

internal fun OutputRule.toPm(): OutputRulePm =
    OutputRulePm(
        pathTemplate = pathTemplate,
        format = format.serialized,
        maxQuality = maxQuality?.name?.lowercase(),
        encodeSettings = encodeSettings?.toPm(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

internal fun OutputRulePm.toDomain(): OutputRule =
    OutputRule(
        pathTemplate = pathTemplate,
        format = OutputFormat.parse(format),
        maxQuality = maxQuality?.let { q ->
            DownloadPolicy.VideoQuality.entries.find { it.name.equals(q, ignoreCase = true) }
        },
        encodeSettings = encodeSettings?.toDomain(),
        embedThumbnail = embedThumbnail,
        embedMetadata = embedMetadata,
        embedSubtitles = embedSubtitles,
        normalizeAudio = normalizeAudio,
    )

internal fun VideoEncodeSettings.toPm(): VideoEncodeSettingsPm =
    VideoEncodeSettingsPm(
        codec = codec.name.lowercase(),
        hwAccel = hwAccel?.name?.lowercase(),
        preset = preset.name.lowercase(),
        crf = crf,
        audioBitrate = audioBitrate,
        audioCodec = audioCodec,
    )

internal fun VideoEncodeSettingsPm.toDomain(): VideoEncodeSettings =
    VideoEncodeSettings(
        codec = VideoEncodeSettings.VideoCodec.entries
            .find { it.name.equals(codec, ignoreCase = true) } ?: VideoEncodeSettings.VideoCodec.H264,
        hwAccel = hwAccel?.let { h ->
            VideoEncodeSettings.HwAccel.entries.find { it.name.equals(h, ignoreCase = true) }
        },
        preset = VideoEncodeSettings.EncodePreset.entries
            .find { it.name.equals(preset, ignoreCase = true) } ?: VideoEncodeSettings.EncodePreset.MEDIUM,
        crf = crf.coerceIn(0, 51),
        audioBitrate = audioBitrate,
        audioCodec = audioCodec,
    )

