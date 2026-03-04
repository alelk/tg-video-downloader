package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.infra.db.model.ThumbnailPm
import io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm
import kotlin.time.Duration.Companion.seconds

internal fun VideoInfo.toPm(): VideoInfoPm = VideoInfoPm(
    videoId = videoId.value,
    extractor = extractor.value,
    title = title,
    channelId = channelId.value,
    channelName = channelName,
    uploadDate = uploadDate?.value,
    durationSeconds = duration.inWholeSeconds.toInt(),
    webpageUrl = webpageUrl.value,
    thumbnails = thumbnails.map { ThumbnailPm(it.url.value, it.width, it.height) },
    description = description,
)

internal fun VideoInfoPm.toDomain(): VideoInfo = VideoInfo(
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

/** Create a minimal [VideoInfoPm] for `raw_info` storage from available domain data. */
internal fun VideoSource.toVideoInfoPm(metadata: ResolvedMetadata): VideoInfoPm =
    VideoInfoPm(
        videoId = videoId.value,
        extractor = extractor.value,
        title = metadata.title,
        channelId = "",
        channelName = "",
        uploadDate = metadata.releaseDate?.value,
        durationSeconds = 0,
        webpageUrl = url.value,
    )
