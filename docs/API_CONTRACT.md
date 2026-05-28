# API Contract

> **Purpose**: Full HTTP API specification — endpoints, DTOs, sealed class serialization, error format.

---

## 1. General Rules

### 1.1 Base URL

```
/api/v1/
```

All domain resources (jobs, rules, preview) are scoped to a workspace:

```
/api/v1/workspaces/{slug}/jobs
/api/v1/workspaces/{slug}/rules
/api/v1/workspaces/{slug}/preview
```

Workspace management and system endpoints are at the top level:

```
/api/v1/workspaces
/api/v1/system/...
```

All resources are described using **Ktor Resources** for type-safety and convenient client usage.

See also: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

### 1.2 Authentication

All requests require a Telegram `initData` header:

```http
X-Telegram-Init-Data: <initData>
```

See [SECURITY.md](./SECURITY.md).

### 1.3 Content-Type

- Request: `application/json`
- Response: `application/json`

### 1.4 Correlation ID

The server generates a `correlationId` for each request.
It is returned in the `X-Correlation-Id` header and in error responses.

---

## 2. Error Format

### 2.1 ApiErrorDto

```kotlin
@Serializable
data class ApiErrorDto(
    val error: ErrorDetail,
) {
    @Serializable
    data class ErrorDetail(
        val code: String,
        val message: String,
        val correlationId: String,
        val details: JsonElement? = null,
    )
}
```

### 2.2 Example Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Field 'url' is required",
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "details": {
      "field": "url"
    }
  }
}
```

### 2.3 Error Codes

| Code                      | HTTP Status | Description                                          |
|---------------------------|-------------|------------------------------------------------------|
| `VALIDATION_ERROR`        | 400         | Input validation error                               |
| `INVALID_URL`             | 400         | Invalid video URL                                    |
| `UNAUTHORIZED`            | 401         | Invalid initData                                     |
| `FORBIDDEN`               | 403         | User not in allowlist                                |
| `WORKSPACE_ACCESS_DENIED` | 403         | User is not a member of the workspace                |
| `NOT_FOUND`               | 404         | Resource not found                                   |
| `CONFLICT`                | 409         | Conflict (e.g. a job already exists for this video)  |
| `UPDATE_DISABLED`         | 403         | yt-dlp update is disabled in configuration           |
| `VIDEO_UNAVAILABLE`       | 422         | Video is unavailable                                 |
| `LLM_ERROR`               | 502         | Error calling the LLM provider                       |
| `INTERNAL_ERROR`          | 500         | Internal server error                                |

---

## 3. Sealed Class Serialization

### 3.1 Principle

Polymorphic DTOs use a `type` discriminator field.

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchDto
```

### 3.2 kotlinx.serialization Configuration

```kotlin
val json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}
```

---

## 4. DTO: RuleMatch

### 4.1 Kotlin

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchDto {
    
    @Serializable
    @SerialName("all-of")
    data class AllOf(
        val matches: List<RuleMatchDto>,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("any-of")
    data class AnyOf(
        val matches: List<RuleMatchDto>,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("channel-id")
    data class ChannelId(
        val value: String,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("channel-name")
    data class ChannelName(
        val value: String,
        val ignoreCase: Boolean = true,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("title-regex")
    data class TitleRegex(
        val pattern: String,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("url-regex")
    data class UrlRegex(
        val pattern: String,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("category-equals")
    data class CategoryEquals(
        val category: CategoryDto,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("has-tag")
    data class HasTag(
        val tag: String,
    ) : RuleMatchDto
}
```

### 4.2 JSON Examples

**ChannelId**:
```json
{
  "type": "channel-id",
  "value": "UCq-Fj5jknLsUf-MWSy4_brA"
}
```

**AllOf (AND)**:
```json
{
  "type": "all-of",
  "matches": [
    { "type": "channel-name", "value": "Kurzgesagt" },
    { "type": "title-regex", "pattern": ".*Documentary.*" }
  ]
}
```

**AnyOf (OR)**:
```json
{
  "type": "any-of",
  "matches": [
    { "type": "channel-id", "value": "UC123" },
    { "type": "channel-id", "value": "UC456" }
  ]
}
```

**CategoryEquals** (matches on user-overridden category):
```json
{
  "type": "category-equals",
  "category": "music-video"
}
```

**HasTag** (matches on tag from the channel directory):
```json
{
  "type": "has-tag",
  "tag": "music-video"
}
```

---

## 5. DTO: ResolvedMetadata

### 5.1 Kotlin

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface ResolvedMetadataDto {
    val title: String
    val releaseDate: String?   // ISO 8601: "2024-02-25"
    val tags: List<String>
    val comment: String?
    
    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artist: String,
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
    
    @Serializable
    @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
    
    @Serializable
    @SerialName("other")
    data class Other(
        override val title: String,
        override val releaseDate: String? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
}
```

### 5.2 JSON Examples

**MusicVideo**:
```json
{
  "type": "music-video",
  "artist": "Rick Astley",
  "title": "Never Gonna Give You Up",
  "releaseDate": "1987-10-01",
  "tags": ["80s", "pop"],
  "comment": null
}
```

**SeriesEpisode**:
```json
{
  "type": "series-episode",
  "seriesName": "Kurzgesagt",
  "season": "2024",
  "episode": "01",
  "title": "The Egg",
  "releaseDate": "2024-01-15",
  "tags": ["science", "animation"],
  "comment": null
}
```

**Other**:
```json
{
  "type": "other",
  "title": "Random Video Title",
  "releaseDate": null,
  "tags": [],
  "comment": null
}
```

---

### 5.3 MetadataSourceDto

```kotlin
@Serializable
enum class MetadataSourceDto {
    @SerialName("rule")     RULE,
    @SerialName("llm")      LLM,
    @SerialName("fallback") FALLBACK,
}
```

---

## 6. Endpoints

### 6.1 POST /api/v1/workspaces/{slug}/preview

Get a metadata preview for a URL.

**Resource**: `ApiV1.Workspaces.ById.Preview`

#### Request

```kotlin
@Serializable
data class PreviewRequestDto(
    val url: String,
    val overrides: UserOverridesDto? = null,
)
```

```json
{
  "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

With user overrides (re-request after refining the category):
```json
{
  "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "overrides": {
    "type": "music-video",
    "artist": "Rick Astley",
    "title": null,
    "album": null
  }
}
```

> `UserOverridesDto` is sealed by category. The sealed type determines the target category.
> If `overrides == null` — this is the first request, with no refinements.

#### Response

```kotlin
@Serializable
data class PreviewResponseDto(
    val source: VideoSourceDto,
    val videoInfo: VideoInfoDto,
    val matchedRule: RuleSummaryDto? = null,
    val metadataSource: MetadataSourceDto,
    val category: CategoryDto,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val appliedOverrides: UserOverridesDto? = null,
    val warnings: List<String> = emptyList(),
)
```

```json
{
  "source": {
    "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "videoId": "dQw4w9WgXcQ",
    "extractor": "youtube"
  },
  "videoInfo": {
    "videoId": "dQw4w9WgXcQ",
    "extractor": "youtube",
    "title": "Rick Astley - Never Gonna Give You Up",
    "channelId": "UCuAXFkgsw1L7xaCfnd5JJOw",
    "channelName": "Rick Astley",
    "uploadDate": "2009-10-25",
    "durationSeconds": 212,
    "webpageUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "thumbnails": [...]
  },
  "matchedRule": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Rick Astley Music Videos"
  },
  "metadataSource": "rule",
  "category": "music-video",
  "metadata": {
    "type": "music-video",
    "artist": "Rick Astley",
    "title": "Never Gonna Give You Up",
    "releaseDate": "2009-10-25",
    "tags": ["music", "official"],
    "comment": null
  },
  "storagePlan": {
    "original": {
      "path": "/media/Music Videos/original/Rick Astley/Never Gonna Give You Up [dQw4w9WgXcQ].webm",
      "format": "original/webm"
    },
    "additional": [
      {
        "path": "/media/Music Videos/converted/Rick Astley/Never Gonna Give You Up.mp4",
        "format": "video/mp4"
      }
    ]
  },
  "warnings": []
}
```

#### Errors

- `400 INVALID_URL` — invalid URL
- `422 VIDEO_UNAVAILABLE` — video is unavailable

---

### 6.2 POST /api/v1/workspaces/{slug}/jobs

Create a download job.

**Resource**: `ApiV1.Workspaces.ById.Jobs`

#### Request

```kotlin
@Serializable
data class CreateJobRequestDto(
    val source: VideoSourceDto,
    val ruleId: String?,
    val category: String,
    val videoInfo: VideoInfoDto,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val saveAsRule: SaveAsRuleDto? = null,  // optional: save current settings as a rule
)

@Serializable
data class SaveAsRuleDto(
    val enabled: Boolean = true,
    val matchBy: String = "channelId",   // channelId | channelName
    val includeCategory: Boolean = true,
    val includeMetadataTemplate: Boolean = true,
    val includeStoragePolicy: Boolean = true,
)
```

```json
{
  "source": {
    "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "videoId": "dQw4w9WgXcQ",
    "extractor": "youtube"
  },
  "ruleId": "550e8400-e29b-41d4-a716-446655440000",
  "category": "music-video",
  "videoInfo": { ... },
  "metadata": {
    "type": "music-video",
    "artist": "Rick Astley",
    "title": "Never Gonna Give You Up",
    "releaseDate": "1987-10-01",
    "tags": ["80s"],
    "comment": null
  },
  "storagePlan": { ... }
}
```

#### Response

```kotlin
@Serializable
data class JobDto(
    val id: String,
    val status: String,
    val source: VideoSourceDto,
    val ruleId: String?,
    val category: String,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val progress: JobProgressDto?,
    val error: JobErrorDto?,
    val attempt: Int,
    val createdBy: String?,   // Telegram user ID of the creator
    val createdAt: String,  // ISO-8601
    val updatedAt: String,
    val startedAt: String?,
    val finishedAt: String?,
)
```

#### Errors

- `400 VALIDATION_ERROR` — invalid input data
- `409 CONFLICT` — an active job for this videoId already exists

---

### 6.3 GET /api/v1/workspaces/{slug}/jobs

List jobs in the current workspace.

**Resource**: `ApiV1.Workspaces.ById.Jobs`

#### Query Parameters

| Param    | Type   | Default | Description       |
|----------|--------|---------|-------------------|
| `status` | string | —       | Filter by status  |
| `limit`  | int    | 20      | Maximum records   |
| `offset` | int    | 0       | Offset            |

#### Response

```kotlin
@Serializable
data class JobListResponseDto(
    val items: List<JobDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)
```

---

### 6.4 GET /api/v1/workspaces/{slug}/jobs/{id}

Get a job by ID.

#### Response

`JobDto`

#### Errors

- `404 NOT_FOUND`

---

### 6.5 POST /api/v1/workspaces/{slug}/jobs/{id}/cancel

Cancel a job.

#### Response

`JobDto` with updated status.

#### Errors

- `404 NOT_FOUND`
- `409 CONFLICT` — job is already in a terminal state

---

### 6.6 GET /api/v1/workspaces/{slug}/rules

List rules.

#### Response

```kotlin
@Serializable
data class RuleListResponseDto(
    val items: List<RuleDto>,
)
```

---

### 6.7 POST /api/v1/workspaces/{slug}/rules

Create a rule.

#### Request

```kotlin
@Serializable
data class CreateRuleRequestDto(
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val match: RuleMatchDto,
    val category: CategoryDto,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val outputs: List<OutputRuleDto>,
)
```

#### Response

`RuleDto`

---

### 6.8 GET /api/v1/workspaces/{slug}/rules/{id}

Get a rule by ID.

---

### 6.9 PUT /api/v1/workspaces/{slug}/rules/{id}

Update a rule.

---

### 6.10 DELETE /api/v1/workspaces/{slug}/rules/{id}

Delete (or deactivate) a rule.

---

### 6.11 GET /api/v1/workspaces/{slug}/channels

List channels in a workspace. Optionally filter by tag.

#### Query Parameters

| Parameter | Type   | Description               |
|-----------|--------|---------------------------|
| `tag`     | string | (optional) Filter by tag  |

#### Response

```json
{
  "items": [
    {
      "id": "uuid",
      "workspaceId": "uuid",
      "channelId": "UCq-Fj5jknLsUf-MWSy4_brA",
      "extractor": "youtube",
      "name": "Rick Astley",
      "tags": ["music-video", "pop"],
      "metadataOverrides": {
        "type": "music-video",
        "artistOverride": "Rick Astley"
      },
      "notes": null,
      "createdAt": "2026-01-15T10:30:00Z",
      "updatedAt": "2026-01-15T10:30:00Z"
    }
  ]
}
```

### 6.12 POST /api/v1/workspaces/{slug}/channels

Add a channel to the directory.

#### Request

```json
{
  "channelId": "UCq-Fj5jknLsUf-MWSy4_brA",
  "extractor": "youtube",
  "name": "Rick Astley",
  "tags": ["music-video", "pop"],
  "metadataOverrides": {
    "type": "music-video",
    "artistOverride": "Rick Astley"
  }
}
```

#### Response

`201 Created` — `ChannelDto`

### 6.13 GET /api/v1/workspaces/{slug}/channels/{id}

Get a channel by ID.

### 6.14 PUT /api/v1/workspaces/{slug}/channels/{id}

Update a channel. All fields are optional (partial update).

#### Request

```json
{
  "name": "Rick Astley Official",
  "tags": ["music-video", "pop", "80s"]
}
```

### 6.15 DELETE /api/v1/workspaces/{slug}/channels/{id}

Delete a channel. `204 No Content`.

### 6.16 GET /api/v1/workspaces/{slug}/channels/tags

List all unique tags in the workspace.

#### Response

```json
{
  "tags": ["lofi", "music-video", "pop", "series"]
}
```

---

## 7. Supporting DTOs

### 7.1 VideoSourceDto

```kotlin
@Serializable
data class VideoSourceDto(
    val url: String,
    val videoId: String,
    val extractor: String,  // determined automatically: "youtube", "rutube", "vk", "generic", ...
)
```

### 7.2 VideoInfoDto

```kotlin
@Serializable
data class VideoInfoDto(
    val videoId: String,
    val extractor: String,    // "youtube", "rutube", "vk", "generic", ...
    val title: String,
    val channelId: String,
    val channelName: String,
    val uploadDate: String?,  // YYYY-MM-DD
    val durationSeconds: Int, // mapping: domain Duration ↔ DTO Int
    val webpageUrl: String,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    val description: String? = null,
    val availableFormats: List<VideoFormatDto> = emptyList(),
)

@Serializable
data class VideoFormatDto(
    val formatId: String,
    val extension: String,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val tbr: Double? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val formatNote: String? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
)

@Serializable
data class ThumbnailDto(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)
```

### 7.3 StoragePlanDto

```kotlin
@Serializable
data class StoragePlanDto(
    val original: OutputTargetDto,
    val additional: List<OutputTargetDto> = emptyList(),
)

@Serializable
data class OutputTargetDto(
    val path: String,                          // resolved path
    val format: OutputFormatDto,               // "original/webm", "video/mp4", "audio/m4a", "image/jpg"
    val maxQuality: VideoQualityDto? = null,   // max height cap; null = no downscale
    val encodeSettings: VideoEncodeSettingsDto? = null,  // null = defaults (H264, CRF 23, medium)
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)
```

> `format` — a string of the form `"kind/extension"`. Mapped via `OutputFormat.parse(format)` / `outputFormat.serialized`.
>
> See section 7.9 for `OutputFormatDto`, `VideoQualityDto`, `VideoEncodeSettingsDto`.

### 7.4 JobProgressDto

```kotlin
@Serializable
data class JobProgressDto(
    val phase: String,
    val percent: Int,
    val message: String? = null,
)
```

### 7.5 JobErrorDto

```kotlin
@Serializable
data class JobErrorDto(
    val code: String,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false,
)
```

### 7.6 RuleDto

```kotlin
@Serializable
data class RuleDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    val match: RuleMatchDto,
    val category: CategoryDto,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val outputs: List<OutputRuleDto>,          // list of output files (first = original)
    val createdAt: String,
    val updatedAt: String,
)
```

### 7.7 RuleSummaryDto

```kotlin
@Serializable
data class RuleSummaryDto(
    val id: String,
    val name: String?,
)
```

### 7.8 Policy DTOs

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface MetadataTemplateDto {
    val titleOverride: String?
    val titlePattern: String?
    val defaultTags: List<String>
    
    @Serializable @SerialName("music-video")
    data class MusicVideo(
        val artistOverride: String? = null,
        val artistPattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplateDto
    
    @Serializable @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesNameOverride: String? = null,
        val seasonPattern: String? = null,
        val episodePattern: String? = null,
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplateDto
    
    @Serializable @SerialName("other")
    data class Other(
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplateDto
}

@Serializable
data class DownloadPolicyDto(
    val maxQuality: VideoQualityDto = VideoQualityDto.BEST,
    val preferredContainer: MediaContainerDto? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
    val writeThumbnail: Boolean = false,
)

@Serializable
enum class VideoQualityDto {
    @SerialName("best") BEST,
    @SerialName("hd_1080") HD_1080,
    @SerialName("hd_720") HD_720,
    @SerialName("sd_480") SD_480,
}

/**
 * One output file descriptor in a rule.
 * The first element in RuleDto.outputs is the original file (OriginalVideo).
 * The rest are conversions, audio tracks, thumbnails, etc.
 */
@Serializable
data class OutputRuleDto(
    val pathTemplate: String,                   // "/media/{artist}/{title}.{ext}"
    val format: OutputFormatDto,                // "original/webm", "video/mp4", ...
    val maxQuality: VideoQualityDto? = null,    // null = source resolution
    val encodeSettings: VideoEncodeSettingsDto? = null,  // null = defaults
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

/**
 * Video encoding settings.
 * Applied only when the source exceeds maxQuality (otherwise — remux only).
 */
@Serializable
data class VideoEncodeSettingsDto(
    val codec: VideoCodecDto = VideoCodecDto.H264,
    val hwAccel: HwAccelDto? = null,
    val preset: EncodePresetDto = EncodePresetDto.MEDIUM,
    val crf: Int = 23,             // 0..51; typical: 18 (high quality), 23 (YouTube-like), 28 (smaller file)
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,  // null = auto (aac for mp4, libopus for webm)
)

@Serializable
enum class VideoCodecDto {
    @SerialName("h264") H264,
    @SerialName("h265") H265,
    @SerialName("vp9") VP9,
    @SerialName("av1") AV1,
}

@Serializable
enum class HwAccelDto {
    @SerialName("videotoolbox") VIDEOTOOLBOX,  // macOS
    @SerialName("nvenc") NVENC,                // NVIDIA
    @SerialName("qsv") QSV,                   // Intel Quick Sync
    @SerialName("vaapi") VAAPI,                // Linux VA-API
    @SerialName("amf") AMF,                   // AMD
}

@Serializable
enum class EncodePresetDto {
    @SerialName("ultrafast") ULTRAFAST,
    @SerialName("superfast") SUPERFAST,
    @SerialName("veryfast") VERYFAST,
    @SerialName("faster") FASTER,
    @SerialName("fast") FAST,
    @SerialName("medium") MEDIUM,
    @SerialName("slow") SLOW,
    @SerialName("slower") SLOWER,
    @SerialName("veryslow") VERYSLOW,
}
```

### 7.9 OutputFormatDto

```kotlin
@Serializable(with = OutputFormatDtoSerializer::class)
sealed interface OutputFormatDto {
    @SerialName("original") data class OriginalVideo(val container: MediaContainerDto) : OutputFormatDto
    @SerialName("video")    data class ConvertedVideo(val container: MediaContainerDto) : OutputFormatDto
    @SerialName("audio")    data class Audio(val format: AudioFormatDto)               : OutputFormatDto
    @SerialName("image")    data class Thumbnail(val format: ImageFormatDto)           : OutputFormatDto
}

@Serializable
enum class MediaContainerDto { @SerialName("mp4") MP4, @SerialName("mkv") MKV,
    @SerialName("webm") WEBM, @SerialName("avi") AVI, @SerialName("mov") MOV }

@Serializable
enum class AudioFormatDto { @SerialName("m4a") M4A, @SerialName("mp3") MP3,
    @SerialName("opus") OPUS, @SerialName("flac") FLAC, @SerialName("wav") WAV }

@Serializable
enum class ImageFormatDto { @SerialName("jpg") JPG, @SerialName("png") PNG,
    @SerialName("webp") WEBP }
```

> `OutputFormatDto` is serialized as the string `"kind/extension"` via a custom serializer:
> `"original/webm"`, `"video/mp4"`, `"audio/m4a"`, `"image/jpg"`.

---

### 7.10 Full Rule JSON Example (music-video)

```json
{
  "id": "34a23c97-9d14-4bb9-b221-545a5895e1bd",
  "name": "Music Videos",
  "enabled": true,
  "priority": 0,
  "match": {
    "type": "channel-name",
    "value": "Casting Crowns",
    "ignoreCase": true
  },
  "category": "music-video",
  "metadataTemplate": {
    "type": "music-video",
    "artistOverride": null,
    "artistPattern": null,
    "titleOverride": null,
    "titlePattern": null,
    "defaultTags": []
  },
  "downloadPolicy": {
    "maxQuality": "best",
    "preferredContainer": null,
    "downloadSubtitles": false,
    "subtitleLanguages": [],
    "writeThumbnail": false
  },
  "outputs": [
    {
      "pathTemplate": "/media/Music Videos/original/{artist}/{title}.{ext}",
      "format": "original/webm",
      "maxQuality": null,
      "encodeSettings": null,
      "embedThumbnail": false,
      "embedMetadata": false,
      "embedSubtitles": false,
      "normalizeAudio": false
    },
    {
      "pathTemplate": "/media/Music Videos/converted/{artist}/{title}/{title}.{ext}",
      "format": "video/mp4",
      "maxQuality": "hd_1080",
      "encodeSettings": {
        "codec": "h264",
        "hwAccel": "videotoolbox",
        "preset": "medium",
        "crf": 23,
        "audioBitrate": "192k",
        "audioCodec": null
      },
      "embedThumbnail": true,
      "embedMetadata": true,
      "embedSubtitles": false,
      "normalizeAudio": false
    }
  ],
  "createdAt": "2026-03-01T10:00:00Z",
  "updatedAt": "2026-03-04T12:00:00Z"
}
```

---

## 8. Domain ↔ DTO Mapping

### 8.1 Location

Module: `api:mapping`

### 8.2 File Structure

```
api/mapping/src/commonMain/kotlin/io/github/alelk/tgvd/api/mapping/
├── common/
│   └── CategoryMapping.kt
├── rule/
│   ├── toDto.kt
│   └── toDomain.kt
├── metadata/
│   ├── ...
├── video/
│   ├── ...
├── storage/
│   ├── ...
├── preview/
│   └── UserOverridesMapping.kt
└── ...
```

### 8.3 RuleMatchToDto.kt

```kotlin
// --- Subtypes ---

fun RuleMatch.AllOf.toDto(): RuleMatchDto.AllOf =
    RuleMatchDto.AllOf(matches.map { it.toDto() })

fun RuleMatch.AnyOf.toDto(): RuleMatchDto.AnyOf =
    RuleMatchDto.AnyOf(matches.map { it.toDto() })

fun RuleMatch.ChannelId.toDto(): RuleMatchDto.ChannelId =
    RuleMatchDto.ChannelId(value)

fun RuleMatch.ChannelName.toDto(): RuleMatchDto.ChannelName =
    RuleMatchDto.ChannelName(value, ignoreCase)

fun RuleMatch.TitleRegex.toDto(): RuleMatchDto.TitleRegex =
    RuleMatchDto.TitleRegex(pattern)

fun RuleMatch.UrlRegex.toDto(): RuleMatchDto.UrlRegex =
    RuleMatchDto.UrlRegex(pattern)

// --- Supertype ---

fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.AllOf -> toDto()
    is RuleMatch.AnyOf -> toDto()
    is RuleMatch.ChannelId -> toDto()
    is RuleMatch.ChannelName -> toDto()
    is RuleMatch.TitleRegex -> toDto()
    is RuleMatch.UrlRegex -> toDto()
    is RuleMatch.CategoryEquals -> RuleMatchDto.CategoryEquals(category.toDto())
}
```

### 8.4 RuleMatchToDomain.kt

```kotlin
// --- Subtypes ---

fun RuleMatchDto.AllOf.toDomain(): Either<DomainError.ValidationError, RuleMatch.AllOf> =
    if (matches.isEmpty()) DomainError.ValidationError("matches", "Cannot be empty").left()
    else matches.traverse { it.toDomain() }.map { RuleMatch.AllOf(it) }

fun RuleMatchDto.AnyOf.toDomain(): Either<DomainError.ValidationError, RuleMatch.AnyOf> =
    if (matches.isEmpty()) DomainError.ValidationError("matches", "Cannot be empty").left()
    else matches.traverse { it.toDomain() }.map { RuleMatch.AnyOf(it) }

fun RuleMatchDto.ChannelId.toDomain(): Either<DomainError.ValidationError, RuleMatch.ChannelId> =
    if (value.isBlank()) DomainError.ValidationError("value", "Cannot be blank").left()
    else RuleMatch.ChannelId(value).right()

fun RuleMatchDto.ChannelName.toDomain(): Either<DomainError.ValidationError, RuleMatch.ChannelName> =
    if (value.isBlank()) DomainError.ValidationError("value", "Cannot be blank").left()
    else RuleMatch.ChannelName(value, ignoreCase).right()

fun RuleMatchDto.TitleRegex.toDomain(): Either<DomainError.ValidationError, RuleMatch.TitleRegex> =
    if (pattern.isBlank()) DomainError.ValidationError("pattern", "Cannot be blank").left()
    else RuleMatch.TitleRegex(pattern).right()

fun RuleMatchDto.UrlRegex.toDomain(): Either<DomainError.ValidationError, RuleMatch.UrlRegex> =
    if (pattern.isBlank()) DomainError.ValidationError("pattern", "Cannot be blank").left()
    else RuleMatch.UrlRegex(pattern).right()

// --- Supertype ---

fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.AllOf -> toDomain()
    is RuleMatchDto.AnyOf -> toDomain()
    is RuleMatchDto.ChannelId -> toDomain()
    is RuleMatchDto.ChannelName -> toDomain()
    is RuleMatchDto.TitleRegex -> toDomain()
    is RuleMatchDto.UrlRegex -> toDomain()
    is RuleMatchDto.CategoryEquals -> RuleMatch.CategoryEquals(category.toDomain()).right()
}
```

### 8.5 ResolvedMetadataToDto.kt

```kotlin
// --- Subtypes ---

fun ResolvedMetadata.MusicVideo.toDto(): ResolvedMetadataDto.MusicVideo =
    ResolvedMetadataDto.MusicVideo(
        artist = artist,
        title = title,
        releaseDate = releaseDate?.value,
        tags = tags,
        comment = comment,
    )

fun ResolvedMetadata.SeriesEpisode.toDto(): ResolvedMetadataDto.SeriesEpisode =
    ResolvedMetadataDto.SeriesEpisode(
        seriesName = seriesName,
        season = season,
        episode = episode,
        title = title,
        releaseDate = releaseDate?.value,
        tags = tags,
        comment = comment,
    )

fun ResolvedMetadata.Other.toDto(): ResolvedMetadataDto.Other =
    ResolvedMetadataDto.Other(
        title = title,
        releaseDate = releaseDate?.value,
        tags = tags,
        comment = comment,
    )

// --- Supertype ---

fun ResolvedMetadata.toDto(): ResolvedMetadataDto = when (this) {
    is ResolvedMetadata.MusicVideo -> toDto()
    is ResolvedMetadata.SeriesEpisode -> toDto()
    is ResolvedMetadata.Other -> toDto()
}
```

### 8.6 ResolvedMetadataToDomain.kt

```kotlin
// --- Subtypes ---

fun ResolvedMetadataDto.MusicVideo.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata.MusicVideo> =
    either {
        ResolvedMetadata.MusicVideo(
            artist = ensure(artist.isNotBlank()) { DomainError.ValidationError("artist", "Cannot be blank") }.let { artist },
            title = ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }.let { title },
            releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags,
            comment = comment,
        )
    }

fun ResolvedMetadataDto.SeriesEpisode.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata.SeriesEpisode> =
    either {
        ResolvedMetadata.SeriesEpisode(
            seriesName = ensure(seriesName.isNotBlank()) { DomainError.ValidationError("seriesName", "Cannot be blank") }.let { seriesName },
            season = season,
            episode = episode,
            title = ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }.let { title },
            releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags,
            comment = comment,
        )
    }

fun ResolvedMetadataDto.Other.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata.Other> =
    either {
        ResolvedMetadata.Other(
            title = ensure(title.isNotBlank()) { DomainError.ValidationError("title", "Cannot be blank") }.let { title },
            releaseDate = releaseDate?.let { LocalDate(it) },
            tags = tags,
            comment = comment,
        )
    }

// --- Supertype ---

fun ResolvedMetadataDto.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata> = when (this) {
    is ResolvedMetadataDto.MusicVideo -> toDomain()
    is ResolvedMetadataDto.SeriesEpisode -> toDomain()
    is ResolvedMetadataDto.Other -> toDomain()
}
```

> **Principle**: each subtype has its own `toDto()` / `toDomain()` function with a precise return type.
> The supertype delegates via an exhaustive `when`. This allows:
> - Calling typed mapping directly when the subtype is known
> - The compiler verifies exhaustiveness when a new subtype is added

### 8.7 Error Mapping

```kotlin
fun DomainError.toApiError(correlationId: String): Pair<HttpStatusCode, ApiErrorDto> = when (this) {
    is DomainError.ValidationError -> 
        HttpStatusCode.BadRequest to ApiErrorDto(
            error = ApiErrorDto.ErrorDetail(
                code = "VALIDATION_ERROR",
                message = message,
                correlationId = correlationId,
                details = buildJsonObject { put("field", field) }
            )
        )
    is DomainError.InvalidUrl -> 
        HttpStatusCode.BadRequest to ApiErrorDto(...)
    is DomainError.Unauthorized -> 
        HttpStatusCode.Unauthorized to ApiErrorDto(...)
    is DomainError.Forbidden -> 
        HttpStatusCode.Forbidden to ApiErrorDto(...)
    is DomainError.WorkspaceAccessDenied -> 
        HttpStatusCode.Forbidden to ApiErrorDto(...)
    is DomainError.RuleNotFound, is DomainError.JobNotFound, is DomainError.WorkspaceNotFound -> 
        HttpStatusCode.NotFound to ApiErrorDto(...)
    is DomainError.JobAlreadyExists -> 
        HttpStatusCode.Conflict to ApiErrorDto(...)
    is DomainError.VideoUnavailable -> 
        HttpStatusCode.UnprocessableEntity to ApiErrorDto(...)
    is DomainError.LlmError ->
        HttpStatusCode.BadGateway to ApiErrorDto(...)
    else -> 
        HttpStatusCode.InternalServerError to ApiErrorDto(...)
}
```

---

## 9. Versioning

### 9.1 Current Version

`v1`

### 9.2 Compatibility Rules

**Allowed in v1**:
- Adding new optional fields to responses
- Adding new endpoints
- Adding new `type` values for sealed DTOs

**Requires v2**:
- Removing fields
- Renaming fields
- Changing field types
- Changing the semantics of existing fields

### 9.3 Handling Unknown Types

The client should:
1. Ignore unknown fields (`ignoreUnknownKeys = true`)
2. On unknown `type` for `ResolvedMetadataDto` — fall back to `Other`
3. On unknown `type` for `RuleMatchDto` — treat as an error (rules are critical)

---

## 10. Workspace

### 10.1 GET /api/v1/workspaces

List workspaces for the current user.

**Resource**: `ApiV1.Workspaces`

#### Response

```kotlin
@Serializable
data class WorkspaceListResponseDto(
    val items: List<WorkspaceDto>,
)

@Serializable
data class WorkspaceDto(
    val id: String,
    val name: String,
    val role: String,       // "owner" | "member"
    val createdAt: String,  // ISO-8601
)
```

### 10.2 POST /api/v1/workspaces

Create a workspace. The creator automatically becomes OWNER.

**Resource**: `ApiV1.Workspaces`

#### Request

```kotlin
@Serializable
data class CreateWorkspaceRequestDto(
    val name: String,
)
```

#### Response (201 Created)

`WorkspaceDto`

### 10.3 GET /api/v1/workspaces/{slug}/members

List workspace members.

**Resource**: `ApiV1.Workspaces.ById.Members`

#### Response

```kotlin
@Serializable
data class WorkspaceMemberListResponseDto(
    val items: List<WorkspaceMemberDto>,
)

@Serializable
data class WorkspaceMemberDto(
    val userId: Long,
    val role: String,       // "owner" | "member"
    val joinedAt: String,   // ISO-8601
)
```

### 10.4 POST /api/v1/workspaces/{slug}/members

Add a member to the workspace. OWNER only.

**Resource**: `ApiV1.Workspaces.ById.Members`

#### Request

```kotlin
@Serializable
data class AddMemberRequestDto(
    val userId: Long,
    val role: String = "member",  // "owner" | "member"
)
```

#### Response (201 Created)

`WorkspaceMemberDto`

### 10.5 DELETE /api/v1/workspaces/{slug}/members/{userId}

Remove a member from the workspace. OWNER only.

**Resource**: `ApiV1.Workspaces.ById.Members.ByUserId`

#### Response

`204 No Content`

See also: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 11. System

### 11.1 GET /api/v1/system/yt-dlp/status

Get the current yt-dlp version and update availability.

**Response (200 OK):**
```json
{
  "currentVersion": "2024.02.11",
  "latestVersion": "2024.02.18",
  "isUpdateAvailable": true,
  "lastCheckedAt": "2024-02-18T10:00:00Z"
}
```

### 11.2 POST /api/v1/system/yt-dlp/update

Trigger the yt-dlp update process.

**Response (202 Accepted):**
```json
{
  "status": "UPDATING",
  "message": "Update process started"
}
```

**Response (403 Forbidden):**
When `ytDlp.allowUpdate: false`.
```json
{
  "error": {
    "code": "UPDATE_DISABLED",
    "message": "Update is disabled by administrator",
    "correlationId": "..."
  }
}
```
