package io.github.alelk.tgvd.domain.video

import io.github.alelk.tgvd.domain.common.*
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Arb.Companion.videoInfo(
    videoId: Arb<VideoId> = Arb.videoId(),
    extractor: Arb<Extractor> = Arb.extractor(),
    title: Arb<String> = Arb.string(5..50, Codepoint.az()),
    channelId: Arb<ChannelId> = Arb.channelId(),
    channelName: Arb<String> = Arb.string(3..20, Codepoint.az()),
    uploadDate: Arb<LocalDate?> = Arb.domainLocalDate().orNull(0.3),
    duration: Arb<Duration> = Arb.int(10..7200).map { it.seconds },
    webpageUrl: Arb<Url> = Arb.url(),
    thumbnails: Arb<List<VideoInfo.Thumbnail>> = Arb.list(Arb.thumbnail(), 0..3),
    description: Arb<String?> = Arb.string(10..100, Codepoint.az()).orNull(0.4),
    viewCount: Arb<Long?> = Arb.long(0..10_000_000L).orNull(0.4),
): Arb<VideoInfo> = arbitrary {
    VideoInfo(
        videoId = videoId.bind(),
        extractor = extractor.bind(),
        title = title.bind(),
        channelId = channelId.bind(),
        channelName = channelName.bind(),
        uploadDate = uploadDate.bind(),
        duration = duration.bind(),
        webpageUrl = webpageUrl.bind(),
        thumbnails = thumbnails.bind(),
        description = description.bind(),
        viewCount = viewCount.bind(),
    )
}


