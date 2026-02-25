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

| Code | HTTP Status | Описание |
|------|-------------|----------|
| `VALIDATION_ERROR` | 400 | Ошибка валидации входных данных |
| `INVALID_URL` | 400 | Некорректный URL видео |
| `UNAUTHORIZED` | 401 | Невалидный initData |
| `FORBIDDEN` | 403 | Пользователь не в allowlist |
| `NOT_FOUND` | 404 | Ресурс не найден |
| `CONFLICT` | 409 | Конфликт (например, job уже существует) |
| `UPDATE_DISABLED` | 403 | Обновление yt-dlp запрещено в конфигурации |
| `VIDEO_UNAVAILABLE` | 422 | Видео недоступно |
| `INTERNAL_ERROR` | 500 | Внутренняя ошибка сервера |

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
    @SerialName("allOf")
    data class AllOf(
        val matches: List<RuleMatchDto>,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("anyOf")
    data class AnyOf(
        val matches: List<RuleMatchDto>,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("channelId")
    data class ChannelId(
        val value: String,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("channelName")
    data class ChannelName(
        val value: String,
        val ignoreCase: Boolean = true,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("titleRegex")
    data class TitleRegex(
        val pattern: String,
    ) : RuleMatchDto
    
    @Serializable
    @SerialName("urlRegex")
    data class UrlRegex(
        val pattern: String,
    ) : RuleMatchDto
}
```

### 4.2 JSON примеры

**ChannelId**:
```json
{
  "type": "channelId",
  "value": "UCq-Fj5jknLsUf-MWSy4_brA"
}
```

**AllOf (AND)**:
```json
{
  "type": "allOf",
  "matches": [
    { "type": "channelName", "value": "Kurzgesagt" },
    { "type": "titleRegex", "pattern": ".*Documentary.*" }
  ]
}
```

**AnyOf (OR)**:
```json
{
  "type": "anyOf",
  "matches": [
    { "type": "channelId", "value": "UC123" },
    { "type": "channelId", "value": "UC456" }
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
    val year: Int?
    val tags: List<String>
    val comment: String?
    
    @Serializable
    @SerialName("musicVideo")
    data class MusicVideo(
        val artist: String,
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
    
    @Serializable
    @SerialName("seriesEpisode")
    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
    
    @Serializable
    @SerialName("other")
    data class Other(
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadataDto
}
```

### 5.2 JSON примеры

**MusicVideo**:
```json
{
  "type": "musicVideo",
  "artist": "Rick Astley",
  "title": "Never Gonna Give You Up",
  "year": 1987,
  "tags": ["80s", "pop"],
  "comment": null
}
```

**SeriesEpisode**:
```json
{
  "type": "seriesEpisode",
  "seriesName": "Kurzgesagt",
  "season": "2024",
  "episode": "01",
  "title": "The Egg",
  "year": 2024,
  "tags": ["science", "animation"],
  "comment": null
}
```

**Other**:
```json
{
  "type": "other",
  "title": "Random Video Title",
  "year": null,
  "tags": [],
  "comment": null
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
  "metadataSource": "RULE",
  "category": "MUSIC_VIDEO",
  "metadata": {
    "type": "musicVideo",
    "artist": "Rick Astley",
    "title": "Never Gonna Give You Up",
    "year": 2009,
    "tags": ["music", "official"],
    "comment": null
  },
  "storagePlan": {
    "original": {
      "path": "/media/Music Videos/original/Rick Astley/Never Gonna Give You Up [dQw4w9WgXcQ].mp4",
      "container": "mp4",
      "kind": "ORIGINAL"
    },
    "converted": {
      "path": "/media/Music Videos/Rick Astley/Never Gonna Give You Up.mp4",
      "container": "mp4",
      "kind": "CONVERTED"
    },
    "additional": []
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
  "category": "MUSIC_VIDEO",
  "videoInfo": { ... },
  "metadata": {
    "type": "musicVideo",
    "artist": "Rick Astley",
    "title": "Never Gonna Give You Up",
    "year": 1987,
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
    val extractor: String = "youtube",
)
```

### 7.2 VideoInfoDto

```kotlin
@Serializable
data class VideoInfoDto(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val uploadDate: String?,  // YYYY-MM-DD
    val durationSeconds: Int,
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
    val original: OutputTargetDto?,
    val converted: OutputTargetDto?,
    val additional: List<OutputTargetDto> = emptyList(),
)

@Serializable
data class OutputTargetDto(
    val path: String,
    val container: String,
    val kind: String,
)
```

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
data class MetadataTemplateDto(
    val artistPattern: String? = null,
    val titlePattern: String? = null,
    val seriesNameOverride: String? = null,
    val defaultTags: List<String> = emptyList(),
)

@Serializable
data class DownloadPolicyDto(
    val maxQuality: String = "BEST",
    val preferredFormat: String? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
)

@Serializable
data class StoragePolicyDto(
    val originalTemplate: String?,
    val convertedTemplate: String?,
    val audioOnlyTemplate: String? = null,
)

@Serializable
data class PostProcessPolicyDto(
    val convertToMp4: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
    val extractAudio: Boolean = false,
    val audioFormat: String = "m4a",
)
```

---

## 8. Маппинг Domain ↔ DTO

### 8.1 Расположение

Модуль: `api-mapping`

### 8.2 Примеры

```kotlin
// RuleMatch -> RuleMatchDto
fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.AllOf -> RuleMatchDto.AllOf(matches.map { it.toDto() })
    is RuleMatch.AnyOf -> RuleMatchDto.AnyOf(matches.map { it.toDto() })
    is RuleMatch.ChannelId -> RuleMatchDto.ChannelId(value)
    is RuleMatch.ChannelName -> RuleMatchDto.ChannelName(value, ignoreCase)
    is RuleMatch.TitleRegex -> RuleMatchDto.TitleRegex(pattern)
    is RuleMatch.UrlRegex -> RuleMatchDto.UrlRegex(pattern)
}

// RuleMatchDto -> RuleMatch (с валидацией)
fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.AllOf -> {
        if (matches.isEmpty()) {
            DomainError.ValidationError("matches", "Cannot be empty").left()
        } else {
            matches.traverse { it.toDomain() }.map { RuleMatch.AllOf(it) }
        }
    }
    is RuleMatchDto.ChannelId -> {
        if (value.isBlank()) {
            DomainError.ValidationError("value", "Cannot be blank").left()
        } else {
            RuleMatch.ChannelId(value).right()
        }
    }
    // ... остальные варианты
}
```

```kotlin
// ResolvedMetadata -> ResolvedMetadataDto
fun ResolvedMetadata.toDto(): ResolvedMetadataDto = when (this) {
    is ResolvedMetadata.MusicVideo -> ResolvedMetadataDto.MusicVideo(
        artist = artist,
        title = title,
        year = year,
        tags = tags,
        comment = comment,
    )
    is ResolvedMetadata.SeriesEpisode -> ResolvedMetadataDto.SeriesEpisode(
        seriesName = seriesName,
        season = season,
        episode = episode,
        title = title,
        year = year,
        tags = tags,
        comment = comment,
    )
    is ResolvedMetadata.Other -> ResolvedMetadataDto.Other(
        title = title,
        year = year,
        tags = tags,
        comment = comment,
    )
}
```

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
