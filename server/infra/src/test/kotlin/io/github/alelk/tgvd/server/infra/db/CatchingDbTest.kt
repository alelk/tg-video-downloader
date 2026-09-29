package io.github.alelk.tgvd.server.infra.db

import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.aJob
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeLeft
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.db.repository.JobRepositoryImpl
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** [catchingDb] on PostgreSQL: SQLSTATE → [DomainError], rollback of the whole transaction, reusable connection. */
@OptIn(ExperimentalUuidApi::class)
class CatchingDbTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val workspaces = WorkspaceRepositoryImpl()
        val jobs = JobRepositoryImpl(Clock.System)

        fun memberOf(workspaceId: WorkspaceId) =
            WorkspaceMember(workspaceId, TelegramUserId(7), WorkspaceRole.MEMBER, T0)

        test("a failed statement is DatabaseFailed and rolls back the earlier writes of the transaction") {
            val workspace = aWorkspace()

            val failure =
                tx.inRwTransaction {
                    workspaces.save(workspace).shouldBeRight()
                    // Foreign-key violation (SQLSTATE 23503): no such workspace.
                    val left = workspaces.addMember(memberOf(WorkspaceId(Uuid.random()))).shouldBeLeft()
                    // The connection is usable again inside the same block, and the earlier write is gone.
                    workspaces.findById(workspace.id).shouldBeNull()
                    left
                }

            failure.shouldBeInstanceOf<DomainError.DatabaseFailed>().detail shouldContain "23503"
            tx.inRoTransaction { workspaces.findById(workspace.id) }.shouldBeNull()
            // The pool hands out healthy connections afterwards.
            tx.inRwTransaction { workspaces.save(aWorkspace()) }.shouldBeRight()
        }

        test("a duplicate primary key is DatabaseFailed when the domain has no conflict for it") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            tx.inRwTransaction { workspaces.addMember(memberOf(workspace.id)) }.shouldBeRight()

            tx.inRwTransaction { workspaces.addMember(memberOf(workspace.id)) }.shouldBeLeft()
                .shouldBeInstanceOf<DomainError.DatabaseFailed>().detail shouldContain "23505"
        }

        test("a slug taken by a concurrent transaction after the check is WorkspaceSlugConflict (unique index)") {
            val first = aWorkspace()
            val second = Workspace(WorkspaceId(Uuid.random()), first.slug, "Second", T0)
            val firstWritten = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()

            val secondResult =
                coroutineScope {
                    val a =
                        async {
                            tx.inRwTransaction {
                                workspaces.save(first).shouldBeRight()
                                firstWritten.complete(Unit)
                                releaseFirst.await()
                            }
                        }
                    firstWritten.await()
                    // The second transaction does not see the uncommitted slug, passes the check and blocks
                    // on the unique index until the first one commits.
                    val b = async { tx.inRwTransaction { workspaces.save(second) } }
                    delay(300.milliseconds)
                    releaseFirst.complete(Unit)
                    a.await()
                    b.await()
                }

            secondResult.shouldBeLeft() shouldBe DomainError.WorkspaceSlugConflict(first.slug)
            tx.inRoTransaction { workspaces.findBySlug(first.slug) }.shouldNotBeNull().id shouldBe first.id
        }

        test("a second active job for the same video is JobAlreadyExists with the id of the active job") {
            val workspace = aWorkspace()
            tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
            val active = aJob(workspace.id, videoId = "catching-active")
            tx.inRwTransaction { jobs.save(active) }.shouldBeRight()

            // The repository has no check of its own: the partial unique index idx_jobs_active_video refuses.
            val duplicate = aJob(workspace.id, videoId = "catching-active")
            tx.inRwTransaction { jobs.save(duplicate) }.shouldBeLeft() shouldBe
                DomainError.JobAlreadyExists(active.source.videoId, active.id)
            tx.inRoTransaction { jobs.findById(duplicate.id) }.shouldBeNull()
        }
    })
