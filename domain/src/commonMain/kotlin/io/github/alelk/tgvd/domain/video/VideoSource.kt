package io.github.alelk.tgvd.domain.video

import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId

data class VideoSource(
    val url: Url,
    val videoId: VideoId,
    val extractor: Extractor,
)
