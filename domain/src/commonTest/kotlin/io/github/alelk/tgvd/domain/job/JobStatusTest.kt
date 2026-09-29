package io.github.alelk.tgvd.domain.job

import io.github.alelk.tgvd.domain.job.JobStatus.CANCELLED
import io.github.alelk.tgvd.domain.job.JobStatus.COMPLETED
import io.github.alelk.tgvd.domain.job.JobStatus.DOWNLOADING
import io.github.alelk.tgvd.domain.job.JobStatus.FAILED
import io.github.alelk.tgvd.domain.job.JobStatus.PENDING
import io.github.alelk.tgvd.domain.job.JobStatus.POST_PROCESSING
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll

class JobStatusTest :
    FunSpec({

        context("transition table: every pair of statuses") {
            // Rows: from; columns: to. The whole table, written out, so that a changed edge is a visible diff.
            val allowed: Map<JobStatus, Set<JobStatus>> =
                mapOf(
                    PENDING to setOf(DOWNLOADING, CANCELLED),
                    DOWNLOADING to setOf(DOWNLOADING, POST_PROCESSING, COMPLETED, FAILED, CANCELLED, PENDING),
                    POST_PROCESSING to setOf(POST_PROCESSING, COMPLETED, FAILED, CANCELLED, PENDING),
                    COMPLETED to emptySet(),
                    FAILED to setOf(PENDING),
                    CANCELLED to setOf(PENDING),
                )

            for (from in JobStatus.entries) {
                for (to in JobStatus.entries) {
                    val expected = to in allowed.getValue(from)
                    test("$from -> $to is ${if (expected) "allowed" else "refused"}") {
                        from.canMoveTo(to) shouldBe expected
                        (to in from.targets) shouldBe expected
                        (from in JobStatus.sourcesOf(to)) shouldBe expected
                    }
                }
            }

            test("a terminal status is left only by a retry to PENDING (COMPLETED never)") {
                JobStatus.entries.filter { it.isTerminal }.forEach { status ->
                    status.targets shouldBe if (status == COMPLETED) emptySet() else setOf(PENDING)
                }
            }
        }

        context("the processor's compare-and-set sources") {
            test("progress stays within DOWNLOADING; a finished outcome comes from either processing status") {
                JobStatus.processingSourcesOf(DOWNLOADING) shouldBe setOf(DOWNLOADING)
                JobStatus.processingSourcesOf(POST_PROCESSING) shouldBe setOf(DOWNLOADING, POST_PROCESSING)
                JobStatus.processingSourcesOf(COMPLETED) shouldBe setOf(DOWNLOADING, POST_PROCESSING)
                JobStatus.processingSourcesOf(FAILED) shouldBe setOf(DOWNLOADING, POST_PROCESSING)
                JobStatus.processingSourcesOf(PENDING) shouldBe setOf(DOWNLOADING, POST_PROCESSING)
            }

            test("CANCELLED is reachable from every non-terminal status") {
                JobStatus.sourcesOf(CANCELLED) shouldBe setOf(PENDING, DOWNLOADING, POST_PROCESSING)
            }
        }

        context("isTerminal") {
            test("COMPLETED, FAILED, CANCELLED are terminal") {
                COMPLETED.isTerminal shouldBe true
                FAILED.isTerminal shouldBe true
                CANCELLED.isTerminal shouldBe true
            }

            test("PENDING, DOWNLOADING, POST_PROCESSING are not terminal") {
                PENDING.isTerminal shouldBe false
                DOWNLOADING.isTerminal shouldBe false
                POST_PROCESSING.isTerminal shouldBe false
            }
        }

        context("isProcessing") {
            test("exactly DOWNLOADING and POST_PROCESSING") {
                JobStatus.entries.filter { it.isProcessing } shouldBe listOf(DOWNLOADING, POST_PROCESSING)
            }
        }

        context("isCancellable and isRetryable come from the table and keep their previous meaning") {
            test("isCancellable: PENDING, DOWNLOADING, POST_PROCESSING (as before the table)") {
                JobStatus.entries.forEach { status ->
                    status.isCancellable shouldBe (status in setOf(PENDING, DOWNLOADING, POST_PROCESSING))
                }
            }

            test("isRetryable: FAILED and CANCELLED (as before the table)") {
                JobStatus.entries.forEach { status ->
                    status.isRetryable shouldBe (status in setOf(FAILED, CANCELLED))
                }
            }
        }

        context("property: terminal and cancellable are disjoint") {
            test("no status is both terminal and cancellable") {
                checkAll(Arb.jobStatus()) { status ->
                    if (status.isTerminal) {
                        status.isCancellable shouldBe false
                    }
                }
            }
        }
    })
