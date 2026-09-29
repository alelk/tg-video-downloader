package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.JobStatusPatch
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.aFullVideoInfo
import io.github.alelk.tgvd.server.infra.db.fixtures.aJob
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeLeft
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.db.table.JobsTable
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A clock the test moves by hand, so every stamp of a status write is known. */
private class ManualClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

/**
 * Status writes of [JobRepositoryImpl] on PostgreSQL: the compare-and-set [JobRepositoryImpl.transition],
 * the atomic claim [JobRepositoryImpl.claimNext] (also under concurrency) and the start-up recovery
 * [JobRepositoryImpl.requeueInterrupted]. Own database: the claim sees every pending job of it.
 */
@OptIn(ExperimentalUuidApi::class)
class JobStatusWritesTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val clock = ManualClock(T0)
        val workspaces = WorkspaceRepositoryImpl()
        val repository = JobRepositoryImpl(clock)
        val workspace = aWorkspace()
        var videoCounter = 0

        beforeSpec { tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight() }

        /** Stores a new job in [status]; no other job of the database stays pending (the claim is global). */
        suspend fun stored(status: JobStatus, createdAt: Instant = T0): Job {
            tx.inRwTransaction {
                JobsTable.update({ JobsTable.status eq "pending" }) { it[JobsTable.status] = "cancelled" }
            }
            val job = aJob(workspace.id, videoId = "status-${++videoCounter}").copy(
                status = status,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
            return tx.inRwTransaction { repository.save(job) }.shouldBeRight()
        }

        suspend fun reload(id: JobId): Job = tx.inRoTransaction { repository.findById(id) }.shouldNotBeNull()

        context("transition") {
            test("claim -> progress -> failed -> retry: every write stamps and patches as documented") {
                val job = stored(JobStatus.PENDING)
                val claimedAt = T0 + 1.minutes
                clock.now = claimedAt
                val claimed = tx.inRwTransaction { repository.claimNext() }.shouldNotBeNull()
                claimed.id shouldBe job.id
                claimed.status shouldBe JobStatus.DOWNLOADING
                (claimed.phase to claimed.progress) shouldBe (JobPhase.DOWNLOAD to 0)
                claimed.startedAt shouldBe claimedAt
                claimed.updatedAt shouldBe claimedAt

                clock.now = T0 + 2.minutes
                val progressed =
                    tx.inRwTransaction {
                        repository.transition(
                            job.id,
                            setOf(JobStatus.DOWNLOADING),
                            JobStatus.DOWNLOADING,
                            JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = 40),
                        )
                    }.shouldBeRight()
                (progressed.phase to progressed.progress) shouldBe (JobPhase.DOWNLOAD to 40)
                progressed.startedAt shouldBe claimedAt // progress does not move the start
                progressed.updatedAt shouldBe T0 + 2.minutes

                clock.now = T0 + 3.minutes
                val failed =
                    tx.inRwTransaction {
                        repository.transition(
                            job.id,
                            JobStatus.processingSourcesOf(JobStatus.FAILED),
                            JobStatus.FAILED,
                            JobStatusPatch(errorMessage = "boom"),
                        )
                    }.shouldBeRight()
                failed.status shouldBe JobStatus.FAILED
                failed.phase.shouldBeNull()
                failed.progress.shouldBeNull()
                failed.errorMessage shouldBe "boom"
                failed.finishedAt shouldBe T0 + 3.minutes

                clock.now = T0 + 4.minutes
                val retried =
                    tx.inRwTransaction {
                        repository.transition(
                            job.id,
                            setOf(JobStatus.FAILED, JobStatus.CANCELLED),
                            JobStatus.PENDING,
                            JobStatusPatch(newAttempt = true),
                        )
                    }.shouldBeRight()
                retried.status shouldBe JobStatus.PENDING
                retried.attempt shouldBe job.attempt + 1
                retried.errorMessage.shouldBeNull()
                retried.startedAt.shouldBeNull()
                retried.finishedAt.shouldBeNull()
                retried.updatedAt shouldBe T0 + 4.minutes
            }

            test("a job in another status is JobStatusConflict with the actual status; the row is untouched") {
                val job = stored(JobStatus.CANCELLED)
                val before = reload(job.id)
                clock.now = T0 + 10.minutes

                tx.inRwTransaction {
                    repository.transition(
                        job.id,
                        setOf(JobStatus.DOWNLOADING),
                        JobStatus.DOWNLOADING,
                        JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = 50),
                    )
                }.shouldBeLeft() shouldBe DomainError.JobStatusConflict(job.id, JobStatus.CANCELLED)

                reload(job.id) shouldBe before
            }

            test("an unknown job is JobNotFound") {
                val id = JobId(Uuid.random())
                tx.inRwTransaction {
                    repository.transition(id, setOf(JobStatus.PENDING), JobStatus.CANCELLED)
                }.shouldBeLeft() shouldBe DomainError.JobNotFound(id)
            }

            test("a requeue (no new attempt) keeps attempt; the patch's video info replaces the stored one") {
                val job = stored(JobStatus.DOWNLOADING)
                val actual = aFullVideoInfo("status-actual")
                tx.inRwTransaction {
                    repository.transition(
                        job.id,
                        setOf(JobStatus.DOWNLOADING),
                        JobStatus.DOWNLOADING,
                        JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = 100, videoInfo = actual),
                    )
                }.shouldBeRight().videoInfo shouldBe actual

                val requeued =
                    tx.inRwTransaction {
                        repository.transition(
                            job.id,
                            JobStatus.processingSourcesOf(JobStatus.PENDING),
                            JobStatus.PENDING,
                        )
                    }.shouldBeRight()
                requeued.status shouldBe JobStatus.PENDING
                requeued.attempt shouldBe job.attempt
                requeued.progress.shouldBeNull()
            }
        }

        context("requeueInterrupted") {
            test("processing jobs go back to pending with the same attempt; the others are untouched") {
                val downloading = stored(JobStatus.DOWNLOADING)
                tx.inRwTransaction {
                    repository.transition(
                        downloading.id,
                        setOf(JobStatus.DOWNLOADING),
                        JobStatus.DOWNLOADING,
                        JobStatusPatch(phase = JobPhase.DOWNLOAD, progress = 37),
                    )
                }.shouldBeRight()
                val postProcessing = stored(JobStatus.POST_PROCESSING).copy(attempt = 2)
                tx.inRwTransaction { repository.save(postProcessing) }.shouldBeRight()
                val completed = stored(JobStatus.COMPLETED)
                val failed = stored(JobStatus.FAILED)
                val untouched = listOf(completed, failed).map { reload(it.id) }

                tx.inRwTransaction { repository.requeueInterrupted() } shouldBe 2

                listOf(downloading, postProcessing).forEach { job ->
                    val requeued = reload(job.id)
                    requeued.status shouldBe JobStatus.PENDING
                    requeued.attempt shouldBe job.attempt
                    requeued.progress.shouldBeNull()
                    requeued.startedAt.shouldBeNull()
                }
                untouched.forEach { reload(it.id) shouldBe it }
                tx.inRwTransaction { repository.requeueInterrupted() } shouldBe 0
            }
        }

        context("claimNext") {
            test("claims the oldest pending job; nothing pending is null") {
                val newer = stored(JobStatus.PENDING, createdAt = T0 + 5.minutes)
                val older = aJob(
                    workspace.id,
                    videoId = "status-${++videoCounter}",
                ).copy(createdAt = T0, updatedAt = T0)
                tx.inRwTransaction { repository.save(older) }.shouldBeRight()

                tx.inRwTransaction { repository.claimNext() }.shouldNotBeNull().id shouldBe older.id
                tx.inRwTransaction { repository.claimNext() }.shouldNotBeNull().id shouldBe newer.id
                tx.inRwTransaction { repository.claimNext() }.shouldBeNull()
            }

            /*
             * To see it fail: in `claimNext` drop `.forUpdate(…SKIP_LOCKED…)` — the second claim then waits
             * for the row lock of the first (held open here) and the test times out; drop the lock AND the
             * `status = 'pending'` guard of the update — both claims return the same job.
             */
            test("two claims in parallel transactions over one pending job: exactly one wins") {
                val job = stored(JobStatus.PENDING)
                val claims =
                    withTimeout(20.seconds) {
                        coroutineScope {
                            List(2) {
                                async(Dispatchers.IO) {
                                    tx.inRwTransaction {
                                        val claimed = repository.claimNext()
                                        // Keep the transaction (and its row lock) open while the other one claims.
                                        delay(500)
                                        claimed
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                claims.filterNotNull().map { it.id } shouldBe listOf(job.id)
                reload(job.id).status shouldBe JobStatus.DOWNLOADING
            }

            test("a claim held open does not block another claim: it takes the next job instead") {
                val first = stored(JobStatus.PENDING, createdAt = T0)
                val second = aJob(workspace.id, videoId = "status-${++videoCounter}").copy(
                    createdAt = T0 + 1.minutes,
                    updatedAt = T0 + 1.minutes,
                )
                tx.inRwTransaction { repository.save(second) }.shouldBeRight()
                val claimedFirst = CompletableDeferred<Job?>()
                val release = CompletableDeferred<Unit>()

                withTimeout(20.seconds) {
                    coroutineScope {
                        val holder =
                            async(Dispatchers.IO) {
                                tx.inRwTransaction {
                                    repository.claimNext().also {
                                        claimedFirst.complete(it)
                                        release.await()
                                    }
                                }
                            }
                        claimedFirst.await()?.id shouldBe first.id
                        // While the first claim's transaction is open and holds its row:
                        tx.inRwTransaction { repository.claimNext() }?.id shouldBe second.id
                        tx.inRwTransaction { repository.claimNext() }.shouldBeNull()
                        release.complete(Unit)
                        holder.await()
                    }
                }
                listOf(first, second).map { reload(it.id).status } shouldContainExactlyInAnyOrder
                    listOf(JobStatus.DOWNLOADING, JobStatus.DOWNLOADING)
            }
        }
    })
