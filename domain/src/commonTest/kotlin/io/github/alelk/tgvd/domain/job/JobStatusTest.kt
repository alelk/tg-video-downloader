package io.github.alelk.tgvd.domain.job

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll

class JobStatusTest : FunSpec({

    context("isTerminal") {
        test("COMPLETED, FAILED, CANCELLED are terminal") {
            JobStatus.COMPLETED.isTerminal shouldBe true
            JobStatus.FAILED.isTerminal shouldBe true
            JobStatus.CANCELLED.isTerminal shouldBe true
        }

        test("PENDING, DOWNLOADING, POST_PROCESSING are not terminal") {
            JobStatus.PENDING.isTerminal shouldBe false
            JobStatus.DOWNLOADING.isTerminal shouldBe false
            JobStatus.POST_PROCESSING.isTerminal shouldBe false
        }
    }

    context("isCancellable") {
        test("PENDING, DOWNLOADING, POST_PROCESSING are cancellable") {
            JobStatus.PENDING.isCancellable shouldBe true
            JobStatus.DOWNLOADING.isCancellable shouldBe true
            JobStatus.POST_PROCESSING.isCancellable shouldBe true
        }

        test("COMPLETED, FAILED, CANCELLED are not cancellable") {
            JobStatus.COMPLETED.isCancellable shouldBe false
            JobStatus.FAILED.isCancellable shouldBe false
            JobStatus.CANCELLED.isCancellable shouldBe false
        }
    }

    context("isRetryable") {
        test("FAILED and CANCELLED are retryable") {
            JobStatus.FAILED.isRetryable shouldBe true
            JobStatus.CANCELLED.isRetryable shouldBe true
        }

        test("PENDING, DOWNLOADING, POST_PROCESSING, COMPLETED are not retryable") {
            JobStatus.PENDING.isRetryable shouldBe false
            JobStatus.DOWNLOADING.isRetryable shouldBe false
            JobStatus.POST_PROCESSING.isRetryable shouldBe false
            JobStatus.COMPLETED.isRetryable shouldBe false
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

