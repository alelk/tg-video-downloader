package io.github.alelk.tgvd.domain.workspace

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/** Workspace use-cases: list, create (join by slug), members list/add/remove. */
class WorkspaceUseCasesTest :
    FunSpec({
        val owner = TelegramUserId(1)
        val member = TelegramUserId(2)
        val stranger = TelegramUserId(3)
        val newcomer = TelegramUserId(4)

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val home = aWorkspace("home").also {
                workspaces.seed(it, aMember(it, owner, WorkspaceRole.OWNER), aMember(it, member))
            }
            val work = aWorkspace("work").also { workspaces.seed(it, aMember(it, stranger, WorkspaceRole.OWNER)) }
            private val tx = NoopTransactionRunner()
            val listWorkspaces = ListWorkspacesUseCase(workspaces, tx)
            val createWorkspace = CreateWorkspaceUseCase(workspaces, tx, clock)
            val listMembers = ListWorkspaceMembersUseCase(WorkspaceAccess(workspaces), workspaces, tx)
            val addMember = AddWorkspaceMemberUseCase(workspaces, tx, clock)
            val removeMember = RemoveWorkspaceMemberUseCase(workspaces, tx)
        }

        context("ListWorkspacesUseCase") {
            test("the caller's workspaces, each with the caller's membership") {
                val env = Env()
                env.listWorkspaces(owner) shouldBe
                    listOf(WorkspaceMembership(env.home, aMember(env.home, owner, WorkspaceRole.OWNER)))
                env.listWorkspaces(newcomer) shouldBe emptyList()
            }
        }

        context("CreateWorkspaceUseCase") {
            test("a new slug creates the workspace with the caller as OWNER at the clock's time") {
                val env = Env()
                val result = env.createWorkspace(WorkspaceSlug("fresh"), "Fresh", newcomer).shouldBeRight()
                result.created shouldBe true
                result.workspace.slug shouldBe WorkspaceSlug("fresh")
                result.workspace.createdAt shouldBe env.clock.now()
                result.membership shouldBe
                    WorkspaceMember(result.workspace.id, newcomer, WorkspaceRole.OWNER, env.clock.now())
            }

            test("a taken slug adds the caller as MEMBER and returns the existing workspace (join by slug)") {
                val env = Env()
                val result = env.createWorkspace(env.home.slug, "Ignored", newcomer).shouldBeRight()
                result.created shouldBe false
                result.workspace shouldBe env.home
                result.membership shouldBe WorkspaceMember(env.home.id, newcomer, WorkspaceRole.MEMBER, env.clock.now())
            }
        }

        context("ListWorkspaceMembersUseCase") {
            test("a member lists the members; a non-member is WorkspaceAccessDenied") {
                val env = Env()
                env.listMembers(env.home.slug, member).shouldBeRight().map { it.userId } shouldContainExactlyInAnyOrder
                    listOf(owner, member)
                env.listMembers(env.home.slug, stranger) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, stranger).left()
            }
        }

        context("AddWorkspaceMemberUseCase") {
            test("the OWNER adds a member with the given role at the clock's time") {
                val env = Env()
                env.addMember(env.home.slug, owner, newcomer, WorkspaceRole.OWNER) shouldBe
                    WorkspaceMember(env.home.id, newcomer, WorkspaceRole.OWNER, env.clock.now()).right()
                env.workspaces.isMember(env.home.id, newcomer) shouldBe true
            }

            test("a plain member and a stranger are WorkspaceAccessDenied; an unknown slug is not found") {
                val env = Env()
                env.addMember(env.home.slug, member, newcomer, WorkspaceRole.MEMBER) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, member).left()
                env.addMember(env.home.slug, stranger, newcomer, WorkspaceRole.MEMBER) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, stranger).left()
                val nowhere = WorkspaceSlug("nowhere")
                env.addMember(nowhere, owner, newcomer, WorkspaceRole.MEMBER) shouldBe
                    DomainError.WorkspaceNotFoundBySlug(nowhere).left()
                env.workspaces.isMember(env.home.id, newcomer) shouldBe false
            }
        }

        context("RemoveWorkspaceMemberUseCase") {
            test("the OWNER removes a member; removing a non-member is a ValidationError") {
                val env = Env()
                env.removeMember(env.home.slug, owner, member) shouldBe Unit.right()
                env.workspaces.isMember(env.home.id, member) shouldBe false
                env.removeMember(env.home.slug, owner, newcomer) shouldBe
                    DomainError.ValidationError("userId", "User 4 is not a member").left()
            }

            test("a plain member cannot remove anyone") {
                val env = Env()
                env.removeMember(env.home.slug, member, owner) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, member).left()
                env.workspaces.isMember(env.home.id, owner) shouldBe true
            }
        }
    })
