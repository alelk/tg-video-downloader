package io.github.alelk.tgvd.server.route

import io.github.alelk.tgvd.api.contract.channel.CreateChannelDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.job.CreateJobRequestDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.preview.PreviewRequestDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.rule.CreateRuleRequestDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto
import io.github.alelk.tgvd.api.contract.workspace.CreateWorkspaceRequestDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.video.VideoInfo
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.seconds

const val WORKSPACES = "/api/v1/workspaces"

fun videoUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

/** A video as the (fake) yt-dlp reports it: one video-only format, two audio tracks, two subtitle tracks. */
fun aVideoInfo(videoId: String, channelId: String = "UC-route-tests", channelName: String = "Route Tests") = VideoInfo(
    videoId = VideoId(videoId),
    extractor = Extractor.YOUTUBE,
    title = "Video $videoId",
    channelId = ChannelId(channelId),
    channelName = channelName,
    uploadDate = LocalDate("2024-05-06"),
    duration = 125.seconds,
    webpageUrl = Url(videoUrl(videoId)),
    thumbnails = listOf(VideoInfo.Thumbnail(Url("https://i.ytimg.com/vi/$videoId/hq.jpg"), 480, 360)),
    description = "Description of $videoId",
    availableFormats =
    listOf(
        VideoInfo.Format(
            formatId = "137",
            extension = "mp4",
            width = 1920,
            height = 1080,
            vcodec = "avc1",
            acodec = "none",
        ),
        VideoInfo.Format(
            formatId = "140-0",
            extension = "m4a",
            vcodec = "none",
            acodec = "mp4a",
            language = "en",
            isOriginalAudio = true,
        ),
        VideoInfo.Format(formatId = "140-1", extension = "m4a", vcodec = "none", acodec = "mp4a", language = "de"),
    ),
    subtitleTracks = listOf(
        VideoInfo.SubtitleTrack("en", automatic = false),
        VideoInfo.SubtitleTrack("ru", automatic = true),
    ),
)

/** The job the Mini App creates from a preview it just received. */
fun PreviewResponseDto.toCreateJobRequest() = CreateJobRequestDto(
    source = source,
    ruleId = matchedRule?.id,
    category = category,
    videoInfo = videoInfo,
    metadata = metadata,
    metadataSource = metadataSource,
    storagePlan = storagePlan,
)

fun aCreateRuleRequest(name: String = "Route test rule", channelId: String = "UC-route-tests") = CreateRuleRequestDto(
    name = name,
    enabled = true,
    priority = 5,
    match = RuleMatchDto.ChannelId(channelId),
    category = CategoryDto.MUSIC_VIDEO,
    metadataTemplate = MetadataTemplateDto.MusicVideo(artistOverride = "Route Artist", defaultTags = listOf("music")),
    downloadPolicy = DownloadPolicyDto(
        audioLanguages = listOf("de"),
        subtitleLanguages = listOf("en"),
        downloadSubtitles = true,
    ),
    outputs =
    listOf(
        OutputRuleDto(
            pathTemplate = "/media/music/{artist}/{title} [{videoId}].{ext}",
            format = OutputFormatDto.OriginalVideo(MediaContainerDto.MKV),
        ),
    ),
)

fun aCreateChannelDto(channelId: String = "UC-route-tests", tags: List<String> = listOf("music")) = CreateChannelDto(
    channelId = channelId,
    extractor = "youtube",
    name = "Channel $channelId",
    tags = tags,
    metadataOverrides = MetadataTemplateDto.Other(defaultTags = listOf("from-channel")),
    notes = "notes",
    trackPreferences = TrackPreferencesDto(audioLanguages = emptyList()),
)

/** `POST /workspaces` as [initData] (the raw `X-Telegram-Init-Data` value). */
suspend fun HttpClient.createWorkspace(
    slug: String,
    initData: String = "dev",
    expected: HttpStatusCode = HttpStatusCode.Created,
): WorkspaceDto {
    val response =
        post(WORKSPACES) {
            headers.append(INIT_DATA_HEADER, initData)
            jsonBody(CreateWorkspaceRequestDto(slug = slug, name = "Workspace $slug"))
        }
    response.status shouldBe expected
    return response.body()
}

/** Preview [videoId] in [slug] and create a job from the result, as the Mini App does. */
suspend fun HttpClient.createJob(slug: String, videoId: String, initData: String = "dev"): JobDto {
    val preview =
        post("$WORKSPACES/$slug/preview") {
            headers.append(INIT_DATA_HEADER, initData)
            jsonBody(PreviewRequestDto(url = videoUrl(videoId)))
        }
    preview.status shouldBe HttpStatusCode.OK
    val response =
        post("$WORKSPACES/$slug/jobs") {
            headers.append(INIT_DATA_HEADER, initData)
            jsonBody(preview.body<PreviewResponseDto>().toCreateJobRequest())
        }
    response.status shouldBe HttpStatusCode.Created
    return response.body()
}

suspend fun HttpClient.createRule(
    slug: String,
    request: CreateRuleRequestDto = aCreateRuleRequest(),
    initData: String = "dev",
): RuleDto {
    val response =
        post("$WORKSPACES/$slug/rules") {
            headers.append(INIT_DATA_HEADER, initData)
            jsonBody(request)
        }
    response.status shouldBe HttpStatusCode.Created
    return response.body()
}
