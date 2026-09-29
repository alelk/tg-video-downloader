package io.github.alelk.tgvd.server.infra.db

import arrow.core.Either
import arrow.core.left
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.server.infra.db.fixtures.aWorkspace
import io.github.alelk.tgvd.server.infra.db.fixtures.shouldBeRight
import io.github.alelk.tgvd.server.infra.db.repository.WorkspaceRepositoryImpl
import io.github.alelk.tgvd.server.infra.testing.PostgresTestContainer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import kotlin.uuid.ExperimentalUuidApi

/**
 * Pins how [ExposedTransactionRunner] (Exposed 1.0) behaves when runners nest and when a block returns
 * a `Left` — the facts every use-case, `JobProcessor` and `SystemSettingsHolder` rely on (G5).
 */
@OptIn(ExperimentalUuidApi::class)
class ExposedTransactionRunnerTest :
    FunSpec({
        val migrated = PostgresTestContainer.newMigratedDatabase()
        val tx = ExposedTransactionRunner(migrated.database)
        val workspaces = WorkspaceRepositoryImpl()

        /** Counts committed rows from another connection — outside every transaction of the runner. */
        fun committedWorkspaces(slug: String): Int = PostgresTestContainer.connect(migrated.config).use { connection ->
            connection.prepareStatement("SELECT count(*) FROM workspaces WHERE slug = ?").use { statement ->
                statement.setString(1, slug)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

        test("a nested runner joins the outer transaction instead of opening its own") {
            tx.inRwTransaction {
                val outer = TransactionManager.current()
                tx.inRwTransaction { TransactionManager.current() } shouldBeSameInstanceAs outer
                tx.inRoTransaction { TransactionManager.current() } shouldBeSameInstanceAs outer
            }
        }

        test("the inner write is committed with the outer transaction, not before it") {
            val workspace = aWorkspace()

            tx.inRwTransaction {
                tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
                // Visible inside the transaction, invisible to anyone else until the outer block ends.
                tx.inRoTransaction { workspaces.findById(workspace.id) }.shouldNotBeNull()
                committedWorkspaces(workspace.slug.value) shouldBe 0
            }

            committedWorkspaces(workspace.slug.value) shouldBe 1
        }

        test("rolling back the outer transaction rolls back the write of the inner runner") {
            val workspace = aWorkspace()

            shouldThrow<IllegalStateException> {
                tx.inRwTransaction {
                    tx.inRwTransaction { workspaces.save(workspace) }.shouldBeRight()
                    error("fail after the inner write")
                }
            }

            committedWorkspaces(workspace.slug.value) shouldBe 0
            tx.inRoTransaction { workspaces.findById(workspace.id) }.shouldBeNull()
        }

        test("a Left returned by the block COMMITS the writes made before it") {
            val workspace = aWorkspace()

            val result: Either<DomainError, Unit> =
                tx.inRwTransaction {
                    workspaces.save(workspace).shouldBeRight()
                    DomainError.ValidationError("any", "raised after the write").left()
                }

            result.isLeft() shouldBe true
            committedWorkspaces(workspace.slug.value) shouldBe 1
        }

        test("a repository called outside a transaction fails — only the runner opens one") {
            shouldThrow<IllegalStateException> { workspaces.findById(aWorkspace().id) }
        }
    })
