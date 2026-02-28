# Доменная модель

> **Цель документа**: Полное описание доменных сущностей, sealed-иерархий, value objects и инвариантов.

---

## 1. Обзор

Домен — ядро приложения. Он **не зависит** от фреймворков, БД, сериализации.

**Модуль**: `domain` — **Kotlin Multiplatform** (targets: `jvm`, `js`).

Весь код размещается в `commonMain` source set. Platform-specific код не допускается.

### Структура: Package-by-Feature

Домен организован **по фичам**, а не по техническим слоям. Каждый пакет автономен, 
содержит свои модели, сервисы и порты. 
Такая структура:
- Повышает cohesion (связанные классы рядом)
- Упрощает навигацию (всё про правила — в `rule/`)
- Каждый пакет потенциально может стать отдельным модулем
- Нет циклических зависимостей между пакетами

```
domain/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── common/             # Общие типы: Category, DomainError, value objects (WorkspaceId, JobId, etc.)
├── workspace/          # Workspace, WorkspaceMember, WorkspaceRole, WorkspaceRepository port
├── video/              # VideoSource, VideoInfo, VideoInfoExtractor port
├── rule/               # Rule, RuleMatch, RuleMatchingService, RuleRepository port
├── metadata/           # ResolvedMetadata, MetadataResolver, MetadataTemplate, LlmPort
├── storage/            # StoragePlan, StoragePolicy, PathTemplateEngine
├── job/                # Job, JobStatus, CreateJobUseCase, JobRepository port
└── preview/            # PreviewUseCase (оркестрация video + rule + metadata + storage)
```

### Граф зависимостей между пакетами

```
                    preview
                   ╱   │   ╲
                  ╱    │    ╲
              rule  metadata  storage
                ╲      │      ╱
                 ╲     │     ╱
                   video
                     │
                   common
                     │
              workspace ──▶ common

              job ──▶ video, storage, common
```

> Стрелки = зависит от. Циклов нет. Каждый пакет при необходимости может быть извлечён в Gradle-модуль.

**KMP-замечания**:
- `kotlin.uuid.Uuid` вместо `java.util.UUID`
- `kotlin.time.Instant` для timestamps (в stdlib с Kotlin 2.1.20+)
- `kotlin.time.Duration` для длительностей (в stdlib)
- `LocalDate`, `Url`, `FilePath` — собственные value classes (Kotlin stdlib не содержит KMP-совместимых аналогов)
- `value class` поддерживается на JS с Kotlin 2.1+

---

## 2. `common` — Общие типы

### 2.1 Value Objects

```kotlin
/**
 * Идентификатор видео на платформе-источнике.
 * Примеры: "dQw4w9WgXcQ" (YouTube), "12345678" (RuTube), "-12345_67890" (VK).
 */
@JvmInline
value class VideoId(val value: String) {
    init {
        require(value.isNotBlank()) { "VideoId cannot be blank" }
        require(value.length <= 64) { "VideoId too long" }
    }
}

@JvmInline
value class ChannelId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChannelId cannot be blank" }
    }
}

@JvmInline
value class RuleId(val value: Uuid)  // kotlin.uuid.Uuid

@JvmInline
value class JobId(val value: Uuid)

@JvmInline
value class WorkspaceId(val value: Uuid)

@JvmInline
value class TelegramUserId(val value: Long) {
    init {
        require(value > 0) { "TelegramUserId must be positive" }
    }
}

/**
 * Идентификатор экстрактора yt-dlp (название платформы-источника).
 * Примеры: "youtube", "rutube", "vk", "generic".
 * Определяется автоматически yt-dlp при извлечении метаданных.
 */
@JvmInline
value class Extractor(val value: String) {
    init {
        require(value.isNotBlank()) { "Extractor cannot be blank" }
    }
    
    companion object {
        val YOUTUBE = Extractor("youtube")
        val RUTUBE = Extractor("rutube")
        val VK = Extractor("vk")
        val GENERIC = Extractor("generic")
    }
}

/**
 * URL видео. Базовая валидация без внешних библиотек.
 * Kotlin stdlib не содержит KMP-совместимого типа URL.
 */
@JvmInline
value class Url(val value: String) {
    init {
        require(value.isNotBlank()) { "URL cannot be blank" }
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "URL must start with http:// or https://"
        }
    }
}

/**
 * Дата в формате ISO 8601 (YYYY-MM-DD).
 * Kotlin stdlib не содержит KMP-совместимого типа LocalDate.
 */
@JvmInline
value class LocalDate(val value: String) {
    init {
        require(ISO_DATE_REGEX.matches(value)) { "LocalDate must be in ISO 8601 format (YYYY-MM-DD): $value" }
    }

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()
    val day: Int get() = value.substring(8, 10).toInt()

    companion object {
        private val ISO_DATE_REGEX = "^\\d{4}-\\d{2}-\\d{2}$".toRegex()
    }
}

/**
 * Путь к файлу или директории.
 * Kotlin stdlib не содержит KMP-совместимого типа Path.
 * Используется в domain вместо java.nio.file.Path.
 */
@JvmInline
value class FilePath(val value: String) {
    init {
        require(value.isNotBlank()) { "FilePath cannot be blank" }
    }
    
    val fileName: String get() = value.substringAfterLast('/')
    val parent: String get() = value.substringBeforeLast('/', "")
    val extension: String get() = fileName.substringAfterLast('.', "")
}
```

### 2.2 Category

```kotlin
enum class Category {
    MUSIC_VIDEO,
    SERIES,
    OTHER;
    
    companion object {
        fun fromString(value: String): Category? = 
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}
```

### 2.3 DomainError

```kotlin
sealed interface DomainError {
    val message: String
    
    // === Validation ===
    data class ValidationError(val field: String, override val message: String) : DomainError
    data class InvalidUrl(val url: Url, override val message: String = "Invalid URL: ${url.value}") : DomainError
    
    // === Not Found ===
    data class RuleNotFound(val id: RuleId, override val message: String = "Rule not found: ${id.value}") : DomainError
    data class JobNotFound(val id: JobId, override val message: String = "Job not found: ${id.value}") : DomainError
    
    // === Video ===
    data class VideoUnavailable(val videoId: VideoId, val reason: String, override val message: String = "Video unavailable: ${videoId.value} - $reason") : DomainError
    data class VideoExtractionFailed(val url: Url, val cause: String, override val message: String = "Failed to extract video info: $cause") : DomainError
    
    // === Job ===
    data class JobAlreadyExists(val videoId: VideoId, val existingJobId: JobId, override val message: String = "Job already exists for video ${videoId.value}") : DomainError
    data class JobCannotBeCancelled(val id: JobId, val currentStatus: JobStatus, override val message: String = "Cannot cancel job in status $currentStatus") : DomainError
    data class DownloadFailed(val jobId: JobId, val cause: String, override val message: String = "Download failed: $cause") : DomainError
    data class PostProcessingFailed(val jobId: JobId, val phase: JobPhase, val cause: String, override val message: String = "Post-processing failed at $phase: $cause") : DomainError
    
    // === Storage ===
    data class PathTraversalAttempt(val path: FilePath, override val message: String = "Path traversal attempt: ${path.value}") : DomainError
    data class StorageFailed(val path: FilePath, val cause: String, override val message: String = "Storage failed for ${path.value}: $cause") : DomainError
    
    // === Auth ===
    data class Unauthorized(override val message: String = "Unauthorized") : DomainError
    data class Forbidden(val userId: TelegramUserId, override val message: String = "User ${userId.value} not allowed") : DomainError
    
    // === Workspace ===
    data class WorkspaceNotFound(val id: WorkspaceId, override val message: String = "Workspace not found: ${id.value}") : DomainError
    data class WorkspaceAccessDenied(val workspaceId: WorkspaceId, val userId: TelegramUserId, override val message: String = "User ${userId.value} is not a member of workspace ${workspaceId.value}") : DomainError

    // === LLM ===
    data class LlmError(val provider: String, override val message: String, val statusCode: Int? = null) : DomainError
}
```

> `DomainError` — в `common/`, т.к. используется во всех пакетах. 
> `JobStatus` импортируется из `job/` для `JobCannotBeCancelled` 
> — это единственная обратная ссылка, допустимая т.к. это sealed subclass, а не бизнес-зависимость.

---

## 3. `workspace` — Рабочее пространство

Зависимости: `common`

```
domain/workspace/
├── Workspace.kt
├── WorkspaceMember.kt
├── WorkspaceRole.kt
└── WorkspaceRepository.kt
```

Workspace — группа пользователей с общими ресурсами. Все доменные сущности (Rule, Job) привязаны к workspace.

### 3.1 Workspace

```kotlin
data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val createdAt: Instant,
)
```

### 3.2 WorkspaceMember

```kotlin
data class WorkspaceMember(
    val workspaceId: WorkspaceId,
    val userId: TelegramUserId,
    val role: WorkspaceRole,
    val joinedAt: Instant,
)
```

### 3.3 WorkspaceRole

```kotlin
enum class WorkspaceRole {
    /** Может управлять участниками (добавлять/удалять) */
    OWNER,
    /** Полный доступ ко всем ресурсам workspace */
    MEMBER,
}
```

> Обе роли имеют равный доступ к ресурсам. OWNER дополнительно может управлять составом workspace.

### 3.4 WorkspaceRepository (port)

```kotlin
interface WorkspaceRepository {
    suspend fun findById(id: WorkspaceId): Workspace?
    suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember>
    suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember>
    suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
    suspend fun save(workspace: Workspace): Either<DomainError, Workspace>
    suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember>
    suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
}
```

Подробнее: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 4. `video` — Видео

Зависимости: `common`

```
domain/video/
├── VideoSource.kt
├── VideoInfo.kt
└── VideoInfoExtractor.kt    # port
```

### 4.1 VideoSource

```kotlin
data class VideoSource(
    val url: Url,
    val videoId: VideoId,
    val extractor: Extractor,
)
```

> `extractor` определяется автоматически yt-dlp. Поддерживается [1000+ сайтов](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

### 4.2 VideoInfo

```kotlin
data class VideoInfo(
    val videoId: VideoId,
    val extractor: Extractor,
    val title: String,
    val channelId: ChannelId,
    val channelName: String,
    val uploadDate: LocalDate?,
    val duration: Duration,          // kotlin.time.Duration (KMP, в stdlib)
    val webpageUrl: Url,
    val thumbnails: List<Thumbnail> = emptyList(),
    val description: String? = null,
    val viewCount: Long? = null,
) {
    data class Thumbnail(val url: Url, val width: Int?, val height: Int?)
}
```

### 4.3 VideoInfoExtractor (port)

```kotlin
interface VideoInfoExtractor {
    suspend fun extract(url: String): Either<DomainError, VideoInfo>
}
```

---

## 5. `rule` — Правила

Зависимости: `common`, `video`

```
domain/rule/
├── Rule.kt
├── RuleMatch.kt               # sealed interface
├── RuleMatchingService.kt
└── RuleRepository.kt          # port
```

### 5.1 RuleMatch (sealed)

```kotlin
sealed interface RuleMatch {
    
    data class AllOf(val matches: List<RuleMatch>) : RuleMatch {
        init { require(matches.isNotEmpty()) { "AllOf cannot be empty" } }
    }
    
    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch {
        init { require(matches.isNotEmpty()) { "AnyOf cannot be empty" } }
    }
    
    data class ChannelId(val value: String) : RuleMatch {
        init { require(value.isNotBlank()) { "ChannelId value cannot be blank" } }
    }
    
    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatch {
        init { require(value.isNotBlank()) { "ChannelName value cannot be blank" } }
    }
    
    data class TitleRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }
        init {
            require(pattern.isNotBlank()) { "TitleRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse { 
                throw IllegalArgumentException("Invalid regex: $pattern", it) 
            }
        }
    }
    
    data class UrlRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }
        init {
            require(pattern.isNotBlank()) { "UrlRegex pattern cannot be blank" }
            runCatching { pattern.toRegex() }.getOrElse { 
                throw IllegalArgumentException("Invalid regex: $pattern", it) 
            }
        }
    }
}
```

**Матчинг и специфичность**:

```kotlin
fun RuleMatch.matches(video: VideoInfo): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(video) }
    is RuleMatch.AnyOf -> matches.any { it.matches(video) }
    is RuleMatch.ChannelId -> video.channelId.value == value
    is RuleMatch.ChannelName -> video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(video.webpageUrl.value)
}

fun RuleMatch.specificity(): Int = when (this) {
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.specificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.specificity() } ?: 0
}
```

### 5.2 Rule

```kotlin
data class Rule(
    val id: RuleId,
    val name: String,
    val workspaceId: WorkspaceId,
    val enabled: Boolean,
    val priority: Int,
    val match: RuleMatch,
    val category: Category,
    val metadataTemplate: MetadataTemplate,
    val downloadPolicy: DownloadPolicy,
    val storagePolicy: StoragePolicy,
    val postProcessPolicy: PostProcessPolicy,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

> `Rule` ссылается на `MetadataTemplate` (из `metadata/`), 
> `StoragePolicy` (из `storage/`), `DownloadPolicy` и `PostProcessPolicy` (из `storage/`). 
> Это допустимо: `rule` зависит от `metadata` и `storage`, но не наоборот.

### 5.3 RuleMatchingService

```kotlin
class RuleMatchingService(
    private val ruleRepository: RuleRepository,
) {
    suspend fun findMatchingRule(video: VideoInfo, workspaceId: WorkspaceId): Rule? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        return rules
            .filter { it.match.matches(video) }
            .maxByOrNull { it.priority * 1000 + it.match.specificity() }
    }
}
```

### 5.4 RuleRepository (port)

```kotlin
interface RuleRepository {
    suspend fun findById(id: RuleId): Rule?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Rule>
    suspend fun findEnabledByWorkspace(workspaceId: WorkspaceId): List<Rule>
    suspend fun save(rule: Rule): Either<DomainError, Rule>
    suspend fun delete(id: RuleId): Boolean
}
```

---

## 6. `metadata` — Метаданные

Зависимости: `common`, `video`

```
domain/metadata/
├── ResolvedMetadata.kt        # sealed interface
├── MetadataSource.kt          # enum
├── MetadataTemplate.kt
├── MetadataResolver.kt
├── LlmSuggestion.kt
└── LlmPort.kt                # port
```

### 6.1 MetadataSource

```kotlin
enum class MetadataSource { RULE, LLM, FALLBACK }
```

### 6.2 ResolvedMetadata (sealed)

```kotlin
sealed interface ResolvedMetadata {
    val title: String
    val releaseDate: LocalDate?
    val tags: List<String>
    val comment: String?
    
    /** Год выпуска (из releaseDate). Удобно для path templates: {year} */
    val year: Int? get() = releaseDate?.year
    
    data class MusicVideo(
        val artist: String,
        override val title: String,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(artist.isNotBlank()) { "Artist cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
        }
    }
    
    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(seriesName.isNotBlank()) { "SeriesName cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
        }
    }
    
    data class Other(
        override val title: String,
        override val releaseDate: LocalDate? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init { require(title.isNotBlank()) { "Title cannot be blank" } }
    }
}

val ResolvedMetadata.category: Category get() = when (this) {
    is ResolvedMetadata.MusicVideo -> Category.MUSIC_VIDEO
    is ResolvedMetadata.SeriesEpisode -> Category.SERIES
    is ResolvedMetadata.Other -> Category.OTHER
}
```

### 6.3 MetadataTemplate (sealed)

```kotlin
/**
 * Шаблон для определения метаданных видео.
 * 
 * Sealed по категории — каждый подтип содержит только релевантные поля.
 * Зеркалит структуру [ResolvedMetadata]: MusicVideo → MusicVideo, и т.д.
 * 
 * **Override-поля** — жёстко задают значение (приоритет над извлечением).
 * **Pattern-поля** — regex для извлечения из title/description видео.
 * 
 * Приоритет: override > pattern > fallback (парсинг по разделителям).
 */
sealed interface MetadataTemplate {
    val titleOverride: String?
    val titlePattern: String?
    val defaultTags: List<String>
    
    data class MusicVideo(
        val artistOverride: String? = null,       // e.g. "Casting Crowns"
        val artistPattern: String? = null,        // regex с группой, e.g. "^(.+?)\\s*[-–—]"
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate
    
    data class SeriesEpisode(
        val seriesNameOverride: String? = null,   // e.g. "Tech News Weekly"
        val seasonPattern: String? = null,        // regex для извлечения сезона
        val episodePattern: String? = null,       // regex для извлечения эпизода
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate
    
    data class Other(
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate
}
```

> **Пример**: канал "Casting Crowns" — правило с `category = MUSIC_VIDEO`,
> `metadataTemplate = MetadataTemplate.MusicVideo(artistOverride = "Casting Crowns")`.
> Видео "Who Am I (Official Music Video)" → `artist = "Casting Crowns"`, `title = "Who Am I (Official Music Video)"`.
> Невозможно случайно задать `artistOverride` для `SERIES` — компилятор не даст.

### 6.4 MetadataResolver

```kotlin
class MetadataResolver {
    
    fun resolve(video: VideoInfo, template: MetadataTemplate): ResolvedMetadata =
        when (template) {
            is MetadataTemplate.MusicVideo -> resolveMusicVideo(video, template)
            is MetadataTemplate.SeriesEpisode -> resolveSeriesEpisode(video, template)
            is MetadataTemplate.Other -> resolveOther(video, template)
        }
    
    private fun resolveMusicVideo(
        video: VideoInfo, template: MetadataTemplate.MusicVideo,
    ): ResolvedMetadata.MusicVideo {
        val (fallbackArtist, fallbackTitle) = parseArtistTitle(video.title)
        
        val artist = template.artistOverride                                           // 1. override
            ?: template.artistPattern?.let { extractByPattern(video.title, it) }       // 2. pattern
            ?: fallbackArtist                                                           // 3. fallback
        
        val title = template.titleOverride
            ?: template.titlePattern?.let { extractByPattern(video.title, it) }
            ?: fallbackTitle
        
        return ResolvedMetadata.MusicVideo(
            artist = artist,
            title = title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )
    }
    
    private fun resolveSeriesEpisode(
        video: VideoInfo, template: MetadataTemplate.SeriesEpisode,
    ): ResolvedMetadata.SeriesEpisode {
        val seriesName = template.seriesNameOverride ?: video.channelName
        val season = template.seasonPattern?.let { extractByPattern(video.title, it) }
        val episode = template.episodePattern?.let { extractByPattern(video.title, it) }
        
        return ResolvedMetadata.SeriesEpisode(
            seriesName = seriesName,
            season = season,
            episode = episode,
            title = template.titleOverride ?: video.title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )
    }
    
    private fun resolveOther(
        video: VideoInfo, template: MetadataTemplate.Other,
    ): ResolvedMetadata.Other =
        ResolvedMetadata.Other(
            title = template.titleOverride ?: video.title,
            releaseDate = video.uploadDate,
            tags = template.defaultTags,
        )
    
    /** Извлечение первой группы из regex-паттерна */
    private fun extractByPattern(input: String, pattern: String): String? =
        runCatching { pattern.toRegex().find(input)?.groupValues?.getOrNull(1)?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    
    /** Fallback: парсинг "Artist - Title" по разделителям */
    private fun parseArtistTitle(title: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", ": ")
        for (sep in separators) {
            if (sep in title) {
                val parts = title.split(sep, limit = 2)
                return parts[0].trim() to parts[1].trim()
            }
        }
        return "Unknown Artist" to title
    }
}
```

### 6.5 LlmPort (port) & LlmSuggestion

```kotlin
interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}

data class LlmSuggestion(
    val category: Category,
    val metadata: ResolvedMetadata,
    val confidence: Double,
)
```

---

## 7. `storage` — Хранение и пост-обработка

Зависимости: `common`, `video`, `metadata`

```
domain/storage/
├── MediaContainer.kt
├── AudioFormat.kt
├── ImageFormat.kt
├── OutputFormat.kt            # sealed interface
├── OutputTarget.kt
├── OutputTemplate.kt
├── StoragePlan.kt
├── StoragePolicy.kt
├── DownloadPolicy.kt
├── PostProcessPolicy.kt
├── PathTemplateEngine.kt
└── VideoDownloader.kt         # port
```

### 7.1 MediaContainer, AudioFormat, ImageFormat

```kotlin
/** Видео/медиа контейнер. Поддерживаемые форматы yt-dlp + ffmpeg. */
enum class MediaContainer(val extension: String) {
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm"),
    AVI("avi"),
    MOV("mov");
    
    companion object {
        fun fromExtension(ext: String): MediaContainer? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }
    }
}

/** Аудио формат для извлечения аудио-дорожки. */
enum class AudioFormat(val extension: String) {
    M4A("m4a"),
    MP3("mp3"),
    OPUS("opus"),
    FLAC("flac"),
    WAV("wav");
}

/** Формат изображения (для обложек/thumbnail). */
enum class ImageFormat(val extension: String) {
    JPG("jpg"),
    PNG("png"),
    WEBP("webp");
}
```

### 7.2 OutputFormat (sealed)

```kotlin
/**
 * Формат выходного файла. Sealed — кодирует и тип (видео/аудио/изображение), и конкретный формат.
 *
 * Сериализуется в строку вида "kind/extension":
 *   - "original/webm", "original/mkv"
 *   - "video/mp4", "video/mkv"
 *   - "audio/m4a", "audio/mp3", "audio/flac"
 *   - "image/jpg", "image/png", "image/webp"
 * 
 * Строковое представление используется в API (JSON), БД (TEXT) и конфигурации (YAML).
 */
sealed interface OutputFormat {
    val extension: String
    
    /** Оригинальное видео (как скачано yt-dlp). */
    data class OriginalVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }
    
    /** Конвертированное видео (после ffmpeg). */
    data class ConvertedVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }
    
    /** Извлечённая аудио-дорожка. */
    data class Audio(val format: AudioFormat) : OutputFormat {
        override val extension: String get() = format.extension
    }
    
    /** Обложка / thumbnail. */
    data class Thumbnail(val format: ImageFormat = ImageFormat.JPG) : OutputFormat {
        override val extension: String get() = format.extension
    }
    
    /** Сериализация в строку "kind/extension". */
    val serialized: String get() = when (this) {
        is OriginalVideo -> "original/${container.extension}"
        is ConvertedVideo -> "video/${container.extension}"
        is Audio -> "audio/${format.extension}"
        is Thumbnail -> "image/${format.extension}"
    }
    
    companion object {
        /**
         * Десериализация из строки "kind/extension".
         * @throws IllegalArgumentException при невалидном формате.
         */
        fun parse(value: String): OutputFormat {
            val (kind, ext) = value.split("/", limit = 2).also {
                require(it.size == 2) { "Invalid OutputFormat: '$value', expected 'kind/extension'" }
            }
            return when (kind) {
                "original" -> OriginalVideo(
                    MediaContainer.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "video" -> ConvertedVideo(
                    MediaContainer.fromExtension(ext) ?: error("Unknown container: $ext")
                )
                "audio" -> AudioFormat.entries.find { it.extension == ext }
                    ?.let { Audio(it) }
                    ?: error("Unknown audio format: $ext")
                "image" -> ImageFormat.entries.find { it.extension == ext }
                    ?.let { Thumbnail(it) }
                    ?: error("Unknown image format: $ext")
                else -> error("Unknown OutputFormat kind: $kind")
            }
        }
    }
}
```

### 7.3 StoragePlan & OutputTarget

```kotlin
data class OutputTarget(
    val path: FilePath,
    val format: OutputFormat,
)

/**
 * План сохранения файлов.
 * 
 * [original] — исходное видео (как скачано yt-dlp). Всегда один.
 * [additional] — производные: конвертированное видео, аудио-дорожка, обложка и т.д.
 * Тип каждого определяется через [OutputFormat] (sealed).
 *
 * Пример для MUSIC_VIDEO:
 *   original  = .../original/Artist/Title.webm       (OriginalVideo)
 *   additional = [
 *     .../converted/Artist/Title.mp4                  (ConvertedVideo)
 *     .../audio/Artist/Title.m4a                      (Audio)
 *   ]
 */
data class StoragePlan(
    val original: OutputTarget,
    val additional: List<OutputTarget> = emptyList(),
) {
    val allTargets: List<OutputTarget> get() = listOf(original) + additional
}
```

### 7.4 DownloadPolicy

```kotlin
data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    val preferredContainer: MediaContainer? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
) {
    enum class VideoQuality { BEST, HD_1080, HD_720, SD_480 }
}
```

### 7.5 StoragePolicy

```kotlin
/**
 * Шаблон пути для одного выходного файла.
 * Связывает path template с форматом выходного файла.
 */
data class OutputTemplate(
    val pathTemplate: String,         // e.g. "/media/Music Videos/converted/{artist}/{title}.mp4"
    val format: OutputFormat,         // тип + формат выходного файла
)

/**
 * Политика хранения файлов.
 * 
 * [originalTemplate] — шаблон для исходного файла (как скачано yt-длп).
 * [additionalOutputs] — производные файлы: конвертированное видео, аудио, thumbnail.
 * Каждый [OutputTemplate] привязывает path template к [OutputFormat].
 * 
 * Зеркалит структуру [StoragePlan]: original + additional.
 */
data class StoragePolicy(
    val originalTemplate: String,
    val additionalOutputs: List<OutputTemplate> = emptyList(),
) {
    init {
        require(originalTemplate.isNotBlank()) { "Original template must not be blank" }
    }
    
    companion object {
        val MUSIC_VIDEO_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
            additionalOutputs = listOf(
                OutputTemplate(
                    pathTemplate = "/media/Music Videos/converted/{artist}/{title}.mp4",
                    format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
                ),
            ),
        )

        val SERIES_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/TV Series/{seriesName}/Season {season}/{episode} - {title}.{ext}",
        )

        // для каналов-серий: сезон = год, файл начинается с даты
        // пример: /Yt Series/Channel 1/2026/2026-01-12 Video Title.mp4
        val YT_SERIES_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Yt Series/{channelName}/{year}/{date} {title}.{ext}"
        )

        val OTHER_DEFAULT = StoragePolicy(
            originalTemplate = "~/Downloads/Media/Videos/{channelName}/{title} [{videoId}].{ext}",
        )

        fun defaultFor(category: Category): StoragePolicy = when (category) {
            Category.MUSIC_VIDEO -> MUSIC_VIDEO_DEFAULT
            Category.SERIES -> SERIES_DEFAULT
            Category.OTHER -> OTHER_DEFAULT
        }
    }
}
```

> **Преимущества**:
> - Формат привязан к шаблону, а не задаётся отдельно в `PostProcessPolicy`
> - Расширяемо: добавление audio/thumbnail — просто ещё один `OutputTemplate` в списке
> - Зеркалит `StoragePlan(original, additional)` — маппинг 1:1
> 
> **Пример**: музыкальное видео с аудио-извлечением:
> ```kotlin
> StoragePolicy(
>     originalTemplate = "/media/Music/original/{artist}/{title} [{videoId}].{ext}",
>     additionalOutputs = listOf(
>         OutputTemplate("/media/Music/video/{artist}/{title}.mp4", OutputFormat.ConvertedVideo(MediaContainer.MP4)),
>         OutputTemplate("/media/Music/audio/{artist}/{title}.m4a", OutputFormat.Audio(AudioFormat.M4A)),
>         OutputTemplate("/media/Music/covers/{artist}/{title}.jpg", OutputFormat.Thumbnail(ImageFormat.JPG)),
>     ),
> )
> ```

### 7.6 PostProcessPolicy

```kotlin
/**
 * Политика пост-обработки.
 * Форматы конвертации задаются в [StoragePolicy] / [OutputTemplate].
 * Здесь — только флаги обработки, применяемые ко всем выходным файлам.
 */
data class PostProcessPolicy(
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
)
```

### 7.7 PathTemplateEngine

```kotlin
class PathTemplateEngine(
    private val baseDirectories: List<FilePath>,
) {
    fun render(template: String, context: TemplateContext): Either<DomainError, FilePath> {
        val rendered = PLACEHOLDER_REGEX.replace(template) { match ->
            val variable = match.groupValues[1]
            context.get(variable)?.sanitizeForPath() ?: ""
        }
        val isWithinBase = baseDirectories.any { base ->
            rendered.startsWith(base.value) && !rendered.contains("..")
        }
        return if (isWithinBase) FilePath(rendered).right()
        else DomainError.PathTraversalAttempt(FilePath(rendered)).left()
    }
    
    private fun String.sanitizeForPath(): String =
        replace(FORBIDDEN_CHARS_REGEX, "_").replace("\\s+".toRegex(), " ").trim().take(MAX_FILENAME_LENGTH)
    
    data class TemplateContext(val values: Map<String, String>) {
        fun get(key: String): String? = values[key]
        
        companion object {
            fun from(metadata: ResolvedMetadata, video: VideoInfo): TemplateContext {
                val date = metadata.releaseDate ?: video.uploadDate
                val map = mutableMapOf(
                    "title" to metadata.title,
                    "date" to (date?.value ?: ""),                         // "2026-01-12"
                    "year" to (date?.year?.toString() ?: ""),              // "2026"
                    "month" to (date?.month?.toString()?.padStart(2, '0') ?: ""),  // "01"
                    "day" to (date?.day?.toString()?.padStart(2, '0') ?: ""),      // "12"
                    "channelName" to video.channelName,
                    "videoId" to video.videoId.value,
                    "uploadDate" to (video.uploadDate?.value ?: ""),
                )
                when (metadata) {
                    is ResolvedMetadata.MusicVideo -> map["artist"] = metadata.artist
                    is ResolvedMetadata.SeriesEpisode -> {
                        map["seriesName"] = metadata.seriesName
                        map["season"] = metadata.season ?: ""
                        map["episode"] = metadata.episode ?: ""
                    }
                    is ResolvedMetadata.Other -> {}
                }
                return TemplateContext(map)
            }
        }
    }
    
    companion object {
        private val PLACEHOLDER_REGEX = "\\{(\\w+)}".toRegex()
        private val FORBIDDEN_CHARS_REGEX = "[/\\\\:*?\"<>|]".toRegex()
        private const val MAX_FILENAME_LENGTH = 180
    }
}
```

### 7.8 VideoDownloader (port)

```kotlin
interface VideoDownloader {
    suspend fun download(
        source: VideoSource,
        outputPath: FilePath,
        policy: DownloadPolicy,
        onProgress: (JobProgress) -> Unit,
    ): Either<DomainError, DownloadResult>
    
    data class DownloadResult(val filePath: FilePath, val container: MediaContainer, val fileSize: Long)
}
```

---

## 8. `job` — Задачи скачивания

Зависимости: `common`, `video`, `metadata`, `storage`

```
domain/job/
├── Job.kt
├── JobStatus.kt
├── JobPhase.kt
├── JobProgress.kt
├── JobError.kt
├── CreateJobUseCase.kt
└── JobRepository.kt           # port
```

### 8.1 JobStatus & JobPhase

```kotlin
enum class JobStatus {
    QUEUED, RUNNING, POST_PROCESSING, DONE, FAILED, CANCELLED;
    fun isTerminal(): Boolean = this in listOf(DONE, FAILED, CANCELLED)
    fun isActive(): Boolean = this in listOf(QUEUED, RUNNING, POST_PROCESSING)
}

enum class JobPhase { DOWNLOAD, MERGE, CONVERT, TAG, MOVE }
```

### 8.2 JobProgress & JobError

```kotlin
data class JobProgress(val phase: JobPhase, val percent: Int, val message: String? = null) {
    init { require(percent in 0..100) { "Percent must be 0-100" } }
}

data class JobError(val code: String, val message: String, val details: String? = null, val retryable: Boolean = false)
```

### 8.3 Job

```kotlin
data class Job(
    val id: JobId,
    val workspaceId: WorkspaceId,
    val status: JobStatus,
    val source: VideoSource,
    val ruleId: RuleId?,
    val category: Category,
    val rawInfo: VideoInfo,
    val metadata: ResolvedMetadata,
    val storagePlan: StoragePlan,
    val progress: JobProgress?,
    val error: JobError?,
    val attempt: Int,
    val createdBy: TelegramUserId,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
) {
    fun isTerminal(): Boolean = status.isTerminal()
    fun isActive(): Boolean = status.isActive()
}
```

### 8.4 CreateJobUseCase

```kotlin
class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val clock: Clock,
) {
    suspend fun execute(request: CreateJobRequest): Either<DomainError, Job> = either {
        ensure(request.category.matches(request.metadata)) {
            DomainError.ValidationError("category", "Category doesn't match metadata type")
        }
        val existing = jobRepository.findByVideoId(request.source.videoId).filter { it.isActive() }
        if (existing.isNotEmpty()) {
            raise(DomainError.JobAlreadyExists(request.source.videoId, existing.first().id))
        }
        val now = clock.now()
        val job = Job(
            id = JobId(Uuid.random()),
            workspaceId = request.workspaceId,
            status = JobStatus.QUEUED,
            source = request.source,
            ruleId = request.ruleId,
            category = request.category,
            rawInfo = request.videoInfo,
            metadata = request.metadata,
            storagePlan = request.storagePlan,
            progress = null, error = null, attempt = 0,
            createdBy = request.createdBy,
            createdAt = now, updatedAt = now,
            startedAt = null, finishedAt = null,
        )
        jobRepository.save(job)
    }
    
    data class CreateJobRequest(
        val workspaceId: WorkspaceId,
        val source: VideoSource,
        val ruleId: RuleId?,
        val category: Category,
        val videoInfo: VideoInfo,
        val metadata: ResolvedMetadata,
        val storagePlan: StoragePlan,
        val createdBy: TelegramUserId,
    )
}
```

### 8.5 JobRepository (port)

```kotlin
interface JobRepository {
    suspend fun findById(id: JobId): Job?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Job>
    suspend fun findByVideoId(videoId: VideoId): List<Job>
    suspend fun findQueued(limit: Int = 10): List<Job>
    suspend fun findByStatus(status: JobStatus, limit: Int = 50, offset: Int = 0): List<Job>
    suspend fun save(job: Job): Job
    suspend fun updateStatus(id: JobId, status: JobStatus): Either<DomainError, Job>
    suspend fun updateError(id: JobId, error: JobError)
}
```

---

## 9. `preview` — Предпросмотр

Зависимости: `common`, `video`, `rule`, `metadata`, `storage`

```
domain/preview/
└── PreviewUseCase.kt
```

### 9.1 PreviewUseCase

`PreviewUseCase` — оркестратор, который связывает все фичи:

```kotlin
class PreviewUseCase(
    private val videoInfoExtractor: VideoInfoExtractor,
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val pathTemplateEngine: PathTemplateEngine,
    private val llmPort: LlmPort?,  // nullable: если LLM не настроен
) {
    suspend fun execute(url: String, workspaceId: WorkspaceId): Either<DomainError, PreviewResult> = either {
        val videoInfo = videoInfoExtractor.extract(url).bind()
        val matchedRule = ruleMatchingService.findMatchingRule(videoInfo, workspaceId)
        
        val (category, metadata, metadataSource) = resolveMetadata(videoInfo, matchedRule).bind()
        
        val storagePolicy = matchedRule?.storagePolicy ?: StoragePolicy.defaultFor(category)
        val context = PathTemplateEngine.TemplateContext.from(metadata, videoInfo)
        val storagePlan = buildStoragePlan(storagePolicy, context).bind()
        
        PreviewResult(
            source = VideoSource(Url(url), videoInfo.videoId, videoInfo.extractor),
            videoInfo = videoInfo,
            matchedRule = matchedRule,
            metadataSource = metadataSource,
            category = category,
            metadata = metadata,
            storagePlan = storagePlan,
            warnings = emptyList(),
        )
    }
    
    /**
     * Три пути определения метаданных:
     * 1. Rule → MetadataResolver (если правило найдено)
     * 2. LLM → LlmPort (если правила нет, но LLM настроен)
     * 3. Fallback → MetadataResolver с MetadataTemplate.Other()
     */
    private suspend fun resolveMetadata(
        video: VideoInfo, rule: Rule?,
    ): Either<DomainError, Triple<Category, ResolvedMetadata, MetadataSource>> = either {
        when {
            rule != null -> {
                val metadata = metadataResolver.resolve(video, rule.metadataTemplate)
                Triple(rule.category, metadata, MetadataSource.RULE)
            }
            llmPort != null -> {
                val suggestion = llmPort.suggestMetadata(video).getOrNull()
                if (suggestion != null) {
                    Triple(suggestion.category, suggestion.metadata, MetadataSource.LLM)
                } else {
                    val metadata = metadataResolver.resolve(video, MetadataTemplate.Other())
                    Triple(Category.OTHER, metadata, MetadataSource.FALLBACK)
                }
            }
            else -> {
                val metadata = metadataResolver.resolve(video, MetadataTemplate.Other())
                Triple(Category.OTHER, metadata, MetadataSource.FALLBACK)
            }
        }
    }
    
    private fun buildStoragePlan(
        policy: StoragePolicy, context: PathTemplateEngine.TemplateContext,
    ): Either<DomainError, StoragePlan> = either {
        val originalContainer = context.get("ext")
            ?.let { MediaContainer.fromExtension(it) }
            ?: MediaContainer.WEBM
        
        val original = OutputTarget(
            path = pathTemplateEngine.render(policy.originalTemplate, context).bind(),
            format = OutputFormat.OriginalVideo(originalContainer),
        )
        
        val additional = policy.additionalOutputs.map { output ->
            OutputTarget(
                path = pathTemplateEngine.render(output.pathTemplate, context).bind(),
                format = output.format,
            )
        }
        
        StoragePlan(original = original, additional = additional)
    }
    
    data class PreviewResult(
        val source: VideoSource,
        val videoInfo: VideoInfo,
        val matchedRule: Rule?,
        val metadataSource: MetadataSource,
        val category: Category,
        val metadata: ResolvedMetadata,
        val storagePlan: StoragePlan,
        val warnings: List<String>,
    )
}
```

---

## 10. Инварианты и валидация

### 9.1 Общие правила

| Поле                            | Правило                                   |
|---------------------------------|-------------------------------------------|
| `title`, `artist`, `seriesName` | Не пустые после trim                      |
| `releaseDate`                   | `LocalDate` (ISO 8601) или null           |
| `tags`                          | Нормализуются: trim, dedupe, remove empty |
| `priority`                      | Int, может быть отрицательным             |
| `percent` (progress)            | 0-100                                     |
| Path templates                  | Минимум `{title}` или `{videoId}`         |

### 9.2 Валидация при создании

Все инварианты проверяются в `init {}` блоках data/value классов.
При нарушении — `IllegalArgumentException`.

### 9.3 Валидация бизнес-правил

Бизнес-валидация (например, "job с таким videoId уже существует") — через `Either<DomainError, T>` в use cases.
