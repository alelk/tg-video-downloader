package io.github.alelk.tgvd.domain.video

import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import kotlin.time.Duration

data class VideoInfo(
    val videoId: VideoId,
    val extractor: Extractor,
    val title: String,
    val channelId: ChannelId,
    val channelName: String,
    val uploadDate: LocalDate?,
    val duration: Duration,
    val webpageUrl: Url,
    val thumbnails: List<Thumbnail> = emptyList(),
    val description: String? = null,
    val viewCount: Long? = null,
    val availableFormats: List<Format> = emptyList(),
    val actualFormat: Format? = null,
) {
    data class Thumbnail(val url: Url, val width: Int?, val height: Int?)

    data class Format(
        val formatId: String,
        val extension: String,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Double? = null,
        val tbr: Double? = null,
        val vcodec: String? = null,
        val acodec: String? = null,
        val formatNote: String? = null,
        val filesize: Long? = null,
        val filesizeApprox: Long? = null,
    )
}
