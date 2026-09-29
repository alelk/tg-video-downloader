package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.VideoInfoCacheTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private val logger = KotlinLogging.logger {}

/**
 * Cache of yt-dlp extraction results by URL. Runs in the transaction of the caller (`TransactionRunner`);
 * never opens one. "Now" (entry creation, expiry) comes from [clock].
 */
class VideoInfoCacheImpl(
    private val clock: Clock,
    /** How long cached entries are valid. */
    private val ttl: Duration = 24.hours,
) : VideoInfoCache {
    override suspend fun get(url: String): VideoInfo? {
        val now = clock.now()
        return VideoInfoCacheTable.selectAll()
            .where {
                (VideoInfoCacheTable.url eq url) and
                    (VideoInfoCacheTable.expiresAt.isNull() or (VideoInfoCacheTable.expiresAt greaterEq now))
            }
            .singleOrNull()
            ?.let {
                logger.debug { "VideoInfo cache hit for: $url" }
                it[VideoInfoCacheTable.videoInfo].toDomain()
            }
    }

    override suspend fun put(url: String, videoInfo: VideoInfo) {
        logger.debug { "Caching VideoInfo for: $url, formats count=${videoInfo.availableFormats.size}" }

        val videoOnlyCount = videoInfo.availableFormats.count {
            (it.vcodec != null && it.vcodec != "none") && (it.acodec == null || it.acodec == "none")
        }
        val audioOnlyCount = videoInfo.availableFormats.count {
            (it.acodec != null && it.acodec != "none") && (it.vcodec == null || it.vcodec == "none")
        }
        val combinedCount = videoInfo.availableFormats.count {
            (it.vcodec != null && it.vcodec != "none") && (it.acodec != null && it.acodec != "none")
        }
        logger.debug { "put: videoOnly=$videoOnlyCount, audioOnly=$audioOnlyCount, combined=$combinedCount" }

        val now = clock.now()
        // A refreshed entry keeps its original created_at.
        VideoInfoCacheTable.upsert(onUpdateExclude = listOf(VideoInfoCacheTable.createdAt)) {
            it[VideoInfoCacheTable.url] = url
            it[VideoInfoCacheTable.videoInfo] = videoInfo.toPm()
            it[VideoInfoCacheTable.createdAt] = now
            it[VideoInfoCacheTable.expiresAt] = now + ttl
        }
    }

    override suspend fun updateActualFormat(url: String, actualFormat: VideoInfo.Format) {
        val entry = VideoInfoCacheTable.selectAll().where { VideoInfoCacheTable.url eq url }.singleOrNull() ?: return
        val updated = entry[VideoInfoCacheTable.videoInfo].toDomain().copy(actualFormat = actualFormat)
        VideoInfoCacheTable.upsert {
            it[VideoInfoCacheTable.url] = url
            it[VideoInfoCacheTable.videoInfo] = updated.toPm()
            it[VideoInfoCacheTable.expiresAt] = entry[VideoInfoCacheTable.expiresAt]
        }
    }

    /**
     * Deletes all expired cache entries. Not called anywhere yet (a known issue, see
     * `docs/project-status.md`); the caller must open the transaction.
     */
    suspend fun evictExpired(): Int {
        val now = clock.now()
        return VideoInfoCacheTable.deleteWhere { expiresAt.isNotNull() and (expiresAt lessEq now) }
    }
}
