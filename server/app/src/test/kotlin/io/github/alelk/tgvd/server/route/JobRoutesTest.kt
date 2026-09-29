package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobListResponseDto
import io.github.alelk.tgvd.api.contract.job.SaveAsRuleDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.rule.RuleListResponseDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode

/** `…/jobs` through the real module over PostgreSQL (job processor not started). */
class JobRoutesTest :
    FunSpec({
        val app = routeTestApp()

        suspend fun previewOf(slug: String, videoId: String): PreviewResponseDto = app.client
            .post("$WORKSPACES/$slug/preview") {
                asDevUser()
                jsonBody(PreviewRequestDto(url = videoUrl(videoId)))
            }.body()

        test("create -> get -> list -> cancel -> retry") {
            app.client.createWorkspace("jb-happy")
            app.knowsVideo("jb-1")
            val preview = previewOf("jb-happy", "jb-1")

            val created =
                app.client.post("$WORKSPACES/jb-happy/jobs") {
                    asDevUser()
                    jsonBody(
                        preview.toCreateJobRequest().copy(
                            mediaSelection = MediaSelectionDto(listOf("140-1"), listOf("en")),
                        ),
                    )
                }
            created.status shouldBe HttpStatusCode.Created
            val job = created.body<JobDto>()
            job.status shouldBe "pending"
            job.source shouldBe preview.source
            job.videoInfo shouldBe preview.videoInfo
            job.category shouldBe preview.category
            job.metadata shouldBe preview.metadata
            job.storagePlan shouldBe preview.storagePlan
            job.ruleId.shouldBeNull()
            job.progress.shouldBeNull()
            job.error.shouldBeNull()
            job.attempt shouldBe 0
            job.createdBy shouldBe DEV_USER_ID.toString()

            val fetched = app.client.get("$WORKSPACES/jb-happy/jobs/${job.id}") { asDevUser() }
            fetched.status shouldBe HttpStatusCode.OK
            fetched.body<JobDto>().id shouldBe job.id

            val list = app.client.get("$WORKSPACES/jb-happy/jobs") { asDevUser() }.body<JobListResponseDto>()
            list.items.map { it.id } shouldBe listOf(job.id)
            list.total shouldBe 1
            list.limit shouldBe 20
            list.offset shouldBe 0
            app.client
                .get("$WORKSPACES/jb-happy/jobs?status=completed") { asDevUser() }
                .body<JobListResponseDto>()
                .total shouldBe 0

            val cancelled = app.client.post("$WORKSPACES/jb-happy/jobs/${job.id}/cancel") { asDevUser() }
            cancelled.status shouldBe HttpStatusCode.OK
            cancelled.body<JobDto>().status shouldBe "cancelled"

            val cancelAgain = app.client.post("$WORKSPACES/jb-happy/jobs/${job.id}/cancel") { asDevUser() }
            cancelAgain.status shouldBe HttpStatusCode.Conflict
            cancelAgain.body<ApiErrorDto>().error.code shouldBe "CONFLICT"

            val retried = app.client.post("$WORKSPACES/jb-happy/jobs/${job.id}/retry") { asDevUser() }
            retried.status shouldBe HttpStatusCode.OK
            retried.body<JobDto>().let { it.status to it.attempt } shouldBe ("pending" to 1)
        }

        test("a second active job for the same video is 409 CONFLICT") {
            app.client.createWorkspace("jb-dup")
            app.knowsVideo("jb-2")
            val request = previewOf("jb-dup", "jb-2").toCreateJobRequest()
            app.client.post("$WORKSPACES/jb-dup/jobs") {
                asDevUser()
                jsonBody(request)
            }.status shouldBe HttpStatusCode.Created

            val duplicate =
                app.client.post("$WORKSPACES/jb-dup/jobs") {
                    asDevUser()
                    jsonBody(request)
                }
            duplicate.status shouldBe HttpStatusCode.Conflict
            duplicate.body<ApiErrorDto>().error.code shouldBe "CONFLICT"
        }

        test("saveAsRule creates a channel rule next to the job") {
            app.client.createWorkspace("jb-save-rule")
            app.knowsVideo("jb-3", channelId = "UC-save-rule")
            val request = previewOf("jb-save-rule", "jb-3").toCreateJobRequest().copy(saveAsRule = SaveAsRuleDto())

            app.client.post("$WORKSPACES/jb-save-rule/jobs") {
                asDevUser()
                jsonBody(request)
            }.status shouldBe HttpStatusCode.Created

            val rules = app.client.get("$WORKSPACES/jb-save-rule/rules") { asDevUser() }.body<RuleListResponseDto>()
            rules.items.map { it.match } shouldBe listOf(RuleMatchDto.ChannelId("UC-save-rule"))
        }

        test("an audio track that the video does not have is 400 VALIDATION_ERROR") {
            app.client.createWorkspace("jb-media")
            app.knowsVideo("jb-4")
            val response =
                app.client.post("$WORKSPACES/jb-media/jobs") {
                    asDevUser()
                    jsonBody(
                        previewOf(
                            "jb-media",
                            "jb-4",
                        ).toCreateJobRequest().copy(mediaSelection = MediaSelectionDto(listOf("137"))),
                    )
                }
            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ApiErrorDto>().error.code shouldBe "VALIDATION_ERROR"
        }

        test("a job of another workspace is 404 NOT_FOUND through get, cancel and retry") {
            app.client.createWorkspace("jb-mine")
            val other = signedInitData(6, "sixth")
            app.client.createWorkspace("jb-theirs", initData = other)
            app.knowsVideo("jb-5")
            val foreignJob = app.client.createJob("jb-theirs", "jb-5", initData = other)

            listOf(
                app.client.get("$WORKSPACES/jb-mine/jobs/${foreignJob.id}") { asDevUser() },
                app.client.post("$WORKSPACES/jb-mine/jobs/${foreignJob.id}/cancel") { asDevUser() },
                app.client.post("$WORKSPACES/jb-mine/jobs/${foreignJob.id}/retry") { asDevUser() },
            ).forEach { response ->
                response.status shouldBe HttpStatusCode.NotFound
                response.body<ApiErrorDto>().error.code shouldBe "NOT_FOUND"
            }
        }
    })
