package io.github.alelk.tgvd.domain.job

import arrow.core.Either
import arrow.core.left
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.fakes.FakeJobRepository
import io.github.alelk.tgvd.domain.fakes.FakeRuleRepository
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.aCreateJobRequest
import io.github.alelk.tgvd.domain.fixtures.aJob
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aStoragePlan
import io.github.alelk.tgvd.domain.fixtures.aVideoFormat
import io.github.alelk.tgvd.domain.fixtures.aVideoInfo
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.anAudioFormat
import io.github.alelk.tgvd.domain.fixtures.shouldBeLeft
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class CreateJobUseCaseTest :
    FunSpec({
        val actor = TelegramUserId(1)
        val stranger = TelegramUserId(2)

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val jobs = FakeJobRepository(clock)
            val rules = FakeRuleRepository()
            val home = aWorkspace("home").also { workspaces.seed(it, aMember(it, actor)) }
            val createJob =
                CreateJobUseCase(WorkspaceAccess(workspaces), jobs, rules, NoopTransactionRunner(), clock)

            suspend fun create(request: CreateJobRequest, user: TelegramUserId = actor) =
                createJob(home.slug, user, request)
        }

        val videoWithTracks =
            aVideoInfo(
                availableFormats =
                listOf(
                    aVideoFormat("137"),
                    anAudioFormat("140-ru", language = "ru"),
                    anAudioFormat("140-en", language = "en", vcodec = null),
                    VideoInfo.Format("18", "mp4", height = 360, vcodec = "avc1", acodec = "mp4a"),
                    anAudioFormat("no-codec", acodec = null),
                ),
                subtitleTracks =
                listOf(VideoInfo.SubtitleTrack("en", automatic = false), VideoInfo.SubtitleTrack("ru", true)),
            )

        fun Either<DomainError, CreateJobResult>.shouldBeValidationError(field: String, message: String) {
            this shouldBe DomainError.ValidationError(field, message).left()
        }

        test("creates a pending job in the workspace on behalf of the actor, stamped by the clock") {
            val env = Env()
            val result = env.create(aCreateJobRequest()).shouldBeRight()

            result.saveAsRuleError.shouldBeNull()
            result.job.workspaceId shouldBe env.home.id
            result.job.createdBy shouldBe actor
            result.job.status shouldBe JobStatus.PENDING
            result.job.createdAt shouldBe env.clock.now()
            env.jobs.all shouldBe listOf(result.job)
            env.rules.all.shouldBeEmpty()
        }

        test("an unknown workspace is WorkspaceNotFoundBySlug and nothing is written") {
            val env = Env()
            val slug = WorkspaceSlug("nowhere")
            env.createJob(slug, actor, aCreateJobRequest()) shouldBe DomainError.WorkspaceNotFoundBySlug(slug).left()
            env.jobs.all.shouldBeEmpty()
        }

        test("a user who is not a member is WorkspaceAccessDenied and nothing is written") {
            val env = Env()
            env.create(aCreateJobRequest(), stranger) shouldBe
                DomainError.WorkspaceAccessDenied(env.home.id, stranger).left()
            env.jobs.all.shouldBeEmpty()
        }

        test("videoInfo.videoId must match source.videoId") {
            Env()
                .create(aCreateJobRequest(sourceVideoId = "other-video"))
                .shouldBeValidationError("videoInfo.videoId", "Must match source.videoId")
        }

        context("mediaSelection.audioFormatIds must be a non-empty set of audio-only tracks the video offers") {
            val field = "mediaSelection.audioFormatIds"
            val message = "Select available audio tracks"
            withData(
                nameFn = { "rejects $it" },
                emptyList(),
                listOf("140-ru", "140-ru"),
                listOf("unknown"),
                listOf("137"),
                listOf("18"),
                listOf("no-codec"),
            ) { ids ->
                Env()
                    .create(aCreateJobRequest(videoWithTracks, mediaSelection = MediaSelection(audioFormatIds = ids)))
                    .shouldBeValidationError(field, message)
            }

            test("accepts audio-only tracks, including one without a video codec field") {
                val selection = MediaSelection(audioFormatIds = listOf("140-en", "140-ru"))
                Env()
                    .create(aCreateJobRequest(videoWithTracks, mediaSelection = selection))
                    .shouldBeRight()
                    .job.mediaSelection shouldBe selection
            }
        }

        context("mediaSelection.subtitleLanguages must be distinct languages the video offers") {
            val field = "mediaSelection.subtitleLanguages"
            val message = "Select available subtitle languages"
            withData(
                nameFn = { "rejects $it" },
                listOf("en", "en"),
                listOf("de"),
                listOf("EN"),
            ) { languages ->
                Env()
                    .create(
                        aCreateJobRequest(
                            videoWithTracks,
                            mediaSelection = MediaSelection(subtitleLanguages = languages),
                        ),
                    ).shouldBeValidationError(field, message)
            }

            test("accepts an empty list (subtitles explicitly off)") {
                Env()
                    .create(
                        aCreateJobRequest(
                            videoWithTracks,
                            mediaSelection = MediaSelection(subtitleLanguages = emptyList()),
                        ),
                    )
                    .shouldBeRight()
            }
        }

        test("a path traversal in the original path is rejected for storagePlan.original") {
            Env()
                .create(aCreateJobRequest(storagePlan = aStoragePlan("/media/../etc/passwd")))
                .shouldBeValidationError(
                    "storagePlan.original",
                    "Path traversal ('..') is not allowed in 'storagePlan.original'",
                )
        }

        test("a forbidden character in an additional path is rejected for storagePlan.additional[i]") {
            Env()
                .create(
                    aCreateJobRequest(storagePlan = aStoragePlan("/media/ok.mkv", "/media/ok.mp4", "/media/a?b.mp4")),
                )
                .shouldBeValidationError(
                    "storagePlan.additional[1]",
                    "Path segment 'a?b.mp4' in 'storagePlan.additional[1]' contains forbidden character '?'",
                )
        }

        test("an active job for the same video in any workspace is JobAlreadyExists") {
            val env = Env()
            val other = aWorkspace("other")
            val active = env.jobs.seed(aJob(other, videoId = "video-1", status = JobStatus.DOWNLOADING))

            env.create(aCreateJobRequest()) shouldBe
                DomainError.JobAlreadyExists(active.source.videoId, active.id).left()
            env.jobs.all shouldBe listOf(active)
        }

        test("a finished job for the same video does not block a new one") {
            val env = Env()
            env.jobs.seed(aJob(env.home, videoId = "video-1", status = JobStatus.COMPLETED))
            env.create(aCreateJobRequest()).shouldBeRight()
        }

        test("a validation error writes neither the job nor the rule") {
            val env = Env()
            env.create(aCreateJobRequest(sourceVideoId = "other", saveAsRule = SaveAsRule())).shouldBeLeft()
            env.jobs.all.shouldBeEmpty()
            env.rules.all.shouldBeEmpty()
        }

        context("saveAsRule") {
            test("creates a rule matching the channel id next to the job") {
                val env = Env()
                val result = env.create(aCreateJobRequest(saveAsRule = SaveAsRule())).shouldBeRight()

                result.saveAsRuleError.shouldBeNull()
                val rule = env.rules.all.single()
                rule.workspaceId shouldBe env.home.id
                rule.match shouldBe RuleMatch.ChannelId("channel-1")
                rule.enabled shouldBe true
                rule.createdAt shouldBe env.clock.now()
            }

            test("matches the channel name when asked, and keeps enabled = false") {
                val env = Env()
                val saveAs = SaveAsRule(matchBy = SaveAsRule.MatchBy.CHANNEL_NAME, enabled = false)
                env.create(aCreateJobRequest(saveAsRule = saveAs)).shouldBeRight()

                val rule = env.rules.all.single()
                rule.match shouldBe RuleMatch.ChannelName("Channel")
                rule.enabled shouldBe false
            }

            test("a rule the repository refuses does not fail the job") {
                val env = Env()
                val refusal = DomainError.ValidationError("name", "refused")
                env.rules.refuseSaveWith = refusal

                val result = env.create(aCreateJobRequest(saveAsRule = SaveAsRule())).shouldBeRight()

                result.saveAsRuleError shouldBe refusal
                env.jobs.all shouldBe listOf(result.job)
            }

            test("a database failure while saving the rule fails the whole call") {
                val env = Env()
                val failure = DomainError.DatabaseFailed("SQLSTATE 08006: connection lost")
                env.rules.refuseSaveWith = failure

                env.create(aCreateJobRequest(saveAsRule = SaveAsRule())).shouldBeLeft() shouldBe failure
            }

            test("a video without a channel name cannot be matched by name; the job is still created") {
                val env = Env()
                val request =
                    aCreateJobRequest(
                        aVideoInfo(channelName = " "),
                        saveAsRule = SaveAsRule(matchBy = SaveAsRule.MatchBy.CHANNEL_NAME),
                    )
                val result = env.create(request).shouldBeRight()

                result.saveAsRuleError.shouldBeInstanceOf<DomainError.ValidationError>().field shouldBe
                    "saveAsRule.matchBy"
                env.jobs.all shouldBe listOf(result.job)
                env.rules.all.shouldBeEmpty()
            }
        }
    })
