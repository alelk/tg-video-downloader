package io.github.alelk.tgvd.domain.job

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.fakes.FakeJobRepository
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.aJob
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The workspace-scoped job use-cases: list, get, cancel, retry. */
@OptIn(ExperimentalUuidApi::class)
class JobUseCasesTest :
    FunSpec({
        val alice = TelegramUserId(1)
        val bob = TelegramUserId(2)

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val jobs = FakeJobRepository(clock)
            val home = aWorkspace("home").also { workspaces.seed(it, aMember(it, alice)) }
            val work = aWorkspace("work").also { workspaces.seed(it, aMember(it, bob)) }
            private val access = WorkspaceAccess(workspaces)
            private val tx = NoopTransactionRunner()
            val listJobs = ListJobsUseCase(access, jobs, tx)
            val getJob = GetJobUseCase(access, jobs, tx)
            val cancelJob = CancelJobUseCase(access, jobs, tx)
            val retryJob = RetryJobUseCase(access, jobs, tx)
        }

        context("ListJobsUseCase") {
            val env = Env()
            val start = env.clock.now()
            val (pending, completed, processing) =
                listOf(JobStatus.PENDING, JobStatus.COMPLETED, JobStatus.POST_PROCESSING).mapIndexed { i, status ->
                    env.jobs.seed(aJob(env.home, videoId = "v-$i", status = status, createdAt = start + i.minutes))
                }
            env.jobs.seed(aJob(env.work, videoId = "foreign"))

            test("lists the workspace's jobs newest first; total counts them all") {
                val page = env.listJobs(env.home.slug, alice, null, 0, 20).shouldBeRight()
                page.items shouldBe listOf(processing, completed, pending)
                page.total shouldBe 3
            }

            test("filters by status name ignoring case; total counts the filtered jobs") {
                env.listJobs(env.home.slug, alice, "completed", 0, 20).shouldBeRight() shouldBe
                    JobPage(listOf(completed), 1)
                env.listJobs(env.home.slug, alice, "post_PROCESSING", 0, 20).shouldBeRight() shouldBe
                    JobPage(listOf(processing), 1)
            }

            test("an unknown status name matches nothing") {
                env.listJobs(env.home.slug, alice, "done", 0, 20).shouldBeRight() shouldBe JobPage(emptyList(), 0)
            }

            test("offset and limit cut the page; total is not affected") {
                env.listJobs(env.home.slug, alice, null, 1, 1).shouldBeRight() shouldBe JobPage(listOf(completed), 3)
                env.listJobs(env.home.slug, alice, null, 5, 20).shouldBeRight() shouldBe JobPage(emptyList(), 3)
            }

            test("a non-member is WorkspaceAccessDenied") {
                env.listJobs(env.home.slug, bob, null, 0, 20) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
            }
        }

        context("GetJobUseCase") {
            test("reads a job of the workspace") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home))
                env.getJob(env.home.slug, alice, job.id) shouldBe job.right()
            }

            test("a job of another workspace is JobNotFound, like a missing one") {
                val env = Env()
                val foreign = env.jobs.seed(aJob(env.work))
                val missing = JobId(Uuid.random())
                env.getJob(env.home.slug, alice, foreign.id) shouldBe DomainError.JobNotFound(foreign.id).left()
                env.getJob(env.home.slug, alice, missing) shouldBe DomainError.JobNotFound(missing).left()
            }

            test("a non-member is WorkspaceAccessDenied even for a job of that workspace") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home))
                env.getJob(env.home.slug, bob, job.id) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
            }
        }

        context("CancelJobUseCase") {
            test("cancels a pending job") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home, status = JobStatus.PENDING))
                env.cancelJob(env.home.slug, alice, job.id).shouldBeRight().status shouldBe JobStatus.CANCELLED
            }

            test("a finished job cannot be cancelled") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home, status = JobStatus.COMPLETED))
                env.cancelJob(env.home.slug, alice, job.id) shouldBe
                    DomainError.JobCannotBeCancelled(job.id, JobStatus.COMPLETED).left()
            }

            test("a job of another workspace is JobNotFound and stays untouched") {
                val env = Env()
                val foreign = env.jobs.seed(aJob(env.work, status = JobStatus.DOWNLOADING))
                env.cancelJob(env.home.slug, alice, foreign.id) shouldBe DomainError.JobNotFound(foreign.id).left()
                env.jobs.findById(foreign.id) shouldBe foreign
            }

            test("cancels a downloading job without touching attempt") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home, status = JobStatus.DOWNLOADING))
                val cancelled = env.cancelJob(env.home.slug, alice, job.id).shouldBeRight()
                cancelled.status shouldBe JobStatus.CANCELLED
                cancelled.attempt shouldBe job.attempt
                cancelled.finishedAt shouldBe env.clock.now()
            }

            test("a job that completed after the read is JobCannotBeCancelled with the actual status, untouched") {
                val env = Env()
                val completed = env.jobs.seed(aJob(env.home, status = JobStatus.COMPLETED))
                // The use-case reads a stale DOWNLOADING snapshot; the compare-and-set sees COMPLETED.
                val stale = StaleReads(env.jobs, completed.copy(status = JobStatus.DOWNLOADING))
                CancelJobUseCase(WorkspaceAccess(env.workspaces), stale, NoopTransactionRunner())(
                    env.home.slug,
                    alice,
                    completed.id,
                ) shouldBe DomainError.JobCannotBeCancelled(completed.id, JobStatus.COMPLETED).left()
                env.jobs.findById(completed.id) shouldBe completed
            }
        }

        context("RetryJobUseCase") {
            test("puts a failed job back to pending with the next attempt") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home, status = JobStatus.FAILED))
                val retried = env.retryJob(env.home.slug, alice, job.id).shouldBeRight()
                retried.status shouldBe JobStatus.PENDING
                retried.attempt shouldBe job.attempt + 1
            }

            test("a pending job cannot be retried") {
                val env = Env()
                val job = env.jobs.seed(aJob(env.home, status = JobStatus.PENDING))
                env.retryJob(env.home.slug, alice, job.id) shouldBe
                    DomainError.JobCannotBeRetried(job.id, JobStatus.PENDING).left()
            }

            test("a job of another workspace is JobNotFound and stays untouched") {
                val env = Env()
                val foreign = env.jobs.seed(aJob(env.work, status = JobStatus.FAILED))
                env.retryJob(env.home.slug, alice, foreign.id) shouldBe DomainError.JobNotFound(foreign.id).left()
                env.jobs.findById(foreign.id) shouldBe foreign
            }

            test("a cancelled job is retried too; the error and the timestamps of the old attempt are cleared") {
                val env = Env()
                val job =
                    env.jobs.seed(
                        aJob(env.home, status = JobStatus.CANCELLED).copy(
                            errorMessage = "old",
                            startedAt = env.clock.now(),
                            finishedAt = env.clock.now(),
                        ),
                    )
                val retried = env.retryJob(env.home.slug, alice, job.id).shouldBeRight()
                retried.status shouldBe JobStatus.PENDING
                retried.attempt shouldBe job.attempt + 1
                retried.errorMessage shouldBe null
                retried.startedAt shouldBe null
                retried.finishedAt shouldBe null
            }

            test("a job retried concurrently is JobCannotBeRetried with the actual status, attempt counted once") {
                val env = Env()
                val retried = env.jobs.seed(aJob(env.home, status = JobStatus.PENDING).copy(attempt = 1))
                // The use-case reads a stale FAILED snapshot; the compare-and-set sees PENDING.
                val stale = StaleReads(env.jobs, retried.copy(status = JobStatus.FAILED))
                RetryJobUseCase(WorkspaceAccess(env.workspaces), stale, NoopTransactionRunner())(
                    env.home.slug,
                    alice,
                    retried.id,
                ) shouldBe DomainError.JobCannotBeRetried(retried.id, JobStatus.PENDING).left()
                env.jobs.findById(retried.id) shouldBe retried
            }
        }
    })

/** Answers [findById] with a stale [snapshot] (the read of a use-case racing another writer); writes go to [jobs]. */
private class StaleReads(private val jobs: FakeJobRepository, private val snapshot: Job) : JobRepository by jobs {
    override suspend fun findById(id: JobId): Job? = if (id == snapshot.id) snapshot else jobs.findById(id)
}
