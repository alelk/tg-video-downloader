package io.github.alelk.tgvd.features.fixtures

import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.api.contract.video.SubtitleTrackDto
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto

const val VIDEO_URL = "https://example.com/video"

fun aMusicVideoMetadata(
    artist: String = "Artist",
    title: String = "Title",
    album: String? = "Album",
    tags: List<String> = listOf("rock"),
) = ResolvedMetadataDto.MusicVideo(artist = artist, title = title, album = album, tags = tags)

fun anOriginalTarget(
    path: String = "/music/Artist - Title.webm",
    container: MediaContainerDto = MediaContainerDto.WEBM,
    maxQuality: VideoQualityDto? = VideoQualityDto.HD_1080,
) = OutputTargetDto(
    path = path,
    format = OutputFormatDto.OriginalVideo(container),
    maxQuality = maxQuality,
    embedMetadata = true,
)

fun anAudioFormat(formatId: String, language: String, tbr: Double = 128.0) = VideoFormatDto(
    formatId = formatId,
    extension = "m4a",
    tbr = tbr,
    vcodec = "none",
    acodec = "mp4a",
    language = language,
)

fun aVideoFormat(formatId: String = "137", height: Int = 1080) = VideoFormatDto(
    formatId = formatId,
    extension = "mp4",
    height = height,
    vcodec = "avc1",
    acodec = "none",
)

fun aPreviewResponse(
    metadata: ResolvedMetadataDto = aMusicVideoMetadata(),
    category: CategoryDto = CategoryDto.MUSIC_VIDEO,
    original: OutputTargetDto = anOriginalTarget(),
    additional: List<OutputTargetDto> = emptyList(),
    formats: List<VideoFormatDto> =
        listOf(aVideoFormat(), anAudioFormat("140-en", "en"), anAudioFormat("140-ru", "ru")),
    subtitles: List<SubtitleTrackDto> = listOf(SubtitleTrackDto("en", automatic = false)),
    defaultSelection: MediaSelectionDto? =
        MediaSelectionDto(audioFormatIds = listOf("140-en"), subtitleLanguages = listOf("en")),
    matchedRule: RuleSummaryDto? = RuleSummaryDto(id = "rule-1", name = "Music"),
    history: List<DownloadHistoryEntryDto> = emptyList(),
    warnings: List<String> = emptyList(),
) = PreviewResponseDto(
    source = VideoSourceDto(url = VIDEO_URL, videoId = "video-1", extractor = "youtube"),
    videoInfo = VideoInfoDto(
        videoId = "video-1",
        extractor = "youtube",
        title = "Source title",
        channelId = "channel-1",
        channelName = "Channel",
        durationSeconds = 185,
        webpageUrl = VIDEO_URL,
        availableFormats = formats,
        subtitleTracks = subtitles,
    ),
    matchedRule = matchedRule,
    metadataSource = MetadataSourceDto.RULE,
    category = category,
    metadata = metadata,
    storagePlan = StoragePlanDto(original = original, additional = additional),
    warnings = warnings,
    previousDownloads = history,
    defaultMediaSelection = defaultSelection,
)

fun aChannelDto(id: String = "dir-1") = ChannelDto(
    id = id,
    workspaceId = "ws-1",
    channelId = "channel-1",
    extractor = "youtube",
    name = "Channel",
    tags = emptyList(),
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)

/** The job the server would answer with for [request]. */
fun aJobDto(request: CreateJobRequestDto) = JobDto(
    id = "job-1",
    status = "PENDING",
    source = request.source,
    videoInfo = request.videoInfo,
    ruleId = request.ruleId,
    category = request.category,
    metadata = request.metadata,
    storagePlan = request.storagePlan,
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)
