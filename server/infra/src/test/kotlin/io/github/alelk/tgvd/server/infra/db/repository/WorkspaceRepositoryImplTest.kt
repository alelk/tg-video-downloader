package io.github.alelk.tgvd.server.infra.db.repository

import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole
import io.github.alelk.tgvd.server.infra.db.ExposedTransactionRunner
import io.github.alelk.tgvd.server.infra.db.fixtures.T0
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeLeft
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.db.fixtures.uniqueSlug
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Round-trips of [WorkspaceRepositoryImpl] on PostgreSQL, every call under [ExposedTransactionRunner]. */
class WorkspaceRepositoryImplTest :
    FunSpec({
        val db = PostgresTestContainer.newMigratedDatabase().database
        val tx = ExposedTransactionRunner(db)
        val repository = WorkspaceRepositoryImpl(db)

        // created_at is set by the database on insert; the repository does not write it.
        fun Workspace.withoutCreatedAt() = copy(createdAt = T0)

        fun WorkspaceMember.withoutJoinedAt() = copy(joinedAt = T0)

        test("save -> find -> update -> find") {
            val workspace = aWorkspace()
            tx.inRwTransaction { repository.save(workspace) }.shouldBeRight() shouldBe workspace

            tx.inRoTransaction { repository.findById(workspace.id) }.shouldNotBeNull().withoutCreatedAt() shouldBe
                workspace
            tx.inRoTransaction { repository.findBySlug(workspace.slug) }.shouldNotBeNull().id shouldBe workspace.id

            val renamed = workspace.copy(slug = uniqueSlug("renamed"), name = "Renamed")
            tx.inRwTransaction { repository.save(renamed) }.shouldBeRight()

            tx.inRoTransaction { repository.findById(workspace.id) }.shouldNotBeNull().withoutCreatedAt() shouldBe
                renamed
            tx.inRoTransaction { repository.findBySlug(workspace.slug) }.shouldBeNull()
        }

        test("a taken slug is WorkspaceSlugConflict on insert and on update") {
            val first = aWorkspace()
            val second = aWorkspace()
            tx.inRwTransaction { repository.save(first) }.shouldBeRight()
            tx.inRwTransaction { repository.save(second) }.shouldBeRight()

            tx
                .inRwTransaction { repository.save(aWorkspace(slug = first.slug)) }
                .shouldBeLeft()
                .shouldBeInstanceOf<DomainError.WorkspaceSlugConflict>()
            tx
                .inRwTransaction { repository.save(second.copy(slug = first.slug)) }
                .shouldBeLeft()
                .shouldBeInstanceOf<DomainError.WorkspaceSlugConflict>()
        }

        test("members: add -> find -> remove") {
            val workspace = aWorkspace()
            tx.inRwTransaction { repository.save(workspace) }.shouldBeRight()
            val owner = WorkspaceMember(workspace.id, TelegramUserId(1001), WorkspaceRole.OWNER, T0)
            val member = WorkspaceMember(workspace.id, TelegramUserId(1002), WorkspaceRole.MEMBER, T0)

            tx.inRwTransaction { repository.addMember(owner) }.shouldBeRight() shouldBe owner
            tx.inRwTransaction { repository.addMember(member) }.shouldBeRight() shouldBe member

            tx.inRoTransaction {
                repository.findMembers(workspace.id)
            }.map { it.withoutJoinedAt() } shouldContainExactlyInAnyOrder
                listOf(owner, member)
            tx.inRoTransaction { repository.findByUser(TelegramUserId(1002)) }.map { it.withoutJoinedAt() } shouldBe
                listOf(member)
            tx.inRoTransaction { repository.isMember(workspace.id, TelegramUserId(1001)) }.shouldBeTrue()

            tx.inRwTransaction { repository.removeMember(workspace.id, TelegramUserId(1002)) }.shouldBeTrue()
            tx.inRwTransaction { repository.removeMember(workspace.id, TelegramUserId(1002)) }.shouldBeFalse()
            tx.inRoTransaction { repository.isMember(workspace.id, TelegramUserId(1002)) }.shouldBeFalse()
        }
    })
