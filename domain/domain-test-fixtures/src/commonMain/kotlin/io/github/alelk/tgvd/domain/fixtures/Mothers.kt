package io.github.alelk.tgvd.domain.fixtures

import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.job.CreateJobRequest
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.job.SaveAsRule
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.domain.workspace.Workspace
import io.github.alelk.tgvd.domain.workspace.WorkspaceMember
import io.github.alelk.tgvd.domain.workspace.WorkspaceRole
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Object mothers for use-case tests: deterministic defaults, a test names only what it is about.

val FIXED_INSTANT: Instant = Instant.parse("2026-01-01T09:00:00Z")

@OptIn(ExperimentalUuidApi::class)
fun aWorkspace(slug: String = "home", id: WorkspaceId = WorkspaceId(Uuid.random()), name: String = "Home"): Workspace =
    Workspace(id = id, slug = WorkspaceSlug(slug), name = name, createdAt = FIXED_INSTANT)

fun aMember(
    workspace: Workspace,
    userId: TelegramUserId,
    role: WorkspaceRole = WorkspaceRole.MEMBER,
): WorkspaceMember = WorkspaceMember(workspace.id, userId, role, FIXED_INSTANT)

fun anAudioFormat(
    formatId: String,
    language: String? = null,
    acodec: String? = "opus",
    vcodec: String? = "none",
): VideoInfo.Format =
    VideoInfo.Format(formatId = formatId, extension = "webm", vcodec = vcodec, acodec = acodec, language = language)

fun aVideoFormat(formatId: String, height: Int = 1080): VideoInfo.Format =
    VideoInfo.Format(formatId = formatId, extension = "webm", height = height, vcodec = "vp9", acodec = "none")

fun aVideoInfo(
    videoId: String = "video-1",
    channelId: String = "channel-1",
    channelName: String = "Channel",
    availableFormats: List<VideoInfo.Format> = emptyList(),
    subtitleTracks: List<VideoInfo.SubtitleTrack> = emptyList(),
): VideoInfo = VideoInfo(
    videoId = VideoId(videoId),
    extractor = Extractor.YOUTUBE,
    title = "Artist - Title",
    channelId = ChannelId(channelId),
    channelName = channelName,
    uploadDate = null,
    duration = 60.seconds,
    webpageUrl = Url("https://example.com/watch?v=$videoId"),
    availableFormats = availableFormats,
    subtitleTracks = subtitleTracks,
)

fun aStoragePlan(original: String = "/media/Channel/Title [video-1].mkv", vararg additional: String): StoragePlan =
    StoragePlan(
        original = OutputTarget(FilePath(original), OutputFormat.OriginalVideo(MediaContainer.MKV)),
        additional = additional.map { OutputTarget(FilePath(it), OutputFormat.ConvertedVideo(MediaContainer.MP4)) },
    )

fun aCreateJobRequest(
    videoInfo: VideoInfo = aVideoInfo(),
    sourceVideoId: String = videoInfo.videoId.value,
    storagePlan: StoragePlan = aStoragePlan(),
    mediaSelection: MediaSelection? = null,
    saveAsRule: SaveAsRule? = null,
): CreateJobRequest = CreateJobRequest(
    source = VideoSource(videoInfo.webpageUrl, VideoId(sourceVideoId), videoInfo.extractor),
    videoInfo = videoInfo,
    metadata = ResolvedMetadata.Other(title = "Title"),
    metadataSource = MetadataSource.RULE,
    storagePlan = storagePlan,
    mediaSelection = mediaSelection,
    saveAsRule = saveAsRule,
)

@OptIn(ExperimentalUuidApi::class)
fun aJob(
    workspace: Workspace,
    videoId: String = "video-1",
    status: JobStatus = JobStatus.PENDING,
    createdAt: Instant = FIXED_INSTANT,
    createdBy: TelegramUserId = TelegramUserId(1),
): Job = Job(
    id = JobId(Uuid.random()),
    workspaceId = workspace.id,
    createdBy = createdBy,
    source = VideoSource(Url("https://example.com/watch?v=$videoId"), VideoId(videoId), Extractor.YOUTUBE),
    metadata = ResolvedMetadata.Other(title = "Title"),
    metadataSource = MetadataSource.RULE,
    storagePlan = aStoragePlan(),
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
    finishedAt = if (status.isTerminal) createdAt else null,
)
