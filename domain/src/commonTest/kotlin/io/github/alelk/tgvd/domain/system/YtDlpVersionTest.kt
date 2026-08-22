package io.github.alelk.tgvd.domain.system

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class YtDlpVersionTest : FunSpec({

    test("newer date-based version is newer") {
        YtDlpVersion("2024.08.06").isNewerThan(YtDlpVersion("2024.08.05")) shouldBe true
        YtDlpVersion("2024.09.01").isNewerThan(YtDlpVersion("2024.08.30")) shouldBe true
        YtDlpVersion("2025.01.01").isNewerThan(YtDlpVersion("2024.12.31")) shouldBe true
    }

    test("older date-based version is not newer") {
        YtDlpVersion("2024.08.05").isNewerThan(YtDlpVersion("2024.08.06")) shouldBe false
    }

    test("equal versions are not newer") {
        YtDlpVersion("2024.08.06").isNewerThan(YtDlpVersion("2024.08.06")) shouldBe false
    }

    test("patch/nightly suffix breaks the tie") {
        YtDlpVersion("2024.08.06.123").isNewerThan(YtDlpVersion("2024.08.06")) shouldBe true
        YtDlpVersion("2024.08.06").isNewerThan(YtDlpVersion("2024.08.06.123")) shouldBe false
    }

    test("tolerates a leading 'v' prefix") {
        YtDlpVersion("v2024.08.06").isNewerThan(YtDlpVersion("2024.08.05")) shouldBe true
    }
})
