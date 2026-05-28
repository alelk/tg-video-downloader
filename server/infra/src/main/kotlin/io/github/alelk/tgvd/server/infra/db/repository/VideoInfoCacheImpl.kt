package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.now
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.VideoInfoCacheTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.LessEqOp
import org.jetbrains.exposed.v1.core.GreaterEqOp
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private val logger = KotlinLogging.logger {}

class VideoInfoCacheImpl(
    private val database: Database,
    /** How long cached entries are valid. Null means entries never expire. */
    private val ttl: Duration = 24.hours,
) : VideoInfoCache {

    override suspend fun get(url: String): VideoInfo? = dbQuery(database) {
        val now = now()
        VideoInfoCacheTable.selectAll()
            .where {
                (VideoInfoCacheTable.url eq url) and
                    (VideoInfoCacheTable.expiresAt.isNull() or
                        VideoInfoCacheTable.expiresAt.isNotNull().and(
                            GreaterEqOp(VideoInfoCacheTable.expiresAt, org.jetbrains.exposed.v1.core.QueryParameter(now, VideoInfoCacheTable.expiresAt.columnType))
                        ))
            }
            .singleOrNull()
            ?.let {
                logger.debug { "VideoInfo cache hit for: $url" }
                it[VideoInfoCacheTable.videoInfo].toDomain()
            }
    }

    override suspend fun put(url: String, videoInfo: VideoInfo): Unit = dbQuery(database) {
        logger.debug { "Caching VideoInfo for: $url" }
        val expiresAt = now() + ttl
        VideoInfoCacheTable.upsert {
            it[VideoInfoCacheTable.url] = url
            it[VideoInfoCacheTable.videoInfo] = videoInfo.toPm()
            it[VideoInfoCacheTable.expiresAt] = expiresAt
        }
    }

    override suspend fun updateActualFormat(url: String, actualFormat: VideoInfo.Format): Unit = dbQuery(database) {
        val entry = VideoInfoCacheTable.selectAll().where { VideoInfoCacheTable.url eq url }.singleOrNull()
        if (entry != null) {
            val videoInfo = entry[VideoInfoCacheTable.videoInfo].toDomain()
            val updated = videoInfo.copy(actualFormat = actualFormat)
            VideoInfoCacheTable.upsert {
                it[VideoInfoCacheTable.url] = url
                it[VideoInfoCacheTable.videoInfo] = updated.toPm()
                it[VideoInfoCacheTable.expiresAt] = entry[VideoInfoCacheTable.expiresAt]
            }
        }
    }

    /** Deletes all expired cache entries. Call periodically (e.g. from a scheduled job). */
    suspend fun evictExpired(): Int = dbQuery(database) {
        val now = now()
        VideoInfoCacheTable.deleteWhere {
            expiresAt.isNotNull() and
                LessEqOp(expiresAt, org.jetbrains.exposed.v1.core.QueryParameter(now, expiresAt.columnType))
        }
    }
}
