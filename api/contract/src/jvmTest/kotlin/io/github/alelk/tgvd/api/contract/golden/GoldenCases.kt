package io.github.alelk.tgvd.api.contract.golden

import io.github.alelk.tgvd.api.contract.channel.ChannelDto
import io.github.alelk.tgvd.api.contract.common.ApiErrorDto
import io.github.alelk.tgvd.api.contract.common.CategoryDto
import io.github.alelk.tgvd.api.contract.job.JobDto
import io.github.alelk.tgvd.api.contract.job.JobErrorDto
import io.github.alelk.tgvd.api.contract.job.JobProgressDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataSourceDto
import io.github.alelk.tgvd.api.contract.metadata.MetadataTemplateDto
import io.github.alelk.tgvd.api.contract.metadata.ResolvedMetadataDto
import io.github.alelk.tgvd.api.contract.preview.DownloadHistoryEntryDto
import io.github.alelk.tgvd.api.contract.preview.PreviewResponseDto
import io.github.alelk.tgvd.api.contract.preview.UserOverridesDto
import io.github.alelk.tgvd.api.contract.rule.RuleDto
import io.github.alelk.tgvd.api.contract.rule.RuleMatchDto
import io.github.alelk.tgvd.api.contract.rule.RuleSummaryDto
import io.github.alelk.tgvd.api.contract.storage.AudioFormatDto
import io.github.alelk.tgvd.api.contract.storage.DownloadPolicyDto
import io.github.alelk.tgvd.api.contract.storage.EncodePresetDto
import io.github.alelk.tgvd.api.contract.storage.HwAccelDto
import io.github.alelk.tgvd.api.contract.storage.ImageFormatDto
import io.github.alelk.tgvd.api.contract.storage.MediaContainerDto
import io.github.alelk.tgvd.api.contract.storage.OutputFormatDto
import io.github.alelk.tgvd.api.contract.storage.OutputRuleDto
import io.github.alelk.tgvd.api.contract.storage.OutputTargetDto
import io.github.alelk.tgvd.api.contract.storage.StoragePlanDto
import io.github.alelk.tgvd.api.contract.storage.TrackPreferencesDto
import io.github.alelk.tgvd.api.contract.storage.VideoCodecDto
import io.github.alelk.tgvd.api.contract.storage.VideoEncodeSettingsDto
import io.github.alelk.tgvd.api.contract.storage.VideoQualityDto
import io.github.alelk.tgvd.api.contract.system.ProxySettingsDto
import io.github.alelk.tgvd.api.contract.system.SystemSettingsDto
import io.github.alelk.tgvd.api.contract.system.YtDlpExtractorOverrideDto
import io.github.alelk.tgvd.api.contract.system.YtDlpSettingsDto
import io.github.alelk.tgvd.api.contract.video.MediaSelectionDto
import io.github.alelk.tgvd.api.contract.video.SubtitleTrackDto
import io.github.alelk.tgvd.api.contract.video.ThumbnailDto
import io.github.alelk.tgvd.api.contract.video.VideoFormatDto
import io.github.alelk.tgvd.api.contract.video.VideoInfoDto
import io.github.alelk.tgvd.api.contract.video.VideoSourceDto
import io.github.alelk.tgvd.api.contract.workspace.WorkspaceDto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One golden file: `src/jvmTest/resources/golden/<name>.json` holds [value] as `apiJson` writes it. */
class GoldenCase<T>(val name: String, val serializer: KSerializer<T>, val value: T)

/**
 * Object mothers for the golden files. Between them the cases hold every variant of every sealed
 * hierarchy of the wire (RuleMatchDto ×8, MetadataTemplateDto ×3, ResolvedMetadataDto ×3,
 * UserOverridesDto ×3, OutputFormatDto ×4 kinds) and every enum value that reaches these DTOs.
 */
object GoldenCases {
    private val source =
        VideoSourceDto(url = "https://youtu.be/dQw4w9WgXcQ", videoId = "dQw4w9WgXcQ", extractor = "youtube")

    private val videoFormat =
        VideoFormatDto(
            formatId = "137",
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
            language = "en",
            languagePreference = 10,
            audioChannels = 2,
            audioTrackName = "English original",
            isOriginalAudio = true,
        )

    private val fullVideoInfo =
        VideoInfoDto(
            videoId = "dQw4w9WgXcQ",
            extractor = "youtube",
            title = "Never Gonna Give You Up",
            channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
            channelName = "Rick Astley",
            uploadDate = "2009-10-25",
            durationSeconds = 212,
            webpageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbnails =
            listOf(
                ThumbnailDto("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", 1280, 720),
                ThumbnailDto("https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg"),
            ),
            description = "The official video",
            availableFormats =
            listOf(
                videoFormat,
                VideoFormatDto(
                    formatId = "140",
                    extension = "m4a",
                    vcodec = "none",
                    acodec = "mp4a.40.2",
                    language = "en",
                ),
            ),
            actualFormat = videoFormat.copy(formatId = "137+140"),
            subtitleTracks = listOf(
                SubtitleTrackDto("en", automatic = false, name = "English"),
                SubtitleTrackDto("ru", automatic = true),
            ),
        )

    private val minimalVideoInfo =
        VideoInfoDto(
            videoId = "rt-1",
            extractor = "rutube",
            title = "Clip",
            channelId = "rt-channel",
            channelName = "RuTube",
            durationSeconds = 0,
            webpageUrl = "https://rutube.ru/video/rt-1/",
        )

    private val encodeSettings =
        VideoEncodeSettingsDto(
            codec = VideoCodecDto.H265,
            hwAccel = HwAccelDto.VAAPI,
            preset = EncodePresetDto.SLOW,
            crf = 20,
            audioBitrate = "256k",
            audioCodec = "aac",
        )

    /** Every OutputFormatDto kind, every quality. */
    private val fullStoragePlan =
        StoragePlanDto(
            original =
            OutputTargetDto(
                path = "/media/music/Rick Astley/Never Gonna Give You Up.mkv",
                format = OutputFormatDto.OriginalVideo(MediaContainerDto.MKV),
                maxQuality = VideoQualityDto.BEST,
                embedThumbnail = true,
                embedMetadata = true,
                embedSubtitles = true,
            ),
            additional =
            listOf(
                OutputTargetDto(
                    path = "/media/converted/Never Gonna Give You Up.mp4",
                    format = OutputFormatDto.ConvertedVideo(MediaContainerDto.MP4),
                    maxQuality = VideoQualityDto.HD_1080,
                    encodeSettings = encodeSettings,
                    normalizeAudio = true,
                ),
                OutputTargetDto(
                    path = "/media/audio/Never Gonna Give You Up.m4a",
                    format = OutputFormatDto.Audio(AudioFormatDto.M4A),
                    maxQuality = VideoQualityDto.HD_720,
                ),
                OutputTargetDto(
                    path = "/media/covers/Never Gonna Give You Up.png",
                    format = OutputFormatDto.Thumbnail(ImageFormatDto.PNG),
                    maxQuality = VideoQualityDto.SD_480,
                ),
            ),
        )

    private val minimalStoragePlan =
        StoragePlanDto(
            OutputTargetDto(path = "/media/clip.webm", format = OutputFormatDto.OriginalVideo(MediaContainerDto.WEBM)),
        )

    private val jobMusicVideo =
        JobDto(
            id = "0192f0c1-2b3c-7d4e-8f90-a1b2c3d4e5f6",
            status = "downloading",
            source = source,
            videoInfo = fullVideoInfo,
            ruleId = "0192f0c1-0000-7000-8000-000000000001",
            category = CategoryDto.MUSIC_VIDEO,
            metadata =
            ResolvedMetadataDto.MusicVideo(
                artist = "Rick Astley",
                title = "Never Gonna Give You Up",
                album = "Whenever You Need Somebody",
                releaseDate = "1987-07-27",
                tags = listOf("pop", "80s"),
                comment = "classic",
            ),
            storagePlan = fullStoragePlan,
            progress = JobProgressDto(phase = "download", percent = 42, message = "2.1MiB/s"),
            error = JobErrorDto(code = "ERROR", message = "transient", details = "retry later", retryable = true),
            attempt = 2,
            createdBy = "42",
            createdAt = "2026-01-02T03:04:05.123456Z",
            updatedAt = "2026-01-02T03:05:00Z",
            startedAt = "2026-01-02T03:04:06Z",
            finishedAt = "2026-01-02T03:10:00Z",
        )

    private val jobSeriesEpisode =
        jobMusicVideo.copy(
            status = "completed",
            category = CategoryDto.SERIES_EPISODE,
            metadata =
            ResolvedMetadataDto.SeriesEpisode(
                seriesName = "The Show",
                season = "02",
                episode = "05",
                title = "Pilot",
                releaseDate = "2020-01-31",
                tags = listOf("tv"),
                comment = "S02E05",
            ),
            progress = null,
            error = null,
        )

    private val jobOtherMinimal =
        JobDto(
            id = "0192f0c1-2b3c-7d4e-8f90-000000000003",
            status = "pending",
            source = VideoSourceDto(url = "https://rutube.ru/video/rt-1/", videoId = "rt-1", extractor = "rutube"),
            videoInfo = minimalVideoInfo,
            category = CategoryDto.OTHER,
            metadata = ResolvedMetadataDto.Other(title = "Clip"),
            storagePlan = minimalStoragePlan,
            createdAt = "2026-01-02T03:04:05Z",
            updatedAt = "2026-01-02T03:04:05Z",
        )

    /** Every RuleMatchDto variant, every CategoryDto inside category-equals. */
    private val everyRuleMatch =
        RuleMatchDto.AllOf(
            listOf(
                RuleMatchDto.ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
                RuleMatchDto.ChannelName("Rick Astley"),
                RuleMatchDto.ChannelName("rick astley", ignoreCase = false),
                RuleMatchDto.AnyOf(
                    listOf(
                        RuleMatchDto.TitleRegex("(?i)official"),
                        RuleMatchDto.UrlRegex("youtube\\.com"),
                        RuleMatchDto.CategoryEquals(CategoryDto.MUSIC_VIDEO),
                        RuleMatchDto.CategoryEquals(CategoryDto.SERIES_EPISODE),
                        RuleMatchDto.CategoryEquals(CategoryDto.OTHER),
                        RuleMatchDto.HasTag("music"),
                    ),
                ),
            ),
        )

    private val ruleMusicVideo =
        RuleDto(
            id = "0192f0c1-0000-7000-8000-000000000001",
            name = "Music videos",
            enabled = true,
            priority = 10,
            match = everyRuleMatch,
            category = CategoryDto.MUSIC_VIDEO,
            metadataTemplate =
            MetadataTemplateDto.MusicVideo(
                artistOverride = "Rick Astley",
                artistPattern = "^(.+?) -",
                titleOverride = "Never Gonna Give You Up",
                titlePattern = "- (.+)$",
                defaultTags = listOf("music"),
            ),
            downloadPolicy =
            DownloadPolicyDto(
                maxQuality = VideoQualityDto.HD_1080,
                downloadSubtitles = true,
                subtitleLanguages = listOf("en", "ru"),
                writeThumbnail = true,
                audioLanguages = listOf("en"),
            ),
            outputs =
            listOf(
                OutputRuleDto(
                    pathTemplate = "/media/music/{artist}/{title} [{videoId}].{ext}",
                    format = OutputFormatDto.OriginalVideo(MediaContainerDto.MKV),
                    maxQuality = VideoQualityDto.BEST,
                    embedThumbnail = true,
                    embedMetadata = true,
                    embedSubtitles = true,
                ),
                OutputRuleDto(
                    pathTemplate = "/media/converted/{title}.avi",
                    format = OutputFormatDto.ConvertedVideo(MediaContainerDto.AVI),
                    maxQuality = VideoQualityDto.HD_720,
                    encodeSettings = encodeSettings,
                    normalizeAudio = true,
                ),
                OutputRuleDto(
                    pathTemplate = "/media/audio/{title}.flac",
                    format = OutputFormatDto.Audio(AudioFormatDto.FLAC),
                ),
                OutputRuleDto(
                    pathTemplate = "/media/covers/{title}.webp",
                    format = OutputFormatDto.Thumbnail(ImageFormatDto.WEBP),
                ),
            ),
            createdAt = "2026-01-02T03:04:05.123456Z",
            updatedAt = "2026-01-03T00:00:00Z",
        )

    private val ruleSeriesEpisode =
        ruleMusicVideo.copy(
            name = "Series",
            enabled = false,
            priority = -1,
            match = RuleMatchDto.AnyOf(listOf(RuleMatchDto.HasTag("tv"))),
            category = CategoryDto.SERIES_EPISODE,
            metadataTemplate =
            MetadataTemplateDto.SeriesEpisode(
                seriesNameOverride = "The Show",
                seasonPattern = "S(\\d+)",
                episodePattern = "E(\\d+)",
                titleOverride = "Pilot",
                titlePattern = "\\| (.+)$",
                defaultTags = listOf("tv"),
            ),
            downloadPolicy = DownloadPolicyDto(
                maxQuality = VideoQualityDto.SD_480,
                downloadSubtitles = false,
                audioLanguages = emptyList(),
            ),
            outputs = listOf(
                OutputRuleDto(
                    pathTemplate = "/tv/{title}.mov",
                    format = OutputFormatDto.ConvertedVideo(MediaContainerDto.MOV),
                ),
            ),
        )

    private val ruleOtherMinimal =
        RuleDto(
            id = "0192f0c1-0000-7000-8000-000000000003",
            name = "Anything",
            enabled = true,
            priority = 0,
            match = RuleMatchDto.ChannelId("UC1"),
            category = CategoryDto.OTHER,
            metadataTemplate = MetadataTemplateDto.Other(),
            downloadPolicy = DownloadPolicyDto(),
            outputs =
            listOf(
                OutputRuleDto(
                    pathTemplate = "/media/{title}.mp3",
                    format = OutputFormatDto.Audio(AudioFormatDto.MP3),
                ),
                OutputRuleDto(
                    pathTemplate = "/media/{title}.opus",
                    format = OutputFormatDto.Audio(AudioFormatDto.OPUS),
                ),
                OutputRuleDto(
                    pathTemplate = "/media/{title}.wav",
                    format = OutputFormatDto.Audio(AudioFormatDto.WAV),
                ),
                OutputRuleDto(
                    pathTemplate = "/media/{title}.jpg",
                    format = OutputFormatDto.Thumbnail(ImageFormatDto.JPG),
                ),
            ),
            createdAt = "2026-01-02T03:04:05Z",
            updatedAt = "2026-01-02T03:04:05Z",
        )

    private val channelFull =
        ChannelDto(
            id = "0192f0c1-0000-7000-8000-00000000000c",
            workspaceId = "0192f0c1-0000-7000-8000-0000000000aa",
            channelId = "UCuAXFkgsw1L7xaCfnd5JJOw",
            extractor = "youtube",
            name = "Rick Astley",
            tags = listOf("music", "pop"),
            metadataOverrides = MetadataTemplateDto.Other(
                titleOverride = "Clip",
                titlePattern = "(.+)",
                defaultTags = listOf("misc"),
            ),
            notes = "The channel",
            trackPreferences = TrackPreferencesDto(
                audioLanguages = listOf("en"),
                downloadSubtitles = true,
                subtitleLanguages = listOf("ru"),
            ),
            createdAt = "2026-01-02T03:04:05Z",
            updatedAt = "2026-01-02T03:04:06Z",
        )

    private val channelMinimal =
        ChannelDto(
            id = "0192f0c1-0000-7000-8000-00000000000d",
            workspaceId = "0192f0c1-0000-7000-8000-0000000000aa",
            channelId = "rt-channel",
            extractor = "rutube",
            name = "RuTube",
            tags = emptyList(),
            createdAt = "2026-01-02T03:04:05Z",
            updatedAt = "2026-01-02T03:04:05Z",
        )

    private val previewMusicVideo =
        PreviewResponseDto(
            source = source,
            videoInfo = fullVideoInfo,
            matchedRule = RuleSummaryDto(id = "0192f0c1-0000-7000-8000-000000000001", name = "Music videos"),
            metadataSource = MetadataSourceDto.RULE,
            category = CategoryDto.MUSIC_VIDEO,
            metadata = jobMusicVideo.metadata,
            storagePlan = fullStoragePlan,
            appliedOverrides = UserOverridesDto.MusicVideo(
                artist = "Rick Astley",
                title = "Never Gonna Give You Up",
                album = "Album",
            ),
            warnings = listOf("Low quality source"),
            previousDownloads =
            listOf(
                DownloadHistoryEntryDto(
                    jobId = "0192f0c1-2b3c-7d4e-8f90-a1b2c3d4e5f6",
                    status = "COMPLETED",
                    finishedAt = "2026-01-02T03:10:00Z",
                    maxQuality = VideoQualityDto.HD_1080,
                    formatSummary = "mkv",
                ),
                DownloadHistoryEntryDto(
                    jobId = "0192f0c1-2b3c-7d4e-8f90-000000000009",
                    status = "FAILED",
                    formatSummary = "mp4",
                ),
            ),
            defaultMediaSelection = MediaSelectionDto(audioFormatIds = listOf("140"), subtitleLanguages = listOf("en")),
        )

    private val previewSeriesEpisode =
        previewMusicVideo.copy(
            matchedRule = RuleSummaryDto(id = "0192f0c1-0000-7000-8000-000000000002", name = null),
            metadataSource = MetadataSourceDto.LLM,
            category = CategoryDto.SERIES_EPISODE,
            metadata = jobSeriesEpisode.metadata,
            appliedOverrides = UserOverridesDto.SeriesEpisode(
                seriesName = "The Show",
                season = "02",
                episode = "05",
                title = "Pilot",
            ),
            warnings = emptyList(),
            previousDownloads = emptyList(),
            defaultMediaSelection = MediaSelectionDto(audioFormatIds = null, subtitleLanguages = emptyList()),
        )

    private val previewOther =
        PreviewResponseDto(
            source = VideoSourceDto(url = "https://rutube.ru/video/rt-1/", videoId = "rt-1", extractor = "rutube"),
            videoInfo = minimalVideoInfo,
            metadataSource = MetadataSourceDto.FALLBACK,
            category = CategoryDto.OTHER,
            metadata = ResolvedMetadataDto.Other(
                title = "Clip",
                releaseDate = "2024-12-01",
                tags = listOf("a"),
                comment = "c",
            ),
            storagePlan = minimalStoragePlan,
            appliedOverrides = UserOverridesDto.Other(title = "Clip"),
        )

    private val previewMinimal =
        previewOther.copy(metadata = ResolvedMetadataDto.Other(title = "Clip"), appliedOverrides = null)

    private val systemSettingsFull =
        SystemSettingsDto(
            ytDlp =
            YtDlpSettingsDto(
                cookiesFromBrowser = "firefox",
                cookiesContent = "# Netscape HTTP Cookie File",
                cookiesFile = "/data/cookies.txt",
                legacyServerConnect = true,
                noCheckCertificate = true,
                preferredFormats = "bestvideo[height<=1080]+bestaudio/best",
                formatSort = "res,tbr,fps",
                checkFormats = false,
                preferredAudioLanguages = listOf("en", "de"),
                maxAdditionalAudioTracks = 4,
                originalAudioLanguage = "ru",
                rateLimit = "5M",
                sleepInterval = 2,
                maxSleepInterval = 9,
                writeSubs = false,
                writeAutoSubs = false,
                preferredSubtitleLanguages = listOf("de"),
                subLangs = "de,fr",
                embedSubs = true,
                sleepSubtitles = null,
                concurrentFragments = 8,
                socketTimeout = 60,
                youtubePlayerClient = "web",
                extractorArgs = "vk:nocheckcertificate=1",
                sponsorBlockRemove = "sponsor,selfpromo",
                userAgent = "Mozilla/5.0",
                extractorOverrides =
                mapOf(
                    "rutube" to YtDlpExtractorOverrideDto(legacyServerConnect = true, proxyEnabled = false),
                    "vk" to YtDlpExtractorOverrideDto(noCheckCertificate = true),
                ),
            ),
            proxy = ProxySettingsDto(
                enabled = true,
                type = "SOCKS5",
                host = "10.0.0.1",
                port = 1080,
                username = "user",
                password = "secret",
            ),
        )

    private val apiErrorWithDetails =
        ApiErrorDto(
            ApiErrorDto.ErrorDetail(
                code = "VALIDATION_ERROR",
                message = "Invalid slug",
                correlationId = "3f1c2d4e-5b6a-4c7d-8e9f-0a1b2c3d4e5f",
                details =
                buildJsonObject {
                    put("field", "slug")
                    put("max", 50)
                    put("nothing", JsonNull)
                    put("list", buildJsonArray { add(JsonPrimitive(true)) })
                },
            ),
        )

    private val apiErrorMinimal =
        ApiErrorDto(
            ApiErrorDto.ErrorDetail(code = "NOT_FOUND", message = "Job not found: x", correlationId = "unknown"),
        )

    val all: List<GoldenCase<*>> =
        listOf(
            GoldenCase("job-music-video", JobDto.serializer(), jobMusicVideo),
            GoldenCase("job-series-episode", JobDto.serializer(), jobSeriesEpisode),
            GoldenCase("job-other-minimal", JobDto.serializer(), jobOtherMinimal),
            GoldenCase("rule-music-video", RuleDto.serializer(), ruleMusicVideo),
            GoldenCase("rule-series-episode", RuleDto.serializer(), ruleSeriesEpisode),
            GoldenCase("rule-other-minimal", RuleDto.serializer(), ruleOtherMinimal),
            GoldenCase("channel-full", ChannelDto.serializer(), channelFull),
            GoldenCase("channel-minimal", ChannelDto.serializer(), channelMinimal),
            GoldenCase("preview-response-music-video", PreviewResponseDto.serializer(), previewMusicVideo),
            GoldenCase("preview-response-series-episode", PreviewResponseDto.serializer(), previewSeriesEpisode),
            GoldenCase("preview-response-other", PreviewResponseDto.serializer(), previewOther),
            GoldenCase("preview-response-minimal", PreviewResponseDto.serializer(), previewMinimal),
            GoldenCase("system-settings-full", SystemSettingsDto.serializer(), systemSettingsFull),
            GoldenCase("system-settings-defaults", SystemSettingsDto.serializer(), SystemSettingsDto()),
            GoldenCase(
                "workspace",
                WorkspaceDto.serializer(),
                WorkspaceDto(
                    id = "0192f0c1-0000-7000-8000-0000000000aa",
                    slug = "home-media",
                    name = "Home media",
                    role = "owner",
                    createdAt = "2026-01-02T03:04:05Z",
                ),
            ),
            GoldenCase("api-error-with-details", ApiErrorDto.serializer(), apiErrorWithDetails),
            GoldenCase("api-error-minimal", ApiErrorDto.serializer(), apiErrorMinimal),
        )
}
