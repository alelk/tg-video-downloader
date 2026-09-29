package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

/** `ytDlp.allowUpdate: false` — `POST /system/yt-dlp/update` is refused with the same body as always. */
class YtDlpUpdateDisabledRoutesTest :
    FunSpec({
        val app = routeTestApp { config -> config.copy(ytDlp = config.ytDlp.copy(allowUpdate = false)) }

        test("403 UPDATE_DISABLED with the legacy message") {
            val response = app.client.post("/api/v1/system/yt-dlp/update") { asDevUser() }
            response.status shouldBe HttpStatusCode.Forbidden
            val error = response.body<ApiErrorDto>().error
            error.code shouldBe "UPDATE_DISABLED"
            error.message shouldBe "Update is disabled by administrator"
        }
    })
