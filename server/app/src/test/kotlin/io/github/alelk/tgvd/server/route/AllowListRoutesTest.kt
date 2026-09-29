package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

/** A non-empty allow-list (Fork 5) admits only the listed Telegram users; everybody else is 403. */
class AllowListRoutesTest :
    FunSpec({
        val app =
            routeTestApp { config ->
                config.copy(telegram = config.telegram.copy(allowedUserIds = listOf("42")))
            }

        test("a user who is not in the allow-list (the dev user, id 1) is 403 FORBIDDEN") {
            val response = app.client.get(WORKSPACES) { asDevUser() }
            response.status shouldBe HttpStatusCode.Forbidden
            response.body<ApiErrorDto>().error.code shouldBe "FORBIDDEN"
        }

        test("the listed user (id 42) is let in") {
            app.client.get(WORKSPACES) { asTelegramUser(42) }.status shouldBe HttpStatusCode.OK
        }
    })
