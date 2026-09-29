package io.github.alelk.tgvd.api.mapping.video

import arrow.core.Either
import arrow.core.raise.either
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.github.alelk.tgvd.api.mapping.common.parseValue
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import kotlin.time.Duration.Companion.seconds

/** @param field the field name used in validation errors (`source` → `source.url`, `source.videoId`, …). */
fun VideoSourceDto.toDomain(field: String = "source"): Either<DomainError.ValidationError, VideoSource> = either {
    VideoSource(
        url = parseValue("$field.url") { Url(url) }.bind(),
        videoId = parseValue("$field.videoId") { VideoId(videoId) }.bind(),
        extractor = parseValue("$field.extractor") { Extractor(extractor) }.bind(),
    )
}

/** @param field the field name used in validation errors (`videoInfo` → `videoInfo.videoId`, …). */
fun VideoInfoDto.toDomain(field: String = "videoInfo"): Either<DomainError.ValidationError, VideoInfo> = either {
    VideoInfo(
        videoId = parseValue("$field.videoId") { VideoId(videoId) }.bind(),
        extractor = parseValue("$field.extractor") { Extractor(extractor) }.bind(),
        title = title,
        channelId = parseValue("$field.channelId") { ChannelId(channelId) }.bind(),
        channelName = channelName,
        uploadDate = uploadDate?.let { date -> parseValue("$field.uploadDate") { LocalDate(date) }.bind() },
        duration = durationSeconds.seconds,
        webpageUrl = parseValue("$field.webpageUrl") { Url(webpageUrl) }.bind(),
        thumbnails =
        thumbnails.mapIndexed { i, thumbnail ->
            val url = parseValue("$field.thumbnails[$i].url") { Url(thumbnail.url) }.bind()
            VideoInfo.Thumbnail(url, thumbnail.width, thumbnail.height)
        },
        description = description,
        subtitleTracks = subtitleTracks.map { VideoInfo.SubtitleTrack(it.language, it.automatic, it.name) },
        availableFormats = availableFormats.map { it.toDomain() },
    )
}

fun VideoFormatDto.toDomain(): VideoInfo.Format = VideoInfo.Format(
    formatId = formatId,
    extension = extension,
    width = width,
    height = height,
    fps = fps,
    tbr = tbr,
    vcodec = vcodec,
    acodec = acodec,
    formatNote = formatNote,
    filesize = filesize,
    filesizeApprox = filesizeApprox,
    language = language,
    languagePreference = languagePreference,
    audioChannels = audioChannels,
    audioTrackName = audioTrackName,
    isOriginalAudio = isOriginalAudio,
)
