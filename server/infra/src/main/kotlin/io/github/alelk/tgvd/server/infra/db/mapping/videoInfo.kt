package io.github.alelk.tgvd.server.infra.db.mapping

import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm

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

