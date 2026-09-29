package io.github.alelk.tgvd.domain.rule

import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.fakes.FakeRuleRepository
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.aCreateRuleRequest
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aRule
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The workspace-scoped rule use-cases: list, get, create, update, delete. */
@OptIn(ExperimentalUuidApi::class)
class RuleUseCasesTest :
    FunSpec({
        val alice = TelegramUserId(1)
        val bob = TelegramUserId(2)

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val rules = FakeRuleRepository()
            val home = aWorkspace("home").also { workspaces.seed(it, aMember(it, alice)) }
            val work = aWorkspace("work").also { workspaces.seed(it, aMember(it, bob)) }
            private val access = WorkspaceAccess(workspaces)
            private val tx = NoopTransactionRunner()
            val listRules = ListRulesUseCase(access, rules, tx)
            val getRule = GetRuleUseCase(access, rules, tx)
            val createRule = CreateRuleUseCase(access, rules, tx, clock)
            val updateRule = UpdateRuleUseCase(access, rules, tx, clock)
            val deleteRule = DeleteRuleUseCase(access, rules, tx)
        }

        context("ListRulesUseCase") {
            test("lists the workspace's rules, highest priority first, without other workspaces' rules") {
                val env = Env()
                val low = env.rules.seed(aRule(env.home, name = "low", priority = 1))
                val high = env.rules.seed(aRule(env.home, name = "high", priority = 9))
                env.rules.seed(aRule(env.work, name = "foreign"))
                env.listRules(env.home.slug, alice) shouldBe listOf(high, low).right()
            }

            test("a non-member is WorkspaceAccessDenied; an unknown slug is WorkspaceNotFoundBySlug") {
                val env = Env()
                env.listRules(env.home.slug, bob) shouldBe DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
                val nowhere = WorkspaceSlug("nowhere")
                env.listRules(nowhere, alice) shouldBe DomainError.WorkspaceNotFoundBySlug(nowhere).left()
            }
        }

        context("GetRuleUseCase") {
            test("reads a rule of the workspace; a rule of another workspace is RuleNotFound, like a missing one") {
                val env = Env()
                val rule = env.rules.seed(aRule(env.home))
                val foreign = env.rules.seed(aRule(env.work))
                val missing = RuleId(Uuid.random())
                env.getRule(env.home.slug, alice, rule.id) shouldBe rule.right()
                env.getRule(env.home.slug, alice, foreign.id) shouldBe DomainError.RuleNotFound(foreign.id).left()
                env.getRule(env.home.slug, alice, missing) shouldBe DomainError.RuleNotFound(missing).left()
            }
        }

        context("CreateRuleUseCase") {
            test("creates the rule in the caller's workspace at the clock's time") {
                val env = Env()
                val request = aCreateRuleRequest(name = "New", priority = 3)
                val created = env.createRule(env.home.slug, alice, request).shouldBeRight()
                created.workspaceId shouldBe env.home.id
                created.name shouldBe "New"
                created.priority shouldBe 3
                created.createdAt shouldBe env.clock.now()
                created.updatedAt shouldBe env.clock.now()
                env.rules.all shouldBe listOf(created)
            }

            test("a non-member cannot create a rule, nothing is stored") {
                val env = Env()
                env.createRule(env.home.slug, bob, aCreateRuleRequest()) shouldBe
                    DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
                env.rules.all shouldBe emptyList()
            }
        }

        context("UpdateRuleUseCase") {
            test("overwrites only the given fields and stamps updatedAt") {
                val env = Env()
                val rule = env.rules.seed(aRule(env.home, name = "old", priority = 1))
                env.clock.advance(5.minutes)
                val updated =
                    env.updateRule(env.home.slug, alice, rule.id, UpdateRuleRequest(name = "new", enabled = false))
                        .shouldBeRight()
                updated shouldBe rule.copy(name = "new", enabled = false, updatedAt = env.clock.now())
                env.rules.findById(rule.id) shouldBe updated
            }

            test("a rule of another workspace is RuleNotFound and stays unchanged") {
                val env = Env()
                val foreign = env.rules.seed(aRule(env.work))
                env.updateRule(env.home.slug, alice, foreign.id, UpdateRuleRequest(name = "hijack")) shouldBe
                    DomainError.RuleNotFound(foreign.id).left()
                env.rules.findById(foreign.id) shouldBe foreign
            }
        }

        context("DeleteRuleUseCase") {
            test("deletes a rule of the workspace") {
                val env = Env()
                val rule = env.rules.seed(aRule(env.home))
                env.deleteRule(env.home.slug, alice, rule.id) shouldBe Unit.right()
                env.rules.findById(rule.id) shouldBe null
            }

            test("a rule of another workspace is RuleNotFound and is not deleted") {
                val env = Env()
                val foreign = env.rules.seed(aRule(env.work))
                env.deleteRule(env.home.slug, alice, foreign.id) shouldBe DomainError.RuleNotFound(foreign.id).left()
                env.rules.findById(foreign.id) shouldBe foreign
            }
        }
    })
