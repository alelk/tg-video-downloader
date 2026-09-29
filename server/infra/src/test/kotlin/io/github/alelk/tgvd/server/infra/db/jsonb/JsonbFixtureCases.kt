package io.github.alelk.tgvd.server.infra.db.jsonb

import io.github.alelk.tgvd.domain.common.Category
import io.github.alelk.tgvd.domain.common.ChannelId
import io.github.alelk.tgvd.domain.common.Extractor
import io.github.alelk.tgvd.domain.common.FilePath
import io.github.alelk.tgvd.domain.common.LocalDate
import io.github.alelk.tgvd.domain.common.Tag
import io.github.alelk.tgvd.domain.common.Url
import io.github.alelk.tgvd.domain.common.VideoId
import io.github.alelk.tgvd.domain.job.JobPhase
import io.github.alelk.tgvd.domain.metadata.MetadataTemplate
import io.github.alelk.tgvd.domain.metadata.ResolvedMetadata
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
import io.github.alelk.tgvd.server.infra.config.ProxyConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpConfig
import io.github.alelk.tgvd.server.infra.config.YtDlpExtractorOverride
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What each frozen JSONB fixture must read back as. The files under
 * `src/test/resources/jsonb-fixtures/<table>.<column>/<variant>.json` were written ONCE (stage 01.4)
 * by the serializers of that time from exactly these values; they are never regenerated. A new
 * stored shape gets a NEW file (and a new entry here), old files stay as they are — they are the
 * rows that already live in deployed databases.
 */
object JsonbFixtureCases {
    val ruleMatch: Map<String, RuleMatch> =
        mapOf(
            "channel-id" to RuleMatch.ChannelId("UCuAXFkgsw1L7xaCfnd5JJOw"),
            "channel-name" to RuleMatch.ChannelName("Rick Astley", ignoreCase = false),
            "channel-name-default-ignore-case" to RuleMatch.ChannelName("rick astley"),
            "title-regex" to RuleMatch.TitleRegex("(?i)official (music )?video"),
            "url-regex" to RuleMatch.UrlRegex("^https://(www\\.)?youtube\\.com/"),
            "category-equals-music-video" to RuleMatch.CategoryEquals(Category.MUSIC_VIDEO),
            "category-equals-series-episode" to RuleMatch.CategoryEquals(Category.SERIES),
            "category-equals-other" to RuleMatch.CategoryEquals(Category.OTHER),
            "has-tag" to RuleMatch.HasTag(Tag("music")),
            "all-of" to
                RuleMatch.AllOf(
                    listOf(
                        RuleMatch.ChannelId("UC1"),
                        RuleMatch.AnyOf(listOf(RuleMatch.HasTag(Tag("live")), RuleMatch.TitleRegex("Live"))),
                    ),
                ),
            "any-of" to RuleMatch.AnyOf(listOf(RuleMatch.ChannelName("A"), RuleMatch.UrlRegex("rutube\\.ru"))),
        )

    private val metadataTemplates: Map<String, MetadataTemplate> =
        mapOf(
            "music-video" to
                MetadataTemplate.MusicVideo(
                    artistOverride = "Rick Astley",
                    artistPattern = "^(.+?) -",
                    titleOverride = "Never Gonna Give You Up",
                    titlePattern = "- (.+)$",
                    defaultTags = listOf("music", "80s"),
                ),
            "series-episode" to
                MetadataTemplate.SeriesEpisode(
                    seriesNameOverride = "The Show",
                    seasonPattern = "S(\\d+)",
                    episodePattern = "E(\\d+)",
                    titleOverride = "Pilot",
                    titlePattern = "\\| (.+)$",
                    defaultTags = listOf("tv"),
                ),
            "other" to
                MetadataTemplate.Other(titleOverride = "Clip", titlePattern = "(.+)", defaultTags = listOf("misc")),
            "other-empty" to MetadataTemplate.Other(),
        )

    val ruleMetadataTemplate: Map<String, MetadataTemplate> = metadataTemplates

    val ruleDownloadPolicy: Map<String, DownloadPolicy> =
        mapOf(
            "full" to
                DownloadPolicy(
                    maxQuality = DownloadPolicy.VideoQuality.HD_1080,
                    downloadSubtitles = true,
                    subtitleLanguages = listOf("en", "ru"),
                    writeThumbnail = true,
                    audioLanguages = listOf("en", "de"),
                ),
            "subtitles-off-original-audio-only" to
                DownloadPolicy(
                    maxQuality = DownloadPolicy.VideoQuality.HD_720,
                    downloadSubtitles = false,
                    audioLanguages = emptyList(),
                ),
            "sd-480" to DownloadPolicy(maxQuality = DownloadPolicy.VideoQuality.SD_480),
            "defaults" to DownloadPolicy(),
        )

    val ruleOutputs: Map<String, List<OutputRule>> =
        mapOf(
            "every-format-kind" to
                listOf(
                    OutputRule(
                        pathTemplate = "/media/{channelName}/{title} [{videoId}].{ext}",
                        format = OutputFormat.OriginalVideo(MediaContainer.MKV),
                        maxQuality = DownloadPolicy.VideoQuality.BEST,
                        embedThumbnail = true,
                        embedMetadata = true,
                        embedSubtitles = true,
                    ),
                    OutputRule(
                        pathTemplate = "/media/converted/{title}.mp4",
                        format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
                        maxQuality = DownloadPolicy.VideoQuality.HD_720,
                        encodeSettings =
                        VideoEncodeSettings(
                            codec = VideoEncodeSettings.VideoCodec.AV1,
                            hwAccel = VideoEncodeSettings.HwAccel.NVENC,
                            preset = VideoEncodeSettings.EncodePreset.VERYSLOW,
                            crf = 30,
                            audioBitrate = "128k",
                            audioCodec = "libopus",
                        ),
                        normalizeAudio = true,
                    ),
                    OutputRule(
                        pathTemplate = "/media/audio/{title}.opus",
                        format = OutputFormat.Audio(AudioFormat.OPUS),
                    ),
                    OutputRule(
                        pathTemplate = "/media/covers/{title}.jpg",
                        format = OutputFormat.Thumbnail(ImageFormat.JPG),
                    ),
                ),
            "single-default-encode" to
                listOf(
                    OutputRule(
                        pathTemplate = "/media/{title}.mov",
                        format = OutputFormat.ConvertedVideo(MediaContainer.MOV),
                        encodeSettings = VideoEncodeSettings(),
                    ),
                ),
        )

    val channelMetadataOverrides: Map<String, MetadataTemplate> = metadataTemplates

    val channelTrackPreferences: Map<String, TrackPreferences> =
        mapOf(
            "full" to
                TrackPreferences(
                    audioLanguages = listOf("en"),
                    downloadSubtitles = true,
                    subtitleLanguages = listOf("ru", "en"),
                ),
            "original-audio-only-subtitles-off" to
                TrackPreferences(audioLanguages = emptyList(), downloadSubtitles = false),
            "all-inherit" to TrackPreferences(),
        )

    private val videoInfos: Map<String, VideoInfo> =
        mapOf(
            "full" to frozenFullVideoInfo(),
            "minimal" to
                VideoInfo(
                    videoId = VideoId("fixture-min"),
                    extractor = Extractor.RUTUBE,
                    title = "Minimal",
                    channelId = ChannelId("rt-1"),
                    channelName = "RuTube channel",
                    uploadDate = null,
                    duration = 0.seconds,
                    webpageUrl = Url("https://rutube.ru/video/fixture-min/"),
                ),
        )

    val jobRawInfo: Map<String, VideoInfo> = videoInfos

    val jobMetadata: Map<String, ResolvedMetadata> =
        mapOf(
            "music-video" to
                ResolvedMetadata.MusicVideo(
                    artist = "Rick Astley",
                    title = "Never Gonna Give You Up",
                    album = "Whenever You Need Somebody",
                    releaseDate = LocalDate("1987-07-27"),
                    tags = listOf("pop"),
                    comment = "classic",
                ),
            "series-episode" to
                ResolvedMetadata.SeriesEpisode(
                    seriesName = "The Show",
                    season = "02",
                    episode = "05",
                    title = "Pilot",
                    releaseDate = LocalDate("2020-01-31"),
                    tags = listOf("tv"),
                    comment = "S02E05",
                ),
            "other" to
                ResolvedMetadata.Other(
                    title = "Clip",
                    releaseDate = LocalDate("2024-12-01"),
                    tags = listOf("a"),
                    comment = "c",
                ),
            "other-minimal" to ResolvedMetadata.Other(title = "Clip"),
        )

    val jobStoragePlan: Map<String, StoragePlan> =
        mapOf(
            "full" to frozenFullStoragePlan(),
            "original-only" to
                StoragePlan(
                    OutputTarget(FilePath("/media/other/clip.webm"), OutputFormat.OriginalVideo(MediaContainer.WEBM)),
                ),
        )

    val jobMediaSelection: Map<String, MediaSelection> =
        mapOf(
            "full" to MediaSelection(audioFormatIds = listOf("140-0", "140-1"), subtitleLanguages = listOf("en", "ru")),
            "no-subtitles" to MediaSelection(audioFormatIds = listOf("251"), subtitleLanguages = emptyList()),
            "all-null" to MediaSelection(),
        )

    /** `jobs.progress` as the repository writes it: phase + percent. */
    val jobProgress: Map<String, Pair<JobPhase, Int>> =
        JobPhase.entries.associate { phase ->
            phase.name.lowercase().replace('_', '-') to
                (phase to (phase.ordinal * 15))
        }

    /** `jobs.error` as the repository writes it: only the message reaches the domain. */
    val jobError: Map<String, String> =
        mapOf("failed" to "yt-dlp exited with code 1: ERROR: [youtube] abc: Video unavailable")

    val videoInfoCacheVideoInfo: Map<String, VideoInfo> = videoInfos

    /** `system_settings.value` for key `ytdlp` (JSON in a `text` column). */
    val systemSettingsYtDlp: Map<String, YtDlpConfig> =
        mapOf(
            "customised" to
                YtDlpConfig(
                    path = "/opt/yt-dlp",
                    timeout = 45.minutes,
                    retries = 7,
                    fragmentRetries = 20,
                    allowUpdate = false,
                    updateChannel = "nightly",
                    autoDownload = false,
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
                        "rutube" to YtDlpExtractorOverride(legacyServerConnect = true, proxyEnabled = false),
                        "vk" to YtDlpExtractorOverride(noCheckCertificate = true),
                    ),
                ),
            "defaults" to YtDlpConfig(),
        )

    /** `system_settings.value` for key `proxy`. */
    val systemSettingsProxy: Map<String, ProxyConfig> =
        mapOf(
            "socks5-with-credentials" to
                ProxyConfig(
                    enabled = true,
                    type = ProxyConfig.ProxyType.SOCKS5,
                    host = "10.0.0.1",
                    port = 1080,
                    username = "user",
                    password = "secret",
                ),
            "defaults" to ProxyConfig(),
        )
}

// Frozen copies of the object mothers: fixture expectations must not move when test mothers change.

private fun frozenVideoFormat(formatId: String = "137") = VideoInfo.Format(
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

private fun frozenAudioFormat(formatId: String, language: String) =
    VideoInfo.Format(formatId = formatId, extension = "m4a", acodec = "mp4a.40.2", vcodec = "none", language = language)

private fun frozenFullVideoInfo(videoId: String = "fixture-full") = VideoInfo(
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
        frozenVideoFormat(),
        frozenAudioFormat("140-0", "en"),
        VideoInfo.Format(formatId = "18", extension = "mp4"),
    ),
    actualFormat = frozenVideoFormat("137+140"),
    subtitleTracks =
    listOf(
        VideoInfo.SubtitleTrack("en", automatic = false, name = "English"),
        VideoInfo.SubtitleTrack("ru", automatic = true),
    ),
)

private fun frozenFullStoragePlan() = StoragePlan(
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
