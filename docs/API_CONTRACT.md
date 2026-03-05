# API Contract

> **Цель документа**: Полная спецификация HTTP API, DTO, сериализация sealed-классов, формат ошибок.

---

## 1. Общие правила

### 1.1 Base URL

```
/api/v1/
```

Все доменные ресурсы (jobs, rules, preview) привязаны к workspace:

```
/api/v1/workspaces/{slug}/jobs
/api/v1/workspaces/{slug}/rules
/api/v1/workspaces/{slug}/preview
```

Управление workspace и системные эндпоинты — на верхнем уровне:

```
/api/v1/workspaces
/api/v1/system/...
```

Все ресурсы описаны с использованием **Ktor Resources** для обеспечения type-safety и удобства использования на клиенте.

Подробнее о workspace: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

### 1.2 Аутентификация

Все запросы требуют Telegram `initData` в заголовке:

```http
X-Telegram-Init-Data: <initData>
```

Подробнее: [SECURITY.md](./SECURITY.md)

### 1.3 Content-Type

- Request: `application/json`
- Response: `application/json`

### 1.4 Correlation ID

Сервер генерирует `correlationId` для каждого запроса.
Возвращается в заголовке `X-Correlation-Id` и в ошибках.

---

## 2. Формат ошибок

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

### 2.2 Пример ответа

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

### 2.3 Коды ошибок

| Code                      | HTTP Status | Описание                                      |
|---------------------------|-------------|-----------------------------------------------|
| `VALIDATION_ERROR`        | 400         | Ошибка валидации входных данных               |
| `INVALID_URL`             | 400         | Некорректный URL видео                        |
| `UNAUTHORIZED`            | 401         | Невалидный initData                           |
| `FORBIDDEN`               | 403         | Пользователь не в allowlist                   |
| `WORKSPACE_ACCESS_DENIED` | 403         | Пользователь не является участником workspace |
| `NOT_FOUND`               | 404         | Ресурс не найден                              |
| `CONFLICT`                | 409         | Конфликт (например, job уже существует)       |
| `UPDATE_DISABLED`         | 403         | Обновление yt-dlp запрещено в конфигурации    |
| `VIDEO_UNAVAILABLE`       | 422         | Видео недоступно                              |
| `LLM_ERROR`               | 502         | Ошибка при обращении к LLM провайдеру         |
| `INTERNAL_ERROR`          | 500         | Внутренняя ошибка сервера                     |

---

## 3. Сериализация sealed-классов

### 3.1 Принцип

Для polymorphic DTO используется discriminator поле `type`.

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchDto
```

### 3.2 Конфигурация kotlinx.serialization

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

### 4.2 JSON примеры

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

**CategoryEquals** (матчит по user override категории):
```json
{
  "type": "category-equals",
  "category": "music-video"
}
```

**HasTag** (матчит по тегу из справочника каналов):
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

### 5.2 JSON примеры

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

## 6. Эндпоинты

### 6.1 POST /api/v1/workspaces/{slug}/preview

Получить preview метаданных для URL.

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

С user overrides (повторный вызов после уточнения категории):
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

> `UserOverridesDto` — sealed по категории. Тип sealed определяет целевую категорию.
> Если overrides == null — первый запрос, без уточнений.

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

#### Ошибки

- `400 INVALID_URL` — некорректный URL
- `422 VIDEO_UNAVAILABLE` — видео недоступно

---

### 6.2 POST /api/v1/workspaces/{slug}/jobs

Создать job.

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
    val saveAsRule: SaveAsRuleDto? = null,  // optional: сохранить настройки как правило
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

#### Ошибки

- `400 VALIDATION_ERROR` — невалидные данные
- `409 CONFLICT` — job для этого videoId уже существует и активен

---

### 6.3 GET /api/v1/workspaces/{slug}/jobs

Список jobs текущего workspace.

**Resource**: `ApiV1.Workspaces.ById.Jobs`

#### Query Parameters

| Param | Type | Default | Описание |
|-------|------|---------|----------|
| `status` | string | — | Фильтр по статусу |
| `limit` | int | 20 | Максимум записей |
| `offset` | int | 0 | Смещение |

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

Получить job по ID.

#### Response

`JobDto`

#### Ошибки

- `404 NOT_FOUND`

---

### 6.5 POST /api/v1/workspaces/{slug}/jobs/{id}/cancel

Отменить job.

#### Response

`JobDto` с обновлённым статусом.

#### Ошибки

- `404 NOT_FOUND`
- `409 CONFLICT` — job уже завершён

---

### 6.6 GET /api/v1/workspaces/{slug}/rules

Список правил.

#### Response

```kotlin
@Serializable
data class RuleListResponseDto(
    val items: List<RuleDto>,
)
```

---

### 6.7 POST /api/v1/workspaces/{slug}/rules

Создать правило.

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

Получить правило по ID.

---

### 6.9 PUT /api/v1/workspaces/{slug}/rules/{id}

Обновить правило.

---

### 6.10 DELETE /api/v1/workspaces/{slug}/rules/{id}

Удалить (или деактивировать) правило.

---

### 6.11 GET /api/v1/workspaces/{slug}/channels

Список каналов workspace. Опционально фильтрация по тегу.

#### Query Parameters

| Параметр | Тип    | Описание                         |
|----------|--------|----------------------------------|
| `tag`    | string | (опц.) Фильтр по тегу           |

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

Создать канал в справочнике.

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

Получить канал по ID.

### 6.14 PUT /api/v1/workspaces/{slug}/channels/{id}

Обновить канал. Все поля опциональны (partial update).

#### Request

```json
{
  "name": "Rick Astley Official",
  "tags": ["music-video", "pop", "80s"]
}
```

### 6.15 DELETE /api/v1/workspaces/{slug}/channels/{id}

Удалить канал. `204 No Content`.

### 6.16 GET /api/v1/workspaces/{slug}/channels/tags

Список всех уникальных тегов в workspace.

#### Response

```json
{
  "tags": ["lofi", "music-video", "pop", "series"]
}
```

---

## 7. Вспомогательные DTO

### 7.1 VideoSourceDto

```kotlin
@Serializable
data class VideoSourceDto(
    val url: String,
    val videoId: String,
    val extractor: String,  // определяется автоматически: "youtube", "rutube", "vk", "generic", ...
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
    val durationSeconds: Int, // маппинг: domain Duration ↔ DTO Int
    val webpageUrl: String,
    val thumbnails: List<ThumbnailDto> = emptyList(),
    val description: String? = null,
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

> `format` — строка вида `"kind/extension"`. Маппинг: `OutputFormat.parse(format)` / `outputFormat.serialized`.
>
> Подробнее о `OutputFormatDto`, `VideoQualityDto`, `VideoEncodeSettingsDto` — см. секцию 7.9.

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
    val outputs: List<OutputRuleDto>,          // список выходных файлов (первый = оригинал)
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
 * Описание одного выходного файла в правиле.
 * Первый элемент в RuleDto.outputs — оригинальный файл (OriginalVideo).
 * Остальные — конвертации, аудио, обложки.
 */
@Serializable
data class OutputRuleDto(
    val pathTemplate: String,                   // "/media/{artist}/{title}.{ext}"
    val format: OutputFormatDto,                // "original/webm", "video/mp4", ...
    val maxQuality: VideoQualityDto? = null,    // null = оригинальное разрешение
    val encodeSettings: VideoEncodeSettingsDto? = null,  // null = дефолты
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

/**
 * Настройки перекодирования видео.
 * Применяются только если источник превышает maxQuality (иначе — ремуксинг).
 */
@Serializable
data class VideoEncodeSettingsDto(
    val codec: VideoCodecDto = VideoCodecDto.H264,
    val hwAccel: HwAccelDto? = null,
    val preset: EncodePresetDto = EncodePresetDto.MEDIUM,
    val crf: Int = 23,             // 0..51; типичные значения: 18 (высокое), 23 (YouTube-like), 28 (экономия)
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,  // null = авто (aac для mp4, libopus для webm)
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

> `OutputFormatDto` сериализуется как строка `"kind/extension"` (кастомный сериализатор):
> `"original/webm"`, `"video/mp4"`, `"audio/m4a"`, `"image/jpg"`.

---

### 7.10 JSON-пример полного правила (music-video)

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

## 8. Маппинг Domain ↔ DTO

### 8.1 Расположение

Модуль: `api:mapping`

### 8.2 Структура файлов

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
// --- Подтипы ---

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

// --- Супертип ---

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
// --- Подтипы ---

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

// --- Супертип ---

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
// --- Подтипы ---

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

// --- Супертип ---

fun ResolvedMetadata.toDto(): ResolvedMetadataDto = when (this) {
    is ResolvedMetadata.MusicVideo -> toDto()
    is ResolvedMetadata.SeriesEpisode -> toDto()
    is ResolvedMetadata.Other -> toDto()
}
```

### 8.6 ResolvedMetadataToDomain.kt

```kotlin
// --- Подтипы ---

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

// --- Супертип ---

fun ResolvedMetadataDto.toDomain(): Either<DomainError.ValidationError, ResolvedMetadata> = when (this) {
    is ResolvedMetadataDto.MusicVideo -> toDomain()
    is ResolvedMetadataDto.SeriesEpisode -> toDomain()
    is ResolvedMetadataDto.Other -> toDomain()
}
```

> **Принцип**: каждый подтип имеет свою `toDto()` / `toDomain()` функцию с точным возвращаемым типом.
> Супертип делегирует через exhaustive `when`. Это позволяет:
> - Вызывать типизированный маппинг напрямую, когда подтип известен
> - Компилятор проверяет exhaustiveness при добавлении нового подтипа

### 8.3 Маппинг ошибок

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

## 9. Версионирование

### 9.1 Текущая версия

`v1`

### 9.2 Правила совместимости

**Допустимо в v1**:
- Добавление новых optional полей в response
- Добавление новых эндпоинтов
- Добавление новых `type` для sealed DTO

**Требует v2**:
- Удаление полей
- Переименование полей
- Изменение типов полей
- Изменение семантики существующих полей

### 9.3 Обработка неизвестных type

Клиент должен:
1. Игнорировать неизвестные поля (`ignoreUnknownKeys = true`)
2. При неизвестном `type` для `ResolvedMetadataDto` — fallback на `Other`
3. При неизвестном `type` для `RuleMatchDto` — ошибка (правила критичны)

---

## 10. Workspace

### 10.1 GET /api/v1/workspaces

Список workspaces текущего пользователя.

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

Создать workspace. Создатель автоматически становится OWNER.

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

Список участников workspace.

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

Добавить участника в workspace. Только OWNER.

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

Удалить участника из workspace. Только OWNER.

**Resource**: `ApiV1.Workspaces.ById.Members.ByUserId`

#### Response

`204 No Content`

Подробнее о workspace: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 11. System

### 11.1 GET /api/v1/system/yt-dlp/status

Получить текущую версию yt-dlp и информацию о доступных обновлениях.

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

Запустить процесс обновления yt-dlp.

**Response (202 Accepted):**
```json
{
  "status": "UPDATING",
  "message": "Update process started"
}
```

**Response (403 Forbidden):**
Если `ytDlp.allowUpdate: false`.
```json
{
  "error": {
    "code": "UPDATE_DISABLED",
    "message": "Update is disabled by administrator",
    "correlationId": "..."
  }
}
```
