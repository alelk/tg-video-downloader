# Доменная модель

> **Цель документа**: Полное описание доменных сущностей, sealed-иерархий, value objects и инвариантов.

---

## 1. Обзор

Домен — ядро приложения. Он **не зависит** от фреймворков, БД, сериализации.

**Модуль**: `domain` — **Kotlin Multiplatform** (targets: `jvm`, `js`).

Весь код размещается в `commonMain` source set. Platform-specific код не допускается.

```
domain/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── model/          # Сущности и value objects
├── service/        # Доменные сервисы
├── usecase/        # Use cases (application layer)
├── error/          # Доменные ошибки (sealed)
├── policy/         # Политики (download, storage, postprocess)
└── port/           # Интерфейсы репозиториев (для infra)
```

**KMP-замечания**:
- `kotlin.uuid.Uuid` вместо `java.util.UUID`
- `kotlinx.datetime.Instant`, `LocalDate` вместо `java.time.*`
- `value class` поддерживается на JS с Kotlin 2.1+
- `kotlin.text.Regex` — уже KMP-совместим
- `Path` не используется в domain — только `String` для путей

---

## 2. Value Objects

### 2.1 VideoId

```kotlin
@JvmInline
value class VideoId(val value: String) {
    init {
        require(value.isNotBlank()) { "VideoId cannot be blank" }
        require(value.length <= 20) { "VideoId too long" }
    }
}
```

### 2.2 ChannelId

```kotlin
@JvmInline
value class ChannelId(val value: String) {
    init {
        require(value.startsWith("UC") || value.startsWith("@")) { 
            "Invalid ChannelId format" 
        }
    }
}
```

### 2.3 RuleId, JobId

```kotlin
@JvmInline
value class RuleId(val value: Uuid)  // kotlin.uuid.Uuid

@JvmInline
value class JobId(val value: Uuid)
```

### 2.4 TelegramUserId

```kotlin
@JvmInline
value class TelegramUserId(val value: Long) {
    init {
        require(value > 0) { "TelegramUserId must be positive" }
    }
}
```

---

## 3. Enums

### 3.1 Category

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

### 3.2 JobStatus

```kotlin
enum class JobStatus {
    QUEUED,
    RUNNING,
    POST_PROCESSING,
    DONE,
    FAILED,
    CANCELLED;
    
    fun isTerminal(): Boolean = this in listOf(DONE, FAILED, CANCELLED)
    fun isActive(): Boolean = this in listOf(QUEUED, RUNNING, POST_PROCESSING)
}
```

### 3.3 JobPhase

```kotlin
enum class JobPhase {
    DOWNLOAD,
    MERGE,
    CONVERT,
    TAG,
    MOVE;
}
```

### 3.4 OutputKind

```kotlin
enum class OutputKind {
    ORIGINAL,
    CONVERTED,
    AUDIO_ONLY,
    THUMBNAIL;
}
```

---

## 4. Sealed Classes

### 4.1 RuleMatch

**Назначение**: Описание критерия для матчинга правила к видео.

```kotlin
sealed interface RuleMatch {
    
    /** Все условия должны выполниться (AND) */
    data class AllOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AllOf cannot be empty" }
        }
    }
    
    /** Хотя бы одно условие должно выполниться (OR) */
    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch {
        init {
            require(matches.isNotEmpty()) { "AnyOf cannot be empty" }
        }
    }
    
    /** Точное совпадение Channel ID */
    data class ChannelId(val value: String) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelId value cannot be blank" }
        }
    }
    
    /** Точное совпадение имени канала (case-insensitive) */
    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatch {
        init {
            require(value.isNotBlank()) { "ChannelName value cannot be blank" }
        }
    }
    
    /** Regex по заголовку видео */
    data class TitleRegex(val pattern: String) : RuleMatch {
        val regex: Regex by lazy { pattern.toRegex() }
        
        init {
            require(pattern.isNotBlank()) { "TitleRegex pattern cannot be blank" }
            // Валидация regex при создании
            runCatching { pattern.toRegex() }.getOrElse { 
                throw IllegalArgumentException("Invalid regex: $pattern", it) 
            }
        }
    }
    
    /** Regex по URL видео */
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

**Алгоритм матчинга**:

```kotlin
fun RuleMatch.matches(video: VideoInfo): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(video) }
    is RuleMatch.AnyOf -> matches.any { it.matches(video) }
    is RuleMatch.ChannelId -> video.channelId == value
    is RuleMatch.ChannelName -> video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(video.webpageUrl)
}
```

**Специфичность** (для выбора между правилами с равным приоритетом):

```kotlin
fun RuleMatch.specificity(): Int = when (this) {
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.specificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.specificity() } ?: 0
}
```

---

### 4.2 ResolvedMetadata

**Назначение**: Метаданные видео для редактирования пользователем.

```kotlin
sealed interface ResolvedMetadata {
    val title: String
    val year: Int?
    val tags: List<String>
    val comment: String?
    
    /** Музыкальное видео */
    data class MusicVideo(
        val artist: String,
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(artist.isNotBlank()) { "Artist cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
            year?.let { require(it in 1800..2100) { "Year out of range" } }
        }
    }
    
    /** Эпизод сериала / шоу */
    data class SeriesEpisode(
        val seriesName: String,
        val season: String? = null,
        val episode: String? = null,
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(seriesName.isNotBlank()) { "SeriesName cannot be blank" }
            require(title.isNotBlank()) { "Title cannot be blank" }
            year?.let { require(it in 1800..2100) { "Year out of range" } }
        }
    }
    
    /** Прочее */
    data class Other(
        override val title: String,
        override val year: Int? = null,
        override val tags: List<String> = emptyList(),
        override val comment: String? = null,
    ) : ResolvedMetadata {
        init {
            require(title.isNotBlank()) { "Title cannot be blank" }
            year?.let { require(it in 1800..2100) { "Year out of range" } }
        }
    }
}
```

**Связь с Category**:

```kotlin
fun ResolvedMetadata.category(): Category = when (this) {
    is ResolvedMetadata.MusicVideo -> Category.MUSIC_VIDEO
    is ResolvedMetadata.SeriesEpisode -> Category.SERIES
    is ResolvedMetadata.Other -> Category.OTHER
}

fun Category.matches(metadata: ResolvedMetadata): Boolean = 
    metadata.category() == this
```

**Нормализация tags**:

```kotlin
fun List<String>.normalizeTags(): List<String> = 
    this.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
```

---

### 4.3 DomainError

**Назначение**: Все возможные доменные ошибки.

```kotlin
sealed interface DomainError {
    val message: String
    
    // === Validation ===
    
    data class ValidationError(
        val field: String,
        override val message: String,
    ) : DomainError
    
    data class InvalidUrl(
        val url: String,
        override val message: String = "Invalid URL: $url",
    ) : DomainError
    
    // === Not Found ===
    
    data class RuleNotFound(
        val id: RuleId,
        override val message: String = "Rule not found: ${id.value}",
    ) : DomainError
    
    data class JobNotFound(
        val id: JobId,
        override val message: String = "Job not found: ${id.value}",
    ) : DomainError
    
    // === Video ===
    
    data class VideoUnavailable(
        val videoId: String,
        val reason: String,
        override val message: String = "Video unavailable: $videoId - $reason",
    ) : DomainError
    
    data class VideoExtractionFailed(
        val url: String,
        val cause: String,
        override val message: String = "Failed to extract video info: $cause",
    ) : DomainError
    
    // === Job ===
    
    data class JobAlreadyExists(
        val videoId: String,
        val existingJobId: JobId,
        override val message: String = "Job already exists for video $videoId",
    ) : DomainError
    
    data class JobCannotBeCancelled(
        val id: JobId,
        val currentStatus: JobStatus,
        override val message: String = "Cannot cancel job in status $currentStatus",
    ) : DomainError
    
    data class DownloadFailed(
        val jobId: JobId,
        val cause: String,
        override val message: String = "Download failed: $cause",
    ) : DomainError
    
    data class PostProcessingFailed(
        val jobId: JobId,
        val phase: String,
        val cause: String,
        override val message: String = "Post-processing failed at $phase: $cause",
    ) : DomainError
    
    // === Storage ===
    
    data class PathTraversalAttempt(
        val path: String,
        override val message: String = "Path traversal attempt: $path",
    ) : DomainError
    
    data class StorageFailed(
        val path: String,
        val cause: String,
        override val message: String = "Storage failed for $path: $cause",
    ) : DomainError
    
    // === Auth ===
    
    data class Unauthorized(
        override val message: String = "Unauthorized",
    ) : DomainError
    
    data class Forbidden(
        val userId: TelegramUserId,
        override val message: String = "User ${userId.value} not allowed",
    ) : DomainError
    
    // === LLM ===
    
    data class LlmError(
        val provider: String,
        val message: String,
        val statusCode: Int? = null,
    ) : DomainError
}
```

---

## 5. Data Classes

### 5.1 VideoSource

```kotlin
data class VideoSource(
    val url: String,
    val videoId: VideoId,
    val extractor: String = "youtube",
) {
    init {
        require(url.isNotBlank()) { "URL cannot be blank" }
    }
}
```

### 5.2 VideoInfo

```kotlin
data class VideoInfo(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val uploadDate: LocalDate?,
    val durationSeconds: Int,
    val webpageUrl: String,
    val thumbnails: List<Thumbnail> = emptyList(),
    val description: String? = null,
    val viewCount: Long? = null,
) {
    data class Thumbnail(
        val url: String,
        val width: Int?,
        val height: Int?,
    )
}
```

### 5.3 Rule

```kotlin
data class Rule(
    val id: RuleId,
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

### 5.4 Job

```kotlin
data class Job(
    val id: JobId,
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

### 5.5 JobProgress

```kotlin
data class JobProgress(
    val phase: JobPhase,
    val percent: Int,
    val message: String? = null,
) {
    init {
        require(percent in 0..100) { "Percent must be 0-100" }
    }
}
```

### 5.6 JobError

```kotlin
data class JobError(
    val code: String,
    val message: String,
    val details: String? = null,
    val retryable: Boolean = false,
)
```

### 5.7 StoragePlan

```kotlin
data class StoragePlan(
    val original: OutputTarget?,
    val converted: OutputTarget?,
    val additional: List<OutputTarget> = emptyList(),
) {
    fun allTargets(): List<OutputTarget> = 
        listOfNotNull(original, converted) + additional
}

data class OutputTarget(
    val path: String,
    val container: String,
    val kind: OutputKind,
)
```

---

## 6. Policies

### 6.1 MetadataTemplate

**Назначение**: Подсказки для автоматического распознавания метаданных.

```kotlin
data class MetadataTemplate(
    val artistPattern: String? = null,      // regex с группами
    val titlePattern: String? = null,
    val seriesNameOverride: String? = null,
    val defaultTags: List<String> = emptyList(),
)
```

### 6.2 DownloadPolicy

```kotlin
data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    val preferredFormat: String? = null,  // "mp4", "webm"
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
) {
    enum class VideoQuality {
        BEST,
        HD_1080,
        HD_720,
        SD_480,
    }
}
```

### 6.3 StoragePolicy

```kotlin
data class StoragePolicy(
    val originalTemplate: String?,
    val convertedTemplate: String?,
    val audioOnlyTemplate: String? = null,
) {
    init {
        require(originalTemplate != null || convertedTemplate != null) {
            "At least one template must be specified"
        }
    }
    
    companion object {
        val MUSIC_VIDEO_DEFAULT = StoragePolicy(
            originalTemplate = "/media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
            convertedTemplate = "/media/Music Videos/{artist}/{title}.mp4",
        )
        
        val SERIES_DEFAULT = StoragePolicy(
            originalTemplate = null,
            convertedTemplate = "/media/TV/{seriesName}/Season {season}/{episode} - {title}.mp4",
        )
        
        val OTHER_DEFAULT = StoragePolicy(
            originalTemplate = "/media/Videos/{channelName}/{title} [{videoId}].{ext}",
            convertedTemplate = null,
        )
        
        fun defaultFor(category: Category): StoragePolicy = when (category) {
            Category.MUSIC_VIDEO -> MUSIC_VIDEO_DEFAULT
            Category.SERIES -> SERIES_DEFAULT
            Category.OTHER -> OTHER_DEFAULT
        }
    }
}
```

### 6.4 PostProcessPolicy

```kotlin
data class PostProcessPolicy(
    val convertToMp4: Boolean = true,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val normalizeAudio: Boolean = false,
    val extractAudio: Boolean = false,
    val audioFormat: String = "m4a",
)
```

---

## 7. Ports (Repository Interfaces)

```kotlin
interface RuleRepository {
    suspend fun findById(id: RuleId): Rule?
    suspend fun findAll(enabled: Boolean? = null): List<Rule>
    suspend fun save(rule: Rule): Rule
    suspend fun delete(id: RuleId)
}

interface JobRepository {
    suspend fun findById(id: JobId): Job?
    suspend fun findByVideoId(videoId: VideoId): List<Job>
    suspend fun findQueued(limit: Int = 10): List<Job>
    suspend fun findByStatus(status: JobStatus, limit: Int = 50, offset: Int = 0): List<Job>
    suspend fun save(job: Job): Job
    suspend fun updateStatus(id: JobId, status: JobStatus, progress: JobProgress? = null)
    suspend fun updateError(id: JobId, error: JobError)
}

interface VideoInfoExtractor {
    suspend fun extract(url: String): Either<DomainError, VideoInfo>
}

interface VideoDownloader {
    suspend fun download(
        source: VideoSource,
        outputPath: String,
        policy: DownloadPolicy,
        onProgress: (JobProgress) -> Unit,
    ): Either<DomainError, DownloadResult>
    
    data class DownloadResult(
        val filePath: String,
        val format: String,
        val fileSize: Long,
    )
}
```

---

## 8. Доменные сервисы

### 8.1 RuleMatchingService

```kotlin
class RuleMatchingService {
    
    fun findMatchingRule(video: VideoInfo, rules: List<Rule>): Rule? {
        return rules
            .filter { it.enabled }
            .filter { it.match.matches(video) }
            .maxWithOrNull(compareBy(
                { it.priority },
                { it.match.specificity() },
                { -it.createdAt.epochSecond }  // старые первее
            ))
    }
}
```

### 8.2 MetadataResolver

```kotlin
class MetadataResolver {
    
    fun resolve(
        video: VideoInfo,
        category: Category,
        template: MetadataTemplate?,
    ): ResolvedMetadata {
        return when (category) {
            Category.MUSIC_VIDEO -> resolveMusicVideo(video, template)
            Category.SERIES -> resolveSeriesEpisode(video, template)
            Category.OTHER -> resolveOther(video, template)
        }
    }
    
    private fun resolveMusicVideo(video: VideoInfo, template: MetadataTemplate?): ResolvedMetadata.MusicVideo {
        val (artist, title) = parseArtistTitle(video.title, template?.artistPattern)
        return ResolvedMetadata.MusicVideo(
            artist = artist,
            title = title,
            year = video.uploadDate?.year,
            tags = template?.defaultTags.orEmpty(),
        )
    }
    
    private fun parseArtistTitle(title: String, pattern: String?): Pair<String, String> {
        // Попытка распарсить "Artist - Title" или использовать pattern
        val separators = listOf(" - ", " – ", " — ", ": ")
        for (sep in separators) {
            if (sep in title) {
                val parts = title.split(sep, limit = 2)
                return parts[0].trim() to parts[1].trim()
            }
        }
        return "Unknown Artist" to title
    }
    
    // ... остальные методы
}
```

### 8.3 PathTemplateEngine

```kotlin
class PathTemplateEngine(
    private val baseDirectories: List<String>,
) {
    
    fun render(template: String, context: TemplateContext): Either<DomainError, String> {
        val rendered = PLACEHOLDER_REGEX.replace(template) { match ->
            val variable = match.groupValues[1]
            context.get(variable)?.sanitizeForPath() ?: ""
        }
        
        // Проверка path traversal (нет ".." в результирующем пути)
        val isWithinBase = baseDirectories.any { base ->
            rendered.startsWith(base) && !rendered.contains("..")
        }
        
        return if (isWithinBase) {
            rendered.right()
        } else {
            DomainError.PathTraversalAttempt(rendered).left()
        }
    }
    
    private fun String.sanitizeForPath(): String =
        this.replace(FORBIDDEN_CHARS_REGEX, "_")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(MAX_FILENAME_LENGTH)
    
    data class TemplateContext(
        val values: Map<String, String>,
    ) {
        fun get(key: String): String? = values[key]
        
        companion object {
            fun from(metadata: ResolvedMetadata, video: VideoInfo): TemplateContext {
                val map = mutableMapOf<String, String>()
                map["title"] = metadata.title
                map["year"] = metadata.year?.toString() ?: ""
                map["channelName"] = video.channelName
                map["videoId"] = video.videoId
                map["uploadDate"] = video.uploadDate?.toString() ?: ""
                
                when (metadata) {
                    is ResolvedMetadata.MusicVideo -> {
                        map["artist"] = metadata.artist
                    }
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

---

## 9. Use Cases

### 9.1 PreviewUseCase

```kotlin
class PreviewUseCase(
    private val videoInfoExtractor: VideoInfoExtractor,
    private val ruleRepository: RuleRepository,
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val pathTemplateEngine: PathTemplateEngine,
) {
    
    suspend fun execute(url: String): Either<DomainError, PreviewResult> = either {
        val videoInfo = videoInfoExtractor.extract(url).bind()
        val rules = ruleRepository.findAll(enabled = true)
        val matchedRule = ruleMatchingService.findMatchingRule(videoInfo, rules)
        
        val category = matchedRule?.category ?: Category.OTHER
        val template = matchedRule?.metadataTemplate
        val storagePolicy = matchedRule?.storagePolicy ?: StoragePolicy.OTHER_DEFAULT
        
        val metadata = metadataResolver.resolve(videoInfo, category, template)
        val context = PathTemplateEngine.TemplateContext.from(metadata, videoInfo)
        
        val storagePlan = buildStoragePlan(storagePolicy, context).bind()
        
        PreviewResult(
            source = VideoSource(url, VideoId(videoInfo.videoId)),
            videoInfo = videoInfo,
            matchedRule = matchedRule,
            metadataSource = metadataSource,
            category = category,
            metadata = metadata,
            storagePlan = storagePlan,
            warnings = emptyList(),
        )
    }
    
    private fun buildStoragePlan(
        policy: StoragePolicy, 
        context: PathTemplateEngine.TemplateContext,
    ): Either<DomainError, StoragePlan> = either {
        StoragePlan(
            original = policy.originalTemplate?.let { template ->
                OutputTarget(
                    path = pathTemplateEngine.render(template, context).bind(),
                    container = "mp4",
                    kind = OutputKind.ORIGINAL,
                )
            },
            converted = policy.convertedTemplate?.let { template ->
                OutputTarget(
                    path = pathTemplateEngine.render(template, context).bind(),
                    container = "mp4",
                    kind = OutputKind.CONVERTED,
                )
            },
        )
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

### 9.2 CreateJobUseCase

```kotlin
class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val clock: Clock,
) {
    
    suspend fun execute(request: CreateJobRequest): Either<DomainError, Job> = either {
        // Проверка совпадения category и metadata.type
        ensure(request.category.matches(request.metadata)) {
            DomainError.ValidationError("category", "Category doesn't match metadata type")
        }
        
        // Проверка на дубликат
        val existing = jobRepository.findByVideoId(request.source.videoId)
            .filter { it.isActive() }
        
        if (existing.isNotEmpty()) {
            raise(DomainError.JobAlreadyExists(
                request.source.videoId.value, 
                existing.first().id
            ))
        }
        
        val now = clock.instant()
        val job = Job(
            id = JobId(Uuid.random()),
            status = JobStatus.QUEUED,
            source = request.source,
            ruleId = request.ruleId,
            category = request.category,
            rawInfo = request.videoInfo,
            metadata = request.metadata,
            storagePlan = request.storagePlan,
            progress = null,
            error = null,
            attempt = 0,
            createdBy = request.createdBy,
            createdAt = now,
            updatedAt = now,
            startedAt = null,
            finishedAt = null,
        )
        
        jobRepository.save(job)
    }
    
    data class CreateJobRequest(
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

---

## 10. Инварианты и валидация

### 10.1 Общие правила

| Поле                            | Правило                                   |
|---------------------------------|-------------------------------------------|
| `title`, `artist`, `seriesName` | Не пустые после trim                      |
| `year`                          | 1800-2100 или null                        |
| `tags`                          | Нормализуются: trim, dedupe, remove empty |
| `priority`                      | Int, может быть отрицательным             |
| `percent` (progress)            | 0-100                                     |
| Path templates                  | Минимум `{title}` или `{videoId}`         |

### 10.2 Валидация при создании

Все инварианты проверяются в `init {}` блоках data/value классов.
При нарушении — `IllegalArgumentException`.

### 10.3 Валидация бизнес-правил

Бизнес-валидация (например, "job с таким videoId уже существует") — через `Either<DomainError, T>` в use cases.

