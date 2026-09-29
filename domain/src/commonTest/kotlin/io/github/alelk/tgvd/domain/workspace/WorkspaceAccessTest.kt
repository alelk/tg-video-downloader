package io.github.alelk.tgvd.domain.workspace

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WorkspaceAccessTest :
    FunSpec({
        val alice = TelegramUserId(1)
        val bob = TelegramUserId(2)
        val repository = FakeWorkspaceRepository()
        val home = aWorkspace("home").also { repository.seed(it, aMember(it, alice)) }
        val work = aWorkspace("work").also { repository.seed(it, aMember(it, bob)) }
        val access = WorkspaceAccess(repository)

        test("a member gets the workspace") {
            access.requireMember(home.slug, alice) shouldBe home.right()
        }

        test("an unknown slug is WorkspaceNotFoundBySlug") {
            val slug = WorkspaceSlug("nowhere")
            access.requireMember(slug, alice) shouldBe DomainError.WorkspaceNotFoundBySlug(slug).left()
        }

        test("a user who is not a member is WorkspaceAccessDenied") {
            access.requireMember(home.slug, bob) shouldBe DomainError.WorkspaceAccessDenied(home.id, bob).left()
        }

        test("membership of another workspace does not count") {
            access.requireMember(work.slug, alice) shouldBe DomainError.WorkspaceAccessDenied(work.id, alice).left()
        }
    })
