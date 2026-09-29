package io.github.alelk.tgvd.api.mapping.workspace

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WorkspaceMappingTest :
    FunSpec({
        test("the role is OWNER for \"owner\" in any case, MEMBER for anything else") {
            listOf("owner", "OWNER", "Owner").forEach { parseWorkspaceRole(it) shouldBe WorkspaceRole.OWNER }
            listOf("member", "admin", "").forEach { parseWorkspaceRole(it) shouldBe WorkspaceRole.MEMBER }
        }

        test("a Telegram user id must be positive") {
            parseTelegramUserId(42) shouldBe TelegramUserId(42).right()
            parseTelegramUserId(0) shouldBe
                DomainError.ValidationError("userId", "TelegramUserId must be positive").left()
        }

        test("a bad new slug carries the value class's message (the legacy {\"error\": …} body)") {
            parseNewWorkspaceSlug("my-team") shouldBe WorkspaceSlug("my-team").right()
            parseNewWorkspaceSlug("Bad Slug") shouldBe
                DomainError.ValidationError(
                    "slug",
                    "WorkspaceSlug must be 3–50 chars, lowercase letters/digits, " +
                        "hyphens allowed but not at start/end: 'Bad Slug'",
                ).left()
        }
    })
