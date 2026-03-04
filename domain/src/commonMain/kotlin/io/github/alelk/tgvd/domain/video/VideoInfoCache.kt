package io.github.alelk.tgvd.domain.video

interface VideoInfoCache {
    suspend fun get(url: String): VideoInfo?
    suspend fun put(url: String, videoInfo: VideoInfo)
}

