# API Contract

> **Цель документа**: Полная спецификация HTTP API, DTO, сериализация sealed-классов, формат ошибок.

---

## 1. Общие правила

### 1.1 Base URL

```
/api/v1/
```

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

| Code                | HTTP Status | Описание                                   |
|---------------------|-------------|--------------------------------------------|
| `VALIDATION_ERROR`  | 400         | Ошибка валидации входных данных            |
| `INVALID_URL`       | 400         | Некорректный URL видео                     |
| `UNAUTHORIZED`      | 401         | Невалидный initData                        |
| `FORBIDDEN`         | 403         | Пользователь не в allowlist                |
| `NOT_FOUND`         | 404         | Ресурс не найден                           |
| `CONFLICT`          | 409         | Конфликт (например, job уже существует)    |
| `UPDATE_DISABLED`   | 403         | Обновление yt-dlp запрещено в конфигурации |
| `VIDEO_UNAVAILABLE` | 422         | Видео недоступно                           |
| `LLM_ERROR`         | 502         | Ошибка при обращении к LLM провайдеру      |
| `INTERNAL_ERROR`    | 500         | Внутренняя ошибка сервера                  |

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

### 6.1 POST /api/v1/preview

Получить preview метаданных для URL.

#### Request

```kotlin
@Serializable
data class PreviewRequestDto(
    val url: String,
)
```

```json
{
  "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

#### Response

```kotlin
@Serializable
data class PreviewResponseDto(
    val source: VideoSourceDto,
    val videoInfo: VideoInfoDto,
    val matchedRule: RuleSummaryDto?,
    val metadataSource: MetadataSourceDto, // RULE | LLM | FALLBACK
    val category: String,
    val metadata: ResolvedMetadataDto,
    val storagePlan: StoragePlanDto,
    val warnings: List<String>,
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

### 6.2 POST /api/v1/jobs

Создать job.

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

### 6.3 GET /api/v1/jobs

Список jobs.

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

### 6.4 GET /api/v1/jobs/{id}

Получить job по ID.

#### Response

`JobDto`

#### Ошибки

- `404 NOT_FOUND`

---

### 6.5 POST /api/v1/jobs/{id}/cancel

Отменить job.

#### Response

`JobDto` с обновлённым статусом.

#### Ошибки

- `404 NOT_FOUND`
- `409 CONFLICT` — job уже завершён

---

### 6.6 GET /api/v1/rules

Список правил.

#### Response

```kotlin
@Serializable
data class RuleListResponseDto(
    val items: List<RuleDto>,
)
```

---

### 6.7 POST /api/v1/rules

Создать правило.

#### Request

```kotlin
@Serializable
data class CreateRuleRequestDto(
    val enabled: Boolean = true,
    val priority: Int = 0,
    val match: RuleMatchDto,
    val category: String,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val storagePolicy: StoragePolicyDto,
    val postProcessPolicy: PostProcessPolicyDto,
)
```

#### Response

`RuleDto`

---

### 6.8 GET /api/v1/rules/{id}

Получить правило по ID.

---

### 6.9 PUT /api/v1/rules/{id}

Обновить правило.

---

### 6.10 DELETE /api/v1/rules/{id}

Удалить (или деактивировать) правило.

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
    val path: String,
    val format: String,  // "original/webm", "video/mp4", "audio/m4a", "image/jpg"
)
```

> `format` — строка вида `"kind/extension"`. Маппинг в domain: `OutputFormat.parse(format)` / `outputFormat.serialized`.

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
    val enabled: Boolean,
    val priority: Int,
    val match: RuleMatchDto,
    val category: String,
    val metadataTemplate: MetadataTemplateDto,
    val downloadPolicy: DownloadPolicyDto,
    val storagePolicy: StoragePolicyDto,
    val postProcessPolicy: PostProcessPolicyDto,
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
    val maxQuality: String = "best",
    val preferredContainer: String? = null,   // "mp4", "mkv", "webm", ...
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
)

@Serializable
data class OutputTemplateDto(
    val pathTemplate: String,
    val format: String,  // "video/mp4", "audio/m4a", "image/jpg"
)

@Serializable
data class StoragePolicyDto(
    val originalTemplate: String,
    val additionalOutputs: List<OutputTemplateDto> = emptyList(),
)

@Serializable
data class PostProcessPolicyDto(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
)
```

---

## 8. Маппинг Domain ↔ DTO

### 8.1 Расположение

Модуль: `api:mapping`

### 8.2 Структура файлов

```
api/mapping/src/commonMain/kotlin/io/github/alelk/tgvd/api/mapping/
├── RuleMatchToDto.kt
├── RuleMatchToDomain.kt
├── ResolvedMetadataToDto.kt
├── ResolvedMetadataToDomain.kt
├── MetadataTemplateToDto.kt
├── MetadataTemplateToDomain.kt
├── VideoInfoToDto.kt
├── VideoInfoToDomain.kt
├── StoragePlanToDto.kt
├── StoragePlanToDomain.kt
└── DomainErrorToApiError.kt
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
    is DomainError.RuleNotFound, is DomainError.JobNotFound -> 
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

## 10. System

### 10.1 GET /api/v1/system/yt-dlp/status

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

### 10.2 POST /api/v1/system/yt-dlp/update

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
