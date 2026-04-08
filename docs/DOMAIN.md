# Domain Model

> **Purpose**: Full description of domain entities, sealed hierarchies, value objects and invariants.

---

## 1. Overview

The domain is the core of the application. It has **no dependencies** on frameworks, databases, or serialization.

**Module**: `domain` — **Kotlin Multiplatform** (targets: `jvm`, `js`).

All code resides in the `commonMain` source set. Platform-specific code is not allowed.

### Structure: Package-by-Feature

The domain is organized **by feature**, not by technical layers. Each package is autonomous,
containing its own models, services, and ports.
This structure:
- Increases cohesion (related classes are co-located)
- Simplifies navigation (everything about rules is in `rule/`)
- Each package can potentially be extracted into a separate module
- No circular dependencies between packages

```
domain/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── common/             # Shared types: Category, DomainError, Tag, value objects (WorkspaceId, JobId, etc.)
├── workspace/          # Workspace, WorkspaceMember, WorkspaceRole, WorkspaceRepository port
├── channel/            # Channel (channel directory), ChannelRepository port
├── video/              # VideoSource, VideoInfo, VideoInfoExtractor port
├── rule/               # Rule, RuleMatch, MatchResult, RuleMatchingService, RuleRepository port
├── metadata/           # ResolvedMetadata, MetadataResolver, MetadataTemplate, MetadataTemplateMerger, LlmPort
├── storage/            # StoragePlan, OutputRule, OutputFormat, PathTemplateEngine
├── job/                # Job, JobStatus, CreateJobUseCase, JobRepository port
└── preview/            # PreviewUseCase (orchestrates video + rule + channel + metadata + storage)
```

### Package Dependency Graph

```
                    preview
                   ╱   │   ╲
                  ╱    │    ╲
              rule  metadata  storage
              │  ╲     │      ╱
              │   ╲    │     ╱
              │    video
              │      │
              channel │
                ╲    │
                 common
                     │
              workspace ──▶ common

              job ──▶ video, storage, common

              rule ──▶ preview (for UserOverrides in MatchContext)
              rule ──▶ channel (for Channel in MatchContext)
```

> Arrows = depends on. No cycles. Each package can be extracted into a Gradle module if needed.

**KMP notes**:
- `kotlin.uuid.Uuid` instead of `java.util.UUID`
- `kotlin.time.Instant` for timestamps (in stdlib since Kotlin 2.1.20+)
- `kotlin.time.Duration` for durations (in stdlib)
- `LocalDate`, `Url`, `FilePath` — custom value classes (Kotlin stdlib has no KMP-compatible equivalents)
- `value class` is supported on JS since Kotlin 2.1+

---

## 2. `common` — Shared Types

### 2.1 Value Objects

```kotlin
/**
 * Video identifier on the source platform.
 * Examples: "dQw4w9WgXcQ" (YouTube), "12345678" (RuTube), "-12345_67890" (VK).
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
value class ChannelDirectoryEntryId(val value: Uuid)

/**
 * Tag for grouping channels in the directory.
 * Lowercase alphanumeric with hyphens. Examples: "music-video", "lofi", "tech-review".
 */
@JvmInline
value class Tag(val value: String) {
    init {
        require(value.isNotBlank()) { "Tag cannot be blank" }
        require(value.length <= 50) { "Tag too long (max 50)" }
        require(value.matches(TAG_REGEX)) { "Tag must be lowercase alphanumeric with hyphens: $value" }
    }
    companion object {
        private val TAG_REGEX = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    }
}

/**
 * Human-readable unique workspace identifier.
 * Used in URL paths and application configuration.
 * Requirements: lowercase letters, digits, hyphens; 3–50 characters; must not start or end with a hyphen.
 * Examples: "personal", "my-team", "project-alpha-2"
 */
@JvmInline
value class WorkspaceSlug(val value: String) {
    init {
        require(value.matches(Regex("^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$"))) {
            "WorkspaceSlug must be 3–50 chars, lowercase letters/digits, hyphens not at start/end"
        }
    }
}

@JvmInline
value class TelegramUserId(val value: Long) {
    init {
        require(value > 0) { "TelegramUserId must be positive" }
    }
}

/**
 * yt-dlp extractor identifier (source platform name).
 * Examples: "youtube", "rutube", "vk", "generic".
 * Determined automatically by yt-dlp during metadata extraction.
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
 * Video URL. Basic validation without external libraries.
 * Kotlin stdlib has no KMP-compatible URL type.
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
 * Date in ISO 8601 format (YYYY-MM-DD).
 * Kotlin stdlib has no KMP-compatible LocalDate type.
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
 * File or directory path.
 * Kotlin stdlib has no KMP-compatible Path type.
 * Used in domain instead of java.nio.file.Path.
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
    data class ChannelNotFound(val id: ChannelDirectoryEntryId, override val message: String = "Channel not found: ${id.value}") : DomainError
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
    data class WorkspaceNotFoundBySlug(val slug: WorkspaceSlug, override val message: String = "Workspace not found: ${slug.value}") : DomainError
    data class WorkspaceSlugConflict(val slug: WorkspaceSlug, override val message: String = "Workspace with slug '${slug.value}' already exists") : DomainError
    data class WorkspaceAccessDenied(val workspaceId: WorkspaceId, val userId: TelegramUserId, override val message: String = "User ${userId.value} is not a member of workspace ${workspaceId.value}") : DomainError

    // === LLM ===
    data class LlmError(val provider: String, override val message: String, val statusCode: Int? = null) : DomainError
}
```

> `DomainError` lives in `common/` because it is used across all packages.
> `JobStatus` is imported from `job/` for `JobCannotBeCancelled`
> — this is the only back-reference, acceptable because it is a sealed subclass, not a business dependency.

---

## 3. `workspace` — Workspace

Dependencies: `common`

```
domain/workspace/
├── Workspace.kt
├── WorkspaceMember.kt
├── WorkspaceRole.kt
└── WorkspaceRepository.kt
```

A workspace is a group of users sharing common resources. All domain entities (Rule, Job) are scoped to a workspace.

### 3.1 Workspace

```kotlin
data class Workspace(
    val id: WorkspaceId,      // UUID — internal technical key
    val slug: WorkspaceSlug,  // "my-team" — human-readable, unique, used in URLs
    val name: String,
    val createdAt: Instant,
)
```

> `slug` — unique string identifier for the workspace. Used in URLs (`/api/v1/workspaces/{slug}/...`) and application configuration. Set when creating the workspace; must be unique in the system.

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
    /** Can manage members (add/remove) */
    OWNER,
    /** Full access to all workspace resources */
    MEMBER,
}
```

> Both roles have equal access to resources. OWNER can additionally manage workspace membership.

### 3.4 WorkspaceRepository (port)

```kotlin
interface WorkspaceRepository {
    suspend fun findById(id: WorkspaceId): Workspace?
    suspend fun findBySlug(slug: WorkspaceSlug): Workspace?
    suspend fun findByUser(userId: TelegramUserId): List<WorkspaceMember>
    suspend fun findMembers(workspaceId: WorkspaceId): List<WorkspaceMember>
    suspend fun isMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
    suspend fun save(workspace: Workspace): Either<DomainError, Workspace>
    suspend fun addMember(member: WorkspaceMember): Either<DomainError, WorkspaceMember>
    suspend fun removeMember(workspaceId: WorkspaceId, userId: TelegramUserId): Boolean
}
```

See also: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 4. `video` — Video

Dependencies: `common`

```
domain/video/
├── VideoSource.kt
├── VideoInfo.kt
├── VideoInfoExtractor.kt    # port
└── VideoInfoCache.kt        # port
```

### 4.1 VideoSource

```kotlin
data class VideoSource(
    val url: Url,
    val videoId: VideoId,
    val extractor: Extractor,
)
```

> `extractor` is determined automatically by yt-dlp. Supports [1000+ sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

### 4.2 VideoInfo

```kotlin
data class VideoInfo(
    val videoId: VideoId,
    val extractor: Extractor,
    val title: String,
    val channelId: ChannelId,
    val channelName: String,
    val uploadDate: LocalDate?,
    val duration: Duration,          // kotlin.time.Duration (KMP, in stdlib)
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

### 4.4 VideoInfoCache (port)

```kotlin
interface VideoInfoCache {
    suspend fun get(url: String): VideoInfo?
    suspend fun put(url: String, videoInfo: VideoInfo)
}
```

> VideoInfo cache in PostgreSQL. Calling yt-dlp is an expensive operation (3–10 sec).
> During interactive preview the user may change overrides multiple times for the same URL.
> The cache ensures yt-dlp is called only once.
> Implementation: `VideoInfoCacheImpl` in `server:infra/db/repository/`.

---

## 4a. `channel` — Channel Directory

Dependencies: `common`

```
domain/channel/
├── Channel.kt
└── ChannelRepository.kt       # port
```

The channel directory allows registering channels with tags and metadata overrides.
Used for tag-based matching in rules (`RuleMatch.HasTag`) and for per-channel metadata overrides.

See also: [ADR/008-channel-directory.md](./ADR/008-channel-directory.md)

### 4a.1 Channel

```kotlin
data class Channel(
    val id: ChannelDirectoryEntryId,
    val workspaceId: WorkspaceId,
    val channelId: ChannelId,          // Platform channel ID
    val extractor: Extractor,          // Platform (youtube, rutube, ...)
    val name: String,                  // Human-readable name
    val tags: Set<Tag>,                // Tags for grouping
    val metadataOverrides: MetadataTemplate? = null,  // Per-channel metadata overrides
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Channel name cannot be blank" }
    }
}
```

> Unique key on the platform: `channelId + extractor` (+ workspace).
> `metadataOverrides` are merged on top of `Rule.metadataTemplate` when a rule matches.

### 4a.2 ChannelRepository (port)

```kotlin
interface ChannelRepository {
    suspend fun findById(id: ChannelDirectoryEntryId): Channel?
    suspend fun findByWorkspace(workspaceId: WorkspaceId): List<Channel>
    suspend fun findByChannelId(workspaceId: WorkspaceId, channelId: ChannelId, extractor: Extractor): Channel?
    suspend fun findByTag(workspaceId: WorkspaceId, tag: Tag): List<Channel>
    suspend fun findByTags(workspaceId: WorkspaceId, tags: Set<Tag>, matchAll: Boolean = false): List<Channel>
    suspend fun save(channel: Channel): Either<DomainError, Channel>
    suspend fun delete(id: ChannelDirectoryEntryId): Boolean
    suspend fun findAllTags(workspaceId: WorkspaceId): Set<Tag>
}
```

---

## 5. `rule` — Rules

Dependencies: `common`, `video`, `preview`, `channel`

```
domain/rule/
├── Rule.kt
├── RuleMatch.kt               # sealed interface
├── MatchContext.kt
├── MatchResult.kt             # result of rule matching (rule + channel)
├── matches.kt                 # extension fun RuleMatch.matches(ctx)
├── matchSpecificity.kt        # extension fun RuleMatch.matchSpecificity()
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
    
    /** Matches on the category from user overrides. If overrides == null — does not match. */
    data class CategoryEquals(val category: Category) : RuleMatch
    
    /**
     * Matches if the video's channel is registered in the directory and has the given tag.
     * Requires channel != null in MatchContext.
     */
    data class HasTag(val tag: Tag) : RuleMatch
}
```

**MatchContext and matching**:

```kotlin
data class MatchContext(
    val video: VideoInfo,
    val overrides: UserOverrides? = null,
    val channel: Channel? = null,        // from the channel directory (loaded once)
)

fun RuleMatch.matches(ctx: MatchContext): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(ctx) }
    is RuleMatch.AnyOf -> matches.any { it.matches(ctx) }
    is RuleMatch.ChannelId -> ctx.video.channelId.value == value
    is RuleMatch.ChannelName -> ctx.video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(ctx.video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(ctx.video.webpageUrl.value)
    is RuleMatch.CategoryEquals -> ctx.overrides != null && ctx.overrides.category == category
    is RuleMatch.HasTag -> ctx.channel != null && tag in ctx.channel.tags
}

fun RuleMatch.matchSpecificity(): Int = when (this) {
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.HasTag -> 70
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.CategoryEquals -> 20
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.matchSpecificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.matchSpecificity() } ?: 0
}
```

> `CategoryEquals` matches **only** when overrides != null and the category matches.
> `HasTag` matches when the video's channel is found in the directory and has the required tag.

### 5.1a MatchResult

```kotlin
/**
 * Matching result — matched rule + optionally the channel from the directory.
 * The channel is needed to apply channel-level metadata overrides.
 */
data class MatchResult(
    val rule: Rule,
    val channel: Channel?,
)
```

### 5.2 Rule

```kotlin
data class Rule(
    val id: RuleId,
    val name: String,
    val workspaceId: WorkspaceId,
    val match: RuleMatch,
    val metadataTemplate: MetadataTemplate,
    val downloadPolicy: DownloadPolicy = DownloadPolicy(),
    val outputs: List<OutputRule>,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Rule name cannot be blank" }
        require(outputs.isNotEmpty()) { "Rule must have at least one output" }
    }
}
```

> `Rule` does not contain `category` — the category is derived from `metadataTemplate.category` (sealed).
> `Rule` references `MetadataTemplate` (from `metadata/`),
> `OutputRule` (from `storage/`) and `DownloadPolicy` (from `storage/`).
> Each `OutputRule` is a self-contained unit: path + format + quality + post-processing.
> First output = original file, the rest = conversions/copies.

### 5.3 RuleMatchingService

```kotlin
class RuleMatchingService(
    private val ruleRepository: RuleRepository,
    private val channelRepository: ChannelRepository,
) {
    suspend fun findMatchingRule(
        video: VideoInfo,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): MatchResult? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        val channel = channelRepository.findByChannelId(workspaceId, video.channelId, video.extractor)
        val ctx = MatchContext(video, overrides, channel)
        val rule = rules
            .filter { it.match.matches(ctx) }
            .maxByOrNull { it.priority * 1000 + it.match.matchSpecificity() }
            ?: return null
        return MatchResult(rule, channel)
    }
}
```

> Channel is loaded once per matching request (not on every `matches()` call).
> Returns `MatchResult?` instead of `Rule?` to propagate the channel through the metadata pipeline.

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

## 6. `metadata` — Metadata

Dependencies: `common`, `video`

```
domain/metadata/
├── ResolvedMetadata.kt        # sealed interface
├── MetadataSource.kt          # enum
├── MetadataTemplate.kt
├── MetadataTemplateMerger.kt  # mergeTemplates(base, overlay)
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
    
    /** Release year (from releaseDate). Convenient for path templates: {year} */
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
 * Template for determining video metadata.
 *
 * Sealed by category — each subtype contains only relevant fields.
 * Mirrors the structure of [ResolvedMetadata]: MusicVideo → MusicVideo, etc.
 *
 * **Override fields** — hard-set the value (take priority over extraction).
 * **Pattern fields** — regex for extracting from the video's title/description.
 *
 * Priority: override > pattern > fallback (parse by separators).
 */
sealed interface MetadataTemplate {
    val titleOverride: String?
    val titlePattern: String?
    val defaultTags: List<String>
    
    data class MusicVideo(
        val artistOverride: String? = null,       // e.g. "Casting Crowns"
        val artistPattern: String? = null,        // regex with a group, e.g. "^(.+?)\\s*[-–—]"
        override val titleOverride: String? = null,
        override val titlePattern: String? = null,
        override val defaultTags: List<String> = emptyList(),
    ) : MetadataTemplate
    
    data class SeriesEpisode(
        val seriesNameOverride: String? = null,   // e.g. "Tech News Weekly"
        val seasonPattern: String? = null,        // regex to extract the season
        val episodePattern: String? = null,       // regex to extract the episode
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

> **Example**: channel "Casting Crowns" — rule with `category = MUSIC_VIDEO`,
> `metadataTemplate = MetadataTemplate.MusicVideo(artistOverride = "Casting Crowns")`.
> Video "Who Am I (Official Music Video)" → `artist = "Casting Crowns"`, `title = "Who Am I (Official Music Video)"`.
> It is impossible to accidentally set `artistOverride` for `SERIES` — the compiler prevents it.

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
    
    /** Extract the first capturing group from a regex pattern */
    private fun extractByPattern(input: String, pattern: String): String? =
        runCatching { pattern.toRegex().find(input)?.groupValues?.getOrNull(1)?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    
    /** Fallback: parse "Artist - Title" by common separators */
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

### 6.5 MetadataTemplateMerger

```kotlin
/**
 * Merges two MetadataTemplates.
 * Fields from overlay take priority over base.
 * If types differ — overlay wins completely.
 */
fun mergeTemplates(base: MetadataTemplate, overlay: MetadataTemplate?): MetadataTemplate
```

Used for three-level metadata merge:
1. `Rule.metadataTemplate` (base)
2. `Channel.metadataOverrides` (per-channel, on top of rule)
3. `UserOverrides` (manual user edits — highest priority)

### 6.6 LlmPort (port) & LlmSuggestion

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

## 7. `storage` — Storage and Post-processing

Dependencies: `common`, `video`, `metadata`

```
domain/storage/
├── MediaContainer.kt
├── AudioFormat.kt
├── ImageFormat.kt
├── OutputFormat.kt            # sealed interface
├── OutputRule.kt              # per-output path + format + quality + post-processing
├── VideoEncodeSettings.kt     # codec, CRF, preset, HW acceleration for conversion
├── OutputTarget.kt
├── StoragePlan.kt
├── DownloadPolicy.kt
├── PathTemplateEngine.kt
└── VideoDownloader.kt         # port
```

### 7.1 MediaContainer, AudioFormat, ImageFormat

```kotlin
/** Video/media container. Supported by yt-dlp + ffmpeg. */
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

/** Audio format for extracting the audio track. */
enum class AudioFormat(val extension: String) {
    M4A("m4a"),
    MP3("mp3"),
    OPUS("opus"),
    FLAC("flac"),
    WAV("wav");
}

/** Image format (for covers/thumbnails). */
enum class ImageFormat(val extension: String) {
    JPG("jpg"),
    PNG("png"),
    WEBP("webp");
}
```

### 7.2 OutputFormat (sealed)

```kotlin
/**
 * Output file format. Sealed — encodes both the type (video/audio/image) and the specific format.
 *
 * Serialized as the string "kind/extension":
 *   - "original/webm", "original/mkv"
 *   - "video/mp4", "video/mkv"
 *   - "audio/m4a", "audio/mp3", "audio/flac"
 *   - "image/jpg", "image/png", "image/webp"
 *
 * The string representation is used in the API (JSON), DB (TEXT), and configuration (YAML).
 */
sealed interface OutputFormat {
    val extension: String
    
    /** Original video (as downloaded by yt-dlp). */
    data class OriginalVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }
    
    /** Converted video (after ffmpeg processing). */
    data class ConvertedVideo(val container: MediaContainer) : OutputFormat {
        override val extension: String get() = container.extension
    }
    
    /** Extracted audio track. */
    data class Audio(val format: AudioFormat) : OutputFormat {
        override val extension: String get() = format.extension
    }
    
    /** Cover art / thumbnail. */
    data class Thumbnail(val format: ImageFormat = ImageFormat.JPG) : OutputFormat {
        override val extension: String get() = format.extension
    }
    
    /** Serialize to the string "kind/extension". */
    val serialized: String get() = when (this) {
        is OriginalVideo -> "original/${container.extension}"
        is ConvertedVideo -> "video/${container.extension}"
        is Audio -> "audio/${format.extension}"
        is Thumbnail -> "image/${format.extension}"
    }
    
    companion object {
        /**
         * Deserialize from the string "kind/extension".
         * @throws IllegalArgumentException on invalid format.
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
/**
 * A concrete output file with a resolved path and post-processing flags.
 * Created from [OutputRule] via [PathTemplateEngine.buildStoragePlan].
 */
data class OutputTarget(
    val path: FilePath,
    val format: OutputFormat,
    val maxQuality: DownloadPolicy.VideoQuality? = null,
    val encodeSettings: VideoEncodeSettings? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)

/**
 * File storage plan.
 *
 * [original] — source video (as downloaded by yt-dlp). Always one.
 * [additional] — derived outputs: converted video, audio track, cover art, etc.
 * Each [OutputTarget] contains a resolved path, format, and individual post-processing settings.
 *
 * Example for MUSIC_VIDEO:
 *   original  = .../original/Artist/Title.webm       (OriginalVideo)
 *   additional = [
 *     .../converted/Artist/Title.mp4                  (ConvertedVideo, embedMetadata=true, embedThumbnail=true)
 *     .../audio/Artist/Title.m4a                      (Audio, embedMetadata=true)
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
/**
 * Download policy. Controls yt-dlp parameters.
 * Determines maximum quality, preferred container, and subtitles.
 */
data class DownloadPolicy(
    val maxQuality: VideoQuality = VideoQuality.BEST,
    val preferredContainer: MediaContainer? = null,
    val downloadSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
) {
    enum class VideoQuality { BEST, HD_1080, HD_720, SD_480 }
}
```

### 7.5 VideoEncodeSettings

Video encoding settings. Applied for `OutputFormat.ConvertedVideo` **only when**
the source resolution exceeds `maxQuality`. If the source already fits within the limit —
a remux (`-c copy`) is performed without re-encoding.

```kotlin
data class VideoEncodeSettings(
    val codec: VideoCodec = VideoCodec.H264,
    val hwAccel: HwAccel? = null,         // null = software encoding (libx264, etc.)
    val preset: EncodePreset = EncodePreset.MEDIUM,
    val crf: Int = 23,                    // 0 = lossless, 51 = worst; YouTube-like ≈ 23
    val audioBitrate: String = "192k",
    val audioCodec: String? = null,       // null = auto by container
) {
    enum class VideoCodec { H264, H265, VP9, AV1 }

    enum class HwAccel {
        VIDEOTOOLBOX,  // macOS (Apple Silicon / Intel)
        NVENC,         // NVIDIA
        QSV,           // Intel Quick Sync
        VAAPI,         // Linux VA-API
        AMF,           // AMD (Windows)
    }

    enum class EncodePreset {
        ULTRAFAST, SUPERFAST, VERYFAST, FASTER, FAST,
        MEDIUM, SLOW, SLOWER, VERYSLOW
    }

    companion object {
        /** YouTube-like: H264, CRF 23, medium preset, 128k audio */
        val YOUTUBE_LIKE = VideoEncodeSettings(crf = 23, audioBitrate = "128k")
        /** High quality: H264, CRF 18, slow preset, 192k audio */
        val HIGH_QUALITY = VideoEncodeSettings(preset = EncodePreset.SLOW, crf = 18)
    }
}
```

> **Encoding decision logic** (in `FfmpegRunner`):
>
> 1. `ffprobe` determines the source video height
> 2. If `sourceHeight ≤ maxHeight` → **remux** (`-c:v copy -c:a copy`), settings are ignored
> 3. If `sourceHeight > maxHeight` → **re-encode** with `VideoEncodeSettings`
>    - Scaling: `scale=-2:min(maxHeight,ih)`
>    - Video codec: HW variant (if set and supported) or SW fallback
>    - Quality: `-crf` (SW) or `-cq`/`-q:v`/`-global_quality` (HW)
>    - Preset: only for SW codecs
>    - Audio: `-c:a aac -b:a 192k` (or per settings)
>
> If `ffprobe` is unavailable — re-encoding is always applied (safe fallback).

---

### 7.6 OutputRule

```kotlin
/**
 * Descriptor for one output file in a rule.
 *
 * Each OutputRule is a self-contained unit:
 * path + format + quality + post-processing.
 *
 * The first output in Rule.outputs is the original file (as downloaded by yt-dlp).
 * The rest are conversions/copies with individual settings.
 *
 * @param pathTemplate path template: "/media/Music/{artist}/{title}.{ext}"
 * @param format output file format (OriginalVideo, ConvertedVideo, Audio, Thumbnail)
 * @param maxQuality max quality for this output (null = no downscaling)
 * @param encodeSettings video encoding settings (codec, CRF, preset, HW acceleration).
 *                       null = defaults (H264, CRF 23, medium preset, no HW).
 *                       Applied only when actual re-encoding is needed (when source
 *                       resolution exceeds maxQuality). If source ≤ maxQuality — remux.
 * @param embedThumbnail embed cover art in this file
 * @param embedMetadata embed tags (title, artist, album) in the container
 * @param embedSubtitles embed subtitles in the container
 * @param normalizeAudio normalize audio volume
 */
data class OutputRule(
    val pathTemplate: String,
    val format: OutputFormat,
    val maxQuality: DownloadPolicy.VideoQuality? = null,
    val encodeSettings: VideoEncodeSettings? = null,
    val embedThumbnail: Boolean = false,
    val embedMetadata: Boolean = false,
    val embedSubtitles: Boolean = false,
    val normalizeAudio: Boolean = false,
)
```

> **Key properties**:
> - Post-processing is tied to the specific output, not globally to the entire rule
> - Each output can have its own quality setting (original in 4K, conversion in 1080p)
> - Extensible: adding audio/thumbnail is just another `OutputRule` in the list
> - `Rule.outputs` mirrors `StoragePlan(original, additional)` — 1:1 mapping
>
> **Example 1**: music video — original without metadata + MP4 conversion with YouTube quality:
> ```kotlin
> Rule(
>     downloadPolicy = DownloadPolicy(maxQuality = BEST),
>     outputs = listOf(
>         OutputRule(
>             pathTemplate = "/media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
>             format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
>         ),
>         OutputRule(
>             pathTemplate = "/media/Music Videos/converted/{artist}/{title}.mp4",
>             format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
>             maxQuality = DownloadPolicy.VideoQuality.HD_1080,
>             encodeSettings = VideoEncodeSettings.YOUTUBE_LIKE,  // H264, CRF 23
>             embedThumbnail = true,
>             embedMetadata = true,
>         ),
>     ),
> )
> ```
>
> **Example 2**: same rule with VideoToolbox (macOS hardware encoding):
> ```kotlin
> OutputRule(
>     pathTemplate = "/media/Music Videos/converted/{artist}/{title}.mp4",
>     format = OutputFormat.ConvertedVideo(MediaContainer.MP4),
>     maxQuality = DownloadPolicy.VideoQuality.HD_1080,
>     encodeSettings = VideoEncodeSettings(
>         codec = VideoEncodeSettings.VideoCodec.H264,
>         hwAccel = VideoEncodeSettings.HwAccel.VIDEOTOOLBOX,
>         crf = 23,
>         audioBitrate = "192k",
>     ),
>     embedThumbnail = true,
>     embedMetadata = true,
> )
> ```
>
> **Example 3**: series/vlog — single output, no re-encoding above 1080p:
> ```kotlin
> Rule(
>     downloadPolicy = DownloadPolicy(maxQuality = HD_1080, downloadSubtitles = true),
>     outputs = listOf(
>         OutputRule(
>             pathTemplate = "/media/Yt Videos/{seriesName}/Season {year}/{date} {title}.{ext}",
>             format = OutputFormat.OriginalVideo(MediaContainer.WEBM),
>             embedThumbnail = true,
>             embedMetadata = true,
>             embedSubtitles = true,
>         ),
>     ),
> )
> ```
>
> **Example 4**: music video with audio extraction and cover art:
> ```kotlin
> outputs = listOf(
>     OutputRule("/media/Music/original/{artist}/{title} [{videoId}].{ext}", OutputFormat.OriginalVideo(MediaContainer.WEBM)),
>     OutputRule("/media/Music/video/{artist}/{title}.mp4", OutputFormat.ConvertedVideo(MediaContainer.MP4),
>         maxQuality = HD_1080, encodeSettings = VideoEncodeSettings.YOUTUBE_LIKE, embedMetadata = true),
>     OutputRule("/media/Music/audio/{artist}/{title}.m4a", OutputFormat.Audio(AudioFormat.M4A), embedMetadata = true),
>     OutputRule("/media/Music/covers/{artist}/{title}.jpg", OutputFormat.Thumbnail(ImageFormat.JPG)),
> )
> ```

### 7.8 PathTemplateEngine

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

## 8. `job` — Download Jobs

Dependencies: `common`, `video`, `metadata`, `storage`

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

## 9. `preview` — Preview

Dependencies: `common`, `video`, `rule`, `metadata`, `storage`

```
domain/preview/
├── UserOverrides.kt
└── PreviewUseCase.kt
```

### 9.1 UserOverrides (sealed)

The user can refine the category and metadata fields. Overrides are sealed by category
(mirroring `ResolvedMetadata`), because the set of available fields depends on the category.

```kotlin
sealed interface UserOverrides {
    
    data class MusicVideo(
        val artist: String? = null,
        val title: String? = null,
        val album: String? = null,
    ) : UserOverrides
    
    data class SeriesEpisode(
        val seriesName: String? = null,
        val season: String? = null,
        val episode: String? = null,
        val title: String? = null,
    ) : UserOverrides
    
    data class Other(
        val title: String? = null,
    ) : UserOverrides
}

val UserOverrides.category: Category get() = when (this) {
    is UserOverrides.MusicVideo -> Category.MUSIC_VIDEO
    is UserOverrides.SeriesEpisode -> Category.SERIES
    is UserOverrides.Other -> Category.OTHER
}
```

> The category is **not passed as a separate field** — it is determined by the sealed type.
> If overrides == null — the user has not refined anything.

### 9.2 PreviewUseCase

`PreviewUseCase` is an orchestrator that ties all features together.
Preview is an **interactive dialog**: the user refines data, and the server re-evaluates rules.

```kotlin
class PreviewUseCase(
    private val videoInfoExtractor: VideoInfoExtractor,
    private val videoInfoCache: VideoInfoCache,
    private val ruleMatchingService: RuleMatchingService,
    private val metadataResolver: MetadataResolver,
    private val llmPort: LlmPort?,
) {
    suspend fun preview(
        url: String,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): Either<DomainError, PreviewResult> = either {
        // 1. VideoInfo: cache (PostgreSQL) or yt-dlp
        val videoInfo = videoInfoCache.get(url)
            ?: videoInfoExtractor.extract(url).bind().also { videoInfoCache.put(url, it) }

        // 2. Rule matching with overrides
        val matchedRule = ruleMatchingService.findMatchingRule(videoInfo, workspaceId, overrides)

        // 3. Resolve metadata (rule → LLM → fallback)
        val (metadata, source) = resolveMetadata(videoInfo, matchedRule)

        // 4. Apply user overrides on top of resolved metadata
        val finalMetadata = applyOverrides(metadata, overrides)

        // 5. Outputs
        val outputs = matchedRule?.outputs ?: OutputDefaults.defaultFor(finalMetadata.category)

        PreviewResult(
            videoInfo = videoInfo,
            metadata = finalMetadata,
            metadataSource = source,
            matchedRule = matchedRule,
            outputs = outputs,
        )
    }
}

data class PreviewResult(
    val videoInfo: VideoInfo,
    val metadata: ResolvedMetadata,
    val metadataSource: MetadataSource,
    val matchedRule: Rule?,
    val outputs: List<OutputRule>,
)
```

**Metadata priority order:**

```
1. UserOverrides (manual user input)              ← highest
2. Rule MetadataTemplate (if a rule matched)
3. LLM suggestion (if LLM is configured and no rule matched)
4. Fallback (parse title by separators)           ← lowest
```

`applyOverrides()` overwrites only fields the user explicitly set (not null).
The sealed overrides type determines the target `ResolvedMetadata` category.

See also: [ADR/007-interactive-preview-refinement.md](./ADR/007-interactive-preview-refinement.md)

---

## 10. Invariants and Validation

### 10.1 General Rules

| Field                             | Rule                                        |
|-----------------------------------|---------------------------------------------|
| `title`, `artist`, `seriesName`   | Non-blank after trim                        |
| `releaseDate`                     | `LocalDate` (ISO 8601) or null              |
| `tags`                            | Normalized: trim, deduplicate, remove empty |
| `priority`                        | Int, may be negative                        |
| `percent` (progress)              | 0–100                                       |
| Path templates                    | Must include at least `{title}` or `{videoId}` |

### 10.2 Validation at Creation

All invariants are checked in `init {}` blocks of data/value classes.
On violation — `IllegalArgumentException`.

### 10.3 Business Rule Validation

Business validation (e.g. "a job for this videoId already exists") — via `Either<DomainError, T>` in use cases.
