package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobOutput
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.T1
import io.github.alelk.tgvd.server.infra.db.fixtures.T2
import io.github.alelk.tgvd.server.infra.db.fixtures.aJob
import io.github.alelk.tgvd.server.infra.db.fixtures.aRule
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock

/** Round-trips of [JobRepositoryImpl] and [JobOutputRepositoryImpl] on PostgreSQL, under [ExposedTransactionRunner]. */
class JobRepositoryImplTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val workspaces = WorkspaceRepositoryImpl()
        val rules = RuleRepositoryImpl(Clock.System)
        val repository = JobRepositoryImpl(Clock.System)
        val outputs = JobOutputRepositoryImpl()

        // Insert stores the job's own created_at/updated_at (T0 in the mothers).
        fun Job.withInsertTimestamps() = copy(createdAt = T0, updatedAt = T0)

        test("save -> find -> update -> find") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val rule = aRule(workspace.id)
            tx.inRwTransaction { rules.save(rule) }.shouldBeRight()
            val job = aJob(workspace.id, videoId = "job-roundtrip", ruleId = rule.id)

            tx.inRwTransaction { repository.save(job) }.shouldBeRight() shouldBe job
            val saved = tx.inRoTransaction { repository.findById(job.id) }.shouldNotBeNull()
            saved.withInsertTimestamps() shouldBe job

            val updated =
                job.copy(
                    status = JobStatus.DOWNLOADING,
                    phase = JobPhase.EMBED_THUMBNAIL,
                    progress = 42,
                    errorMessage = "transient",
                    attempt = 2,
                    metadata = ResolvedMetadata.Other(title = "Renamed"),
                    metadataSource = MetadataSource.FALLBACK,
                    storagePlan =
                    StoragePlan(
                        OutputTarget(
                            FilePath("/media/other/Renamed.webm"),
                            OutputFormat.OriginalVideo(MediaContainer.WEBM),
                        ),
                    ),
                    mediaSelection = null,
                    ruleId = null,
                    updatedAt = T1,
                    startedAt = T1,
                    finishedAt = T2,
                )
            tx.inRwTransaction { repository.save(updated) }.shouldBeRight()

            val reloaded = tx.inRoTransaction { repository.findById(job.id) }.shouldNotBeNull()
            reloaded shouldBe updated.copy(createdAt = saved.createdAt)
        }

        test("queries: by workspace, by video id, active") {
            val workspace = aWorkspace()
            val other = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            tx.inRwTransaction { workspaces.save(other) }.shouldBeRight()
            val pending = aJob(workspace.id, videoId = "job-q-1")
            val done = aJob(workspace.id, videoId = "job-q-2").copy(status = JobStatus.COMPLETED)
            val foreign = aJob(other.id, videoId = "job-q-3")
            listOf(pending, done, foreign).forEach { tx.inRwTransaction { repository.save(it) }.shouldBeRight() }

            tx.inRoTransaction { repository.findByWorkspace(workspace.id) }.map { it.id } shouldContainExactlyInAnyOrder
                listOf(pending.id, done.id)
            tx.inRoTransaction { repository.findByVideoId("job-q-2", workspace.id) }.map { it.id } shouldBe
                listOf(done.id)
            tx.inRoTransaction { repository.findByVideoId("job-q-3", workspace.id) } shouldBe emptyList()
            val active = tx.inRoTransaction { repository.findActive() }.map { it.id }
            active shouldContain pending.id
            active shouldContain foreign.id
            active shouldNotContain done.id
        }

        test("job outputs: saveAll -> findByJob") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val job = aJob(workspace.id, videoId = "job-outputs")
            tx.inRwTransaction { repository.save(job) }.shouldBeRight()
            val written =
                listOf(
                    JobOutput(job.id, "original/mkv", FilePath("/media/a.mkv"), sizeBytes = 1_000L, createdAt = T0),
                    JobOutput(job.id, "audio/mp3", FilePath("/media/a.mp3"), sizeBytes = null, createdAt = T0),
                )

            tx.inRwTransaction { outputs.saveAll(written) }
            tx.inRwTransaction { outputs.saveAll(emptyList()) }

            tx.inRoTransaction {
                outputs.findByJob(job.id)
            }.map { it.copy(createdAt = T0) } shouldContainExactlyInAnyOrder
                written
        }
    })
