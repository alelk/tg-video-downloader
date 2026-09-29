package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.ImageFormat
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.aRule
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Round-trips of [RuleRepositoryImpl] on PostgreSQL, every call under [ExposedTransactionRunner]. */
class RuleRepositoryImplTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val workspaces = WorkspaceRepositoryImpl(db)
        val repository = RuleRepositoryImpl(db)

        // created_at / updated_at are written by the database (insert) or by the repository (update).
        fun Rule.withoutTimestamps() = copy(createdAt = T0, updatedAt = T0)

        test("save -> find -> update -> find") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val rule = aRule(workspace.id)

            tx.inRwTransaction { repository.save(rule) }.shouldBeRight() shouldBe rule
            val saved = tx.inRoTransaction { repository.findById(rule.id) }.shouldNotBeNull()
            saved.withoutTimestamps() shouldBe rule

            val updated =
                rule.copy(
                    name = "Series",
                    enabled = false,
                    priority = -5,
                    match = RuleMatch.CategoryEquals(Category.SERIES),
                    metadataTemplate = MetadataTemplate.SeriesEpisode(
                        seriesNameOverride = "Show",
                        defaultTags = listOf("tv"),
                    ),
                    downloadPolicy = DownloadPolicy(),
                    outputs =
                    listOf(
                        OutputRule(
                            pathTemplate = "/media/tv/{seriesName}/{title}.{ext}",
                            format = OutputFormat.ConvertedVideo(MediaContainer.WEBM),
                            maxQuality = DownloadPolicy.VideoQuality.SD_480,
                        ),
                        OutputRule(
                            pathTemplate = "/media/tv/{title}.{ext}",
                            format = OutputFormat.Thumbnail(ImageFormat.PNG),
                        ),
                    ),
                )
            tx.inRwTransaction { repository.save(updated) }.shouldBeRight()

            val reloaded = tx.inRoTransaction { repository.findById(rule.id) }.shouldNotBeNull()
            reloaded.withoutTimestamps() shouldBe updated
            reloaded.createdAt shouldBe saved.createdAt
            reloaded.updatedAt shouldNotBe saved.updatedAt
        }

        test("queries by workspace and enabled flag, ordered by priority; delete") {
            val workspace = aWorkspace()
            val other = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            tx.inRwTransaction { workspaces.save(other) }.shouldBeRight()
            val low = aRule(workspace.id, "low").copy(priority = 1)
            val high = aRule(workspace.id, "high").copy(priority = 100)
            val disabled = aRule(workspace.id, "disabled").copy(enabled = false, priority = 50)
            val foreign = aRule(other.id, "foreign")
            listOf(low, high, disabled, foreign).forEach { tx.inRwTransaction { repository.save(it) }.shouldBeRight() }

            tx.inRoTransaction { repository.findByWorkspace(workspace.id) }.map { it.name } shouldBe
                listOf("high", "disabled", "low")
            tx.inRoTransaction { repository.findEnabledByWorkspace(workspace.id) }.map { it.name } shouldBe
                listOf("high", "low")
            tx.inRoTransaction {
                repository.findAllEnabled()
            }.map { it.id }.containsAll(listOf(low.id, high.id, foreign.id)).shouldBeTrue()

            tx.inRwTransaction { repository.delete(low.id) }.shouldBeTrue()
            tx.inRwTransaction { repository.delete(low.id) }.shouldBeFalse()
            tx.inRoTransaction { repository.findById(low.id) }.shouldBeNull()
        }
    })
