package io.github.alelk.tgvd.api.mapping.video

import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import kotlin.time.Duration.Companion.seconds

fun VideoSourceDto.toDomain(): VideoSource =
    VideoSource(url = Url(url), videoId = VideoId(videoId), extractor = Extractor(extractor))

fun VideoInfoDto.toDomain(): VideoInfo =
    VideoInfo(
        videoId = VideoId(videoId),
        extractor = Extractor(extractor),
        title = title,
        channelId = ChannelId(channelId),
        channelName = channelName,
        uploadDate = uploadDate?.let { LocalDate(it) },
        duration = durationSeconds.seconds,
        webpageUrl = Url(webpageUrl),
        thumbnails = thumbnails.map { VideoInfo.Thumbnail(Url(it.url), it.width, it.height) },
        description = description,
    )

