package io.github.alelk.tgvd.server.infra.db

import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState

/**
 * An empty database brought up by the production start path ([DatabaseFactory.create]) ends up with
 * every file from `db/migration` applied, and Flyway `validate()` agrees with the checksums on disk.
 *
 * The test does not pin the exact count, so a new `V9…` only has to apply cleanly. It pins the
 * floor: V1…V8 exist and are applied (they are never edited — step-01 protocol, rule 7).
 *
 * To see it fail: edit any byte of an existing `V*.sql` after it was applied (checksum mismatch in
 * `validate()`), or add a migration with invalid SQL (the start path throws).
 */
class MigrationsTest :
    FunSpec({
        test("the start path applies every migration to an empty database and validate() passes") {
            val db = PostgresTestContainer.newMigratedDatabase()
            val flyway =
                Flyway
                    .configure()
                    .dataSource(db.config.url, db.config.user, db.config.password)
                    .locations("classpath:db/migration")
                    .load()

            flyway.validate()

            val info = flyway.info()
            info.pending().toList().shouldBeEmpty()
            val all = info.all().toList()
            all.filterNot { it.state == MigrationState.SUCCESS }.map { "${it.version} ${it.state}" }.shouldBeEmpty()
            all.size shouldBeGreaterThanOrEqual KNOWN_MIGRATION_COUNT
            all.map { it.version.version } shouldContainAll (1..KNOWN_MIGRATION_COUNT).map { it.toString() }
            info.applied().size shouldBe all.size
        }

        test("running the start path again on a migrated database is a no-op") {
            val db = PostgresTestContainer.newMigratedDatabase()
            DatabaseFactory(db.config).create()

            val info =
                Flyway
                    .configure()
                    .dataSource(db.config.url, db.config.user, db.config.password)
                    .locations("classpath:db/migration")
                    .load()
                    .info()
            info.pending().toList().shouldBeEmpty()
            info.applied().size shouldBeGreaterThanOrEqual KNOWN_MIGRATION_COUNT
        }
    })

/** V1…V8 at the time the safety net was introduced (stage 01.4). Only grows. */
private const val KNOWN_MIGRATION_COUNT = 8
