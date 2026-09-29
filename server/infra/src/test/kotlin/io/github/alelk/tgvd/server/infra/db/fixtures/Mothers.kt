@file:OptIn(ExperimentalUuidApi::class)

package io.github.alelk.tgvd.server.infra.db.fixtures

import io.github.alelk.tgvd.domain.channel.Channel
import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.ChannelDirectoryEntryId
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.JobId
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.RuleId
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.TelegramUserId
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.common.WorkspaceId
import io.github.alelk.tgvd.domain.common.WorkspaceSlug
import io.github.alelk.tgvd.domain.job.Job
import io.github.alelk.tgvd.domain.job.JobStatus
import io.github.alelk.tgvd.domain.metadata.MetadataSource
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
import io.github.alelk.tgvd.domain.rule.Rule
import io.github.alelk.tgvd.domain.rule.RuleMatch
import io.github.alelk.tgvd.domain.storage.AudioFormat
import io.github.alelk.tgvd.domain.storage.DownloadPolicy
import io.github.alelk.tgvd.domain.storage.ImageFormat
import io.github.alelk.tgvd.domain.storage.MediaContainer
import io.github.alelk.tgvd.domain.storage.OutputFormat
import io.github.alelk.tgvd.domain.storage.OutputRule
import io.github.alelk.tgvd.domain.storage.OutputTarget
import io.github.alelk.tgvd.domain.storage.StoragePlan
import io.github.alelk.tgvd.domain.storage.TrackPreferences
import io.github.alelk.tgvd.domain.storage.VideoEncodeSettings
import io.github.alelk.tgvd.domain.video.MediaSelection
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.github.alelk.tgvd.domain.video.VideoSource
import io.github.alelk.tgvd.domain.workspace.Workspace
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Object mothers for repository tests. Every value is deterministic except ids; instants carry at
 * most microseconds (PostgreSQL `timestamptz` precision) so a round-trip compares equal.
 */
val T0: Instant = Instant.parse("2026-01-02T03:04:05.123456Z")
val T1: Instant = Instant.parse("2026-01-02T04:05:06.654321Z")
val T2: Instant = Instant.parse("2026-01-02T05:06:07.000001Z")

private val slugCounter = java.util.concurrent.atomic.AtomicInteger()

fun uniqueSlug(prefix: String = "ws"): WorkspaceSlug = WorkspaceSlug("$prefix-${slugCounter.incrementAndGet()}-x")

fun aWorkspace(slug: WorkspaceSlug = uniqueSlug(), name: String = "Home media") =
    Workspace(id = WorkspaceId(Uuid.random()), slug = slug, name = name, createdAt = T0)

fun aFullVideoFormat(formatId: String = "137") = VideoInfo.Format(
    formatId = formatId,
    extension = "mp4",
    width = 1920,
    height = 1080,
    fps = 29.97,
    tbr = 4400.5,
    vcodec = "avc1.640028",
    acodec = "none",
    formatNote = "1080p",
    filesize = 123_456_789L,
    filesizeApprox = 123_000_000L,
    language = "ru",
    languagePreference = 10,
    audioChannels = 2,
    audioTrackName = "Russian original",
    isOriginalAudio = true,
)

fun anAudioFormat(formatId: String, language: String) =
    VideoInfo.Format(formatId = formatId, extension = "m4a", acodec = "mp4a.40.2", vcodec = "none", language = language)

fun aFullVideoInfo(videoId: String = "dQw4w9WgXcQ") = VideoInfo(
    videoId = VideoId(videoId),
    extractor = Extractor.YOUTUBE,
    title = "Never Gonna Give You Up",
    channelId = ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
    channelName = "Rick Astley",
    uploadDate = LocalDate("2009-10-25"),
    duration = 212.seconds,
    webpageUrl = Url("https://www.youtube.com/watch?v=$videoId"),
    thumbnails =
    listOf(
        VideoInfo.Thumbnail(Url("https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"), 1280, 720),
        VideoInfo.Thumbnail(Url("https://i.ytimg.com/vi/$videoId/default.jpg"), null, null),
    ),
    description = "The official video",
    availableFormats =
    listOf(
        aFullVideoFormat(),
        anAudioFormat("140-0", "en"),
        VideoInfo.Format(formatId = "18", extension = "mp4"),
    ),
    actualFormat = aFullVideoFormat("137+140"),
    subtitleTracks =
    listOf(
        VideoInfo.SubtitleTrack("en", automatic = false, name = "English"),
        VideoInfo.SubtitleTrack("ru", automatic = true),
    ),
)

fun aFullStoragePlan() = StoragePlan(
    original =
    OutputTarget(
        path = FilePath("/media/music/Rick Astley/Never Gonna Give You Up [dQw4w9WgXcQ].mkv"),
        format = OutputFormat.OriginalVideo(MediaContainer.MKV),
        maxQuality = DownloadPolicy.VideoQuality.HD_1080,
        embedThumbnail = true,
        embedMetadata = true,
        embedSubtitles = true,
    ),
    additional =
    listOf(
        OutputTarget(
            path = FilePath("/media/converted/Never Gonna Give You Up.mp4"),
            format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
            maxQuality = DownloadPolicy.VideoQuality.HD_720,
            encodeSettings =
            VideoEncodeSettings(
                codec = VideoEncodeSettings.VideoCodec.H265,
                hwAccel = VideoEncodeSettings.HwAccel.VAAPI,
                preset = VideoEncodeSettings.EncodePreset.SLOW,
                crf = 20,
                audioBitrate = "256k",
                audioCodec = "aac",
            ),
            normalizeAudio = true,
        ),
        OutputTarget(
            path = FilePath("/media/audio/Never Gonna Give You Up.mp3"),
            format = OutputFormat.Audio(AudioFormat.MP3),
            maxQuality = DownloadPolicy.VideoQuality.SD_480,
        ),
        OutputTarget(
            path = FilePath("/media/covers/Never Gonna Give You Up.webp"),
            format = OutputFormat.Thumbnail(ImageFormat.WEBP),
            maxQuality = DownloadPolicy.VideoQuality.BEST,
        ),
    ),
)

fun aJob(workspaceId: WorkspaceId, videoId: String, ruleId: RuleId? = null) = Job(
    id = JobId(Uuid.random()),
    workspaceId = workspaceId,
    createdBy = TelegramUserId(42),
    source =
    VideoSource(
        url = Url("https://youtu.be/$videoId"),
        videoId = VideoId(videoId),
        extractor = Extractor.YOUTUBE,
    ),
    videoInfo = aFullVideoInfo(videoId),
    metadata =
    ResolvedMetadata.MusicVideo(
        artist = "Rick Astley",
        title = "Never Gonna Give You Up",
        album = "Whenever You Need Somebody",
        releaseDate = LocalDate("1987-07-27"),
        tags = listOf("pop", "80s"),
        comment = "classic",
    ),
    metadataSource = MetadataSource.LLM,
    storagePlan = aFullStoragePlan(),
    ruleId = ruleId,
    mediaSelection = MediaSelection(audioFormatIds = listOf("140-0"), subtitleLanguages = listOf("en")),
    status = JobStatus.PENDING,
    attempt = 0,
    createdAt = T0,
    updatedAt = T0,
)

fun aRule(workspaceId: WorkspaceId, name: String = "Music videos") = Rule(
    id = RuleId(Uuid.random()),
    name = name,
    workspaceId = workspaceId,
    match =
    RuleMatch.AllOf(
        listOf(
            RuleMatch.ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
            RuleMatch.AnyOf(
                listOf(
                    RuleMatch.ChannelName("Rick Astley", ignoreCase = false),
                    RuleMatch.TitleRegex("(?i)official"),
                    RuleMatch.UrlRegex("youtube\\.com"),
                    RuleMatch.CategoryEquals(Category.MUSIC_VIDEO),
                    RuleMatch.HasTag(Tag("music")),
                ),
            ),
        ),
    ),
    metadataTemplate =
    MetadataTemplate.MusicVideo(
        artistOverride = "Rick Astley",
        artistPattern = "^(.+?) -",
        titleOverride = null,
        titlePattern = "- (.+)$",
        defaultTags = listOf("music"),
    ),
    downloadPolicy =
    DownloadPolicy(
        maxQuality = DownloadPolicy.VideoQuality.HD_1080,
        downloadSubtitles = true,
        subtitleLanguages = listOf("en", "ru"),
        writeThumbnail = true,
        audioLanguages = listOf("en"),
    ),
    outputs =
    listOf(
        OutputRule(
            pathTemplate = "/media/music/{artist}/{title} [{videoId}].{ext}",
            format = OutputFormat.OriginalVideo(MediaContainer.MKV),
            maxQuality = DownloadPolicy.VideoQuality.HD_1080,
            embedThumbnail = true,
            embedMetadata = true,
        ),
        OutputRule(
            pathTemplate = "/media/audio/{artist}/{title}.{ext}",
            format = OutputFormat.Audio(AudioFormat.FLAC),
            encodeSettings = VideoEncodeSettings.HIGH_QUALITY,
            normalizeAudio = true,
        ),
    ),
    enabled = true,
    priority = 10,
    createdAt = T0,
    updatedAt = T0,
)

fun aChannel(
    workspaceId: WorkspaceId,
    channelId: String = "UCuAXFkgsw1L7xaCfnd5JJOw",
    tags: Set<String> = setOf("music", "pop"),
) = Channel(
    id = ChannelDirectoryEntryId(Uuid.random()),
    workspaceId = workspaceId,
    channelId = ChannelId(channelId),
    extractor = Extractor.YOUTUBE,
    name = "Rick Astley",
    tags = tags.map { Tag(it) }.toSet(),
    metadataOverrides =
    MetadataTemplate.SeriesEpisode(
        seriesNameOverride = "Rick Rolls",
        seasonPattern = "S(\\d+)",
        episodePattern = "E(\\d+)",
        titleOverride = "Roll",
        titlePattern = "\\| (.+)$",
        defaultTags = listOf("series"),
    ),
    notes = "The channel",
    trackPreferences = TrackPreferences(
        audioLanguages = listOf("en"),
        downloadSubtitles = false,
        subtitleLanguages = listOf("ru"),
    ),
    createdAt = T0,
    updatedAt = T0,
)
