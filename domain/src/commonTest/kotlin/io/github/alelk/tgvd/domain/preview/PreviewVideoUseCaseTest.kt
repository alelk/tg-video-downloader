package io.github.alelk.tgvd.domain.preview

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.DomainError
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.fakes.FakeChannelRepository
import io.github.alelk.tgvd.domain.fakes.FakeJobRepository
import io.github.alelk.tgvd.domain.fakes.FakeLlmPort
import io.github.alelk.tgvd.domain.fakes.FakeRuleRepository
import io.github.alelk.tgvd.domain.fakes.FakeWorkspaceRepository
import io.github.alelk.tgvd.domain.fakes.TestClock
import io.github.alelk.tgvd.domain.fixtures.FIXED_INSTANT
import io.github.alelk.tgvd.domain.fixtures.aJob
import io.github.alelk.tgvd.domain.fixtures.aMember
import io.github.alelk.tgvd.domain.fixtures.aVideoFormat
import io.github.alelk.tgvd.domain.fixtures.aVideoInfo
import io.github.alelk.tgvd.domain.fixtures.aWorkspace
import io.github.alelk.tgvd.domain.fixtures.anAudioFormat
import io.github.alelk.tgvd.domain.fixtures.shouldBeRight
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.metadata.MetadataResolver
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.rule.RuleMatchingService
import io.github.alelk.tgvd.domain.storage.PathTemplateEngine
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.domain.track.aTrackSelectionSettings
import io.github.alelk.tgvd.domain.tx.NoopTransactionRunner
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoInfoCache
import io.github.alelk.tgvd.domain.video.VideoInfoExtractor
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.domain.workspace.WorkspaceAccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PreviewVideoUseCaseTest :
    FunSpec({
        val alice = TelegramUserId(1)
        val bob = TelegramUserId(2)
        val url = "https://example.com/watch?v=video-1"
        val video =
            aVideoInfo(
                availableFormats =
                listOf(
                    aVideoFormat("137"),
                    anAudioFormat("140-ru", language = "ru").copy(isOriginalAudio = true),
                    anAudioFormat("140-en", language = "en"),
                ),
                subtitleTracks =
                listOf(
                    VideoInfo.SubtitleTrack("en-US", automatic = false),
                    VideoInfo.SubtitleTrack("ru", automatic = true),
                    VideoInfo.SubtitleTrack("de", automatic = true),
                ),
            )

        class Env {
            val clock = TestClock()
            val workspaces = FakeWorkspaceRepository()
            val jobs = FakeJobRepository(clock)
            val channels = FakeChannelRepository()
            val extracted = mutableListOf<String>()
            val home = aWorkspace("home").also { workspaces.seed(it, aMember(it, alice)) }
            val work = aWorkspace("work").also { workspaces.seed(it, aMember(it, alice)) }
            private val tx = NoopTransactionRunner()
            private val extractor =
                object : VideoInfoExtractor {
                    override suspend fun extract(url: String): Either<DomainError, VideoInfo> {
                        extracted += url
                        return video.right()
                    }
                }
            private val cache =
                object : VideoInfoCache {
                    override suspend fun get(url: String): VideoInfo? = null

                    override suspend fun put(url: String, videoInfo: VideoInfo) = Unit

                    override suspend fun updateActualFormat(url: String, actualFormat: VideoInfo.Format) = Unit
                }
            private val previewUseCase =
                PreviewUseCase(
                    videoInfoExtractor = extractor,
                    videoInfoCache = cache,
                    ruleMatchingService = RuleMatchingService(FakeRuleRepository(), channels),
                    metadataResolver = MetadataResolver(),
                    llmPort = FakeLlmPort(),
                    txRunner = tx,
                )
            val previewVideo =
                PreviewVideoUseCase(
                    workspaceAccess = WorkspaceAccess(workspaces),
                    previewUseCase = previewUseCase,
                    pathTemplateEngine = PathTemplateEngine(),
                    channelRepository = channels,
                    jobRepository = jobs,
                    trackSelectionSettings = { aTrackSelectionSettings(preferredSubtitleLanguages = listOf("en")) },
                    txRunner = tx,
                )

            suspend fun preview(
                slug: WorkspaceSlug = home.slug,
                user: TelegramUserId = alice,
                overrides: UserOverrides? = null,
            ) = previewVideo(slug, user, url, overrides, force = false)
        }

        test("wraps the resolved preview with the requested source and the rendered storage plan") {
            val env = Env()
            val preview = env.preview().shouldBeRight()

            preview.source shouldBe VideoSource(Url(url), video.videoId, video.extractor)
            preview.result.videoInfo shouldBe video
            preview.result.metadataSource shouldBe MetadataSource.FALLBACK
            preview.appliedOverrides shouldBe null
            val context = PathTemplateEngine().buildContext(video, preview.result.metadata)
            preview.storagePlan shouldBe
                PathTemplateEngine().buildStoragePlan(preview.result.outputs, context, video)
        }

        test("keeps the overrides it was computed with") {
            val overrides = UserOverrides.Other(title = "Custom")
            Env().preview(overrides = overrides).shouldBeRight().appliedOverrides shouldBe overrides
        }

        test("pre-selects the automatic audio tracks and the offered subtitle languages the settings ask for") {
            Env().preview().shouldBeRight().defaultMediaSelection shouldBe
                MediaSelection(audioFormatIds = listOf("140-ru"), subtitleLanguages = listOf("en-US"))
        }

        test("the channel's track preferences take part in the defaults") {
            val env = Env()
            env.channels.seed(
                Channel(
                    id = ChannelDirectoryEntryId(Uuid.random()),
                    workspaceId = env.home.id,
                    channelId = video.channelId,
                    extractor = video.extractor,
                    name = "Channel",
                    tags = emptySet(),
                    trackPreferences = TrackPreferences(audioLanguages = listOf("en"), downloadSubtitles = false),
                    createdAt = FIXED_INSTANT,
                    updatedAt = FIXED_INSTANT,
                ),
            )
            env.preview().shouldBeRight().defaultMediaSelection shouldBe
                MediaSelection(audioFormatIds = listOf("140-ru", "140-en"), subtitleLanguages = emptyList())
        }

        test("previous downloads are the finished jobs of this video in this workspace, newest first") {
            val env = Env()
            val start = env.clock.now()
            val older = env.jobs.seed(aJob(env.home, status = JobStatus.FAILED, createdAt = start))
            val newer = env.jobs.seed(aJob(env.home, status = JobStatus.COMPLETED, createdAt = start + 1.minutes))
            env.jobs.seed(aJob(env.home, status = JobStatus.PENDING, createdAt = start + 2.minutes))
            env.jobs.seed(aJob(env.home, videoId = "another-video", status = JobStatus.COMPLETED))
            env.jobs.seed(aJob(env.work, status = JobStatus.COMPLETED))

            env.preview().shouldBeRight().previousDownloads shouldBe listOf(newer, older)
        }

        test("a non-member is refused before the video is extracted") {
            val env = Env()
            env.preview(user = bob) shouldBe DomainError.WorkspaceAccessDenied(env.home.id, bob).left()
            env.extracted.shouldBeEmpty()
        }

        test("an unknown workspace is WorkspaceNotFoundBySlug") {
            val slug = WorkspaceSlug("nowhere")
            Env().preview(slug = slug) shouldBe DomainError.WorkspaceNotFoundBySlug(slug).left()
        }
    })
