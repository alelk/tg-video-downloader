package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.server.infra.db.dbQuery
import io.github.alelk.tgvd.server.infra.db.mapping.toDomain
import io.github.alelk.tgvd.server.infra.db.mapping.toPm
import io.github.alelk.tgvd.server.infra.db.table.VideoInfoCacheTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

private val logger = KotlinLogging.logger {}

class VideoInfoCacheImpl(
    private val database: Database,
) : VideoInfoCache {

    override suspend fun get(url: String): VideoInfo? = dbQuery(database) {
        VideoInfoCacheTable.selectAll()
            .where { VideoInfoCacheTable.url eq url }
            .singleOrNull()
            ?.let {
                logger.debug { "VideoInfo cache hit for: $url" }
                it[VideoInfoCacheTable.videoInfo].toDomain()
            }
    }

    override suspend fun put(url: String, videoInfo: VideoInfo): Unit = dbQuery(database) {
        logger.debug { "Caching VideoInfo for: $url" }
        VideoInfoCacheTable.upsert {
            it[VideoInfoCacheTable.url] = url
            it[VideoInfoCacheTable.videoInfo] = videoInfo.toPm()
        }
    }
}

