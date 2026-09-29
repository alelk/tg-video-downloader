package io.github.alelk.tgvd.domain.fixtures

import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.channel.CreateChannelRequest
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.Tag
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
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.CreateRuleRequest
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
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

fun aCreateRuleRequest(name: String = "Music", channelId: String = "channel-1", priority: Int = 0): CreateRuleRequest =
    CreateRuleRequest(
        name = name,
        match = RuleMatch.ChannelId(channelId),
        metadataTemplate = MetadataTemplate.Other(),
        outputs = listOf(OutputRule("/media/{title}.{ext}", OutputFormat.OriginalVideo(MediaContainer.MKV))),
        priority = priority,
    )

@OptIn(ExperimentalUuidApi::class)
fun aRule(workspace: Workspace, name: String = "Music", priority: Int = 0, createdAt: Instant = FIXED_INSTANT): Rule =
    aCreateRuleRequest(name = name, priority = priority).let {
        Rule(
            id = RuleId(Uuid.random()),
            name = it.name,
            workspaceId = workspace.id,
            match = it.match,
            metadataTemplate = it.metadataTemplate,
            outputs = it.outputs,
            priority = it.priority,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }

fun aCreateChannelRequest(channelId: String = "UC-1", tags: Set<String> = emptySet()): CreateChannelRequest =
    CreateChannelRequest(
        channelId = ChannelId(channelId),
        extractor = Extractor.YOUTUBE,
        name = "Channel $channelId",
        tags = tags.map(::Tag).toSet(),
    )

@OptIn(ExperimentalUuidApi::class)
fun aChannel(
    workspace: Workspace,
    channelId: String = "UC-1",
    tags: Set<String> = emptySet(),
    createdAt: Instant = FIXED_INSTANT,
): Channel = Channel(
    id = ChannelDirectoryEntryId(Uuid.random()),
    workspaceId = workspace.id,
    channelId = ChannelId(channelId),
    extractor = Extractor.YOUTUBE,
    name = "Channel $channelId",
    tags = tags.map(::Tag).toSet(),
    createdAt = createdAt,
    updatedAt = createdAt,
)
