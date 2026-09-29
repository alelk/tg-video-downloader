package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.aFullVideoFormat
import io.github.alelk.tgvd.server.infra.db.fixtures.aFullVideoInfo
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours

/** Round-trips of [VideoInfoCacheImpl] on PostgreSQL, every call under [ExposedTransactionRunner]. */
class VideoInfoCacheImplTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val cache = VideoInfoCacheImpl(db)

        test("put -> get -> updateActualFormat -> get; put again replaces") {
            val url = "https://youtu.be/cache-roundtrip"
            val info = aFullVideoInfo("cache-roundtrip").copy(actualFormat = null)

            tx.inRoTransaction { cache.get(url) }.shouldBeNull()
            tx.inRwTransaction { cache.put(url, info) }
            tx.inRoTransaction { cache.get(url) } shouldBe info

            val actual = aFullVideoFormat("299+140")
            tx.inRwTransaction { cache.updateActualFormat(url, actual) }
            tx.inRoTransaction { cache.get(url) } shouldBe info.copy(actualFormat = actual)

            val replaced = info.copy(title = "Replaced", availableFormats = emptyList())
            tx.inRwTransaction { cache.put(url, replaced) }
            tx.inRoTransaction { cache.get(url) } shouldBe replaced
        }

        test("updateActualFormat of an unknown url is a no-op") {
            tx.inRwTransaction { cache.updateActualFormat("https://youtu.be/missing", aFullVideoFormat()) }
            tx.inRoTransaction { cache.get("https://youtu.be/missing") }.shouldBeNull()
        }

        test("an expired entry is not returned and is evicted") {
            val expiring = VideoInfoCacheImpl(db, ttl = (-1).hours)
            val url = "https://youtu.be/cache-expired"
            tx.inRwTransaction { expiring.put(url, aFullVideoInfo("cache-expired")) }

            tx.inRoTransaction { cache.get(url) }.shouldBeNull()
            tx.inRwTransaction { cache.evictExpired() } shouldBeGreaterThanOrEqual 1
        }
    })
