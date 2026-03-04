package io.github.alelk.tgvd.server.infra.db.table

import io.github.alelk.tgvd.server.infra.db.jsonb
import io.github.alelk.tgvd.server.infra.db.model.VideoInfoPm
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object VideoInfoCacheTable : Table("video_info_cache") {
    val url = text("url")
    val videoInfo = jsonb<VideoInfoPm>("video_info", jsonb)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(url)
}

