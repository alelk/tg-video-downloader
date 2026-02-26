package io.github.alelk.tgvd.api.mapping.video

import io.github.alelk.tgvd.api.contract.video.ThumbnailDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource

fun VideoSource.toDto(): VideoSourceDto =
    VideoSourceDto(url = url.value, videoId = videoId.value, extractor = extractor.value)

fun VideoInfo.toDto(): VideoInfoDto =
    VideoInfoDto(
        videoId = videoId.value,
        extractor = extractor.value,
        title = title,
        channelId = channelId.value,
        channelName = channelName,
        uploadDate = uploadDate?.value,
        durationSeconds = duration.inWholeSeconds.toInt(),
        webpageUrl = webpageUrl.value,
        thumbnails = thumbnails.map { ThumbnailDto(it.url.value, it.width, it.height) },
        description = description,
    )

