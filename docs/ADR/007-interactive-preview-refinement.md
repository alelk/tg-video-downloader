# ADR-007: Interactive Preview with User Overrides

**Status**: Accepted  
**Date**: 2026-03-04  
**Authors**: Alex Elkin

---

## Context

The preview flow is the core user scenario of the application:
1. The user enters a video URL
2. The server extracts metadata via yt-dlp, finds a matching rule, determines the category, resolves metadata and the storage plan
3. The user sees the result and can edit it before downloading

But preview is not a one-shot action. The user **interacts** with the form: changes the category, refines the artist, corrects the title. Each refinement must be **re-evaluated by the server** — find a better matching rule, recalculate paths.

At the same time, calling yt-dlp is an expensive operation (3–10 sec). Video metadata does not change between refinements — caching is mandatory.

---

## Decision

### Overview

Preview is an **interactive dialog** between the frontend and backend:

```
  Frontend                                Backend
    │                                        │
    │  POST /preview {url}                   │
    │───────────────────────────────────────▶│──▶ yt-dlp (slow)
    │                                        │──▶ cache VideoInfo in PostgreSQL
    │◀───────────────────────────────────────│    rule matching → fallback
    │  category=OTHER, artist="?"            │
    │                                        │
    │  *** user: category=music ***          │
    │                                        │
    │  POST /preview {url, overrides}        │
    │───────────────────────────────────────▶│──▶ cache HIT (instant)
    │                                        │──▶ rule matching + overrides → Rule!
    │◀───────────────────────────────────────│    metadata + storage plan
    │  category=MUSIC_VIDEO                  │
    │  artist="Rick Astley"                  │
    │  storagePlan=correct paths             │
```

A single endpoint `POST /preview` accepts a URL and optional user overrides. The server caches `VideoInfo` in PostgreSQL and does not call yt-dlp on subsequent requests for the same URL.

---

## 1. VideoInfo Cache (PostgreSQL)

### Goal

Avoid calling yt-dlp again for the same URL.

### Port (domain)

```kotlin
// domain/video/VideoInfoCache.kt
interface VideoInfoCache {
    suspend fun get(url: String): VideoInfo?
    suspend fun put(url: String, videoInfo: VideoInfo)
}
```

Located in `domain/video/` — alongside the `VideoInfoExtractor` port. This is a domain port that `PreviewUseCase` uses directly.

### PostgreSQL Table

```sql
-- V1__initial_schema.sql (added to the existing migration)

CREATE TABLE video_info_cache (
    url         TEXT PRIMARY KEY,
    video_info  JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE video_info_cache IS 'VideoInfo cache from yt-dlp to avoid redundant calls';
COMMENT ON COLUMN video_info_cache.video_info IS 'VideoInfoPm JSON';
```

### Exposed Table

```kotlin
// server:infra/db/table/VideoInfoCacheTable.kt
object VideoInfoCacheTable : Table("video_info_cache") {
    val url = text("url")
    val videoInfo = jsonb<VideoInfoPm>("video_info", jsonb)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    
    override val primaryKey = PrimaryKey(url)
}
```

### Implementation (server:infra)

```kotlin
// server:infra/db/repository/VideoInfoCacheImpl.kt
class VideoInfoCacheImpl(
    private val database: Database,
) : VideoInfoCache {

    override suspend fun get(url: String): VideoInfo? = dbQuery(database) {
        VideoInfoCacheTable.selectAll()
            .where { VideoInfoCacheTable.url eq url }
            .singleOrNull()
            ?.let { it[VideoInfoCacheTable.videoInfo].toDomain() }
    }

    override suspend fun put(url: String, videoInfo: VideoInfo) = dbQuery(database) {
        VideoInfoCacheTable.upsert {
            it[VideoInfoCacheTable.url] = url
            it[VideoInfoCacheTable.videoInfo] = videoInfo.toPm()
        }
    }
}
```

`VideoInfoPm` already exists in `server:infra/db/model/`. The `VideoInfo` ↔ `VideoInfoPm` mapping is added in `db/mapping/videoInfo.kt`.

---

## 2. User Overrides (sealed)

### Concept

The user can refine the category and metadata fields. Overrides are sealed by category (mirroring `ResolvedMetadata`), because the set of available fields depends on the category:
- `MusicVideo` → artist, title, album
- `SeriesEpisode` → seriesName, season, episode, title
- `Other` → title

If the user has not refined anything — overrides == null.

### Domain model

```kotlin
// domain/preview/UserOverrides.kt
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

/** The category implied by the overrides. */
val UserOverrides.category: Category get() = when (this) {
    is UserOverrides.MusicVideo -> Category.MUSIC_VIDEO
    is UserOverrides.SeriesEpisode -> Category.SERIES
    is UserOverrides.Other -> Category.OTHER
}
```

> The category is **not passed as a separate field** — it is determined by the sealed type. If the user switches category to `MUSIC_VIDEO` → the frontend creates `UserOverrides.MusicVideo(...)`.

### API Contract

```kotlin
// api:contract/preview/UserOverridesDto.kt
@Serializable
@JsonClassDiscriminator("type")
sealed interface UserOverridesDto {
    
    @Serializable
    @SerialName("music-video")
    data class MusicVideo(
        val artist: String? = null,
        val title: String? = null,
        val album: String? = null,
    ) : UserOverridesDto
    
    @Serializable
    @SerialName("series-episode")
    data class SeriesEpisode(
        val seriesName: String? = null,
        val season: String? = null,
        val episode: String? = null,
        val title: String? = null,
    ) : UserOverridesDto
    
    @Serializable
    @SerialName("other")
    data class Other(
        val title: String? = null,
    ) : UserOverridesDto
}
```

```kotlin
// api:contract/preview/PreviewRequestDto.kt
@Serializable
data class PreviewRequestDto(
    val url: String,
    val overrides: UserOverridesDto? = null,
)
```

```kotlin
// api:contract/preview/PreviewResponseDto.kt
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

`appliedOverrides` is an echo of the overrides the server took into account. The frontend checks: if its current overrides differ from `appliedOverrides` — the response is stale (due to a debounce race condition) and should be ignored.

### JSON Examples

**First request** (no overrides):
```json
{ "url": "https://youtube.com/watch?v=dQw4w9WgXcQ" }
```

**Subsequent request** (user selected music-video):
```json
{
  "url": "https://youtube.com/watch?v=dQw4w9WgXcQ",
  "overrides": {
    "type": "music-video",
    "artist": null,
    "title": null,
    "album": null
  }
}
```

**Subsequent request** (user selected music-video and refined the artist):
```json
{
  "url": "https://youtube.com/watch?v=dQw4w9WgXcQ",
  "overrides": {
    "type": "music-video",
    "artist": "Rick Astley",
    "title": null,
    "album": null
  }
}
```

---

## 3. RuleMatch.CategoryEquals

### Motivation

Rules match on video properties (channel, title, url). But sometimes a rule should trigger based on **request context** — when the user has explicitly selected a category.

Example: "Default rule for music videos" — sets paths `/media/Music Videos/...` and template `MetadataTemplate.MusicVideo`. Not tied to a specific channel. Triggers when the user selects `MUSIC_VIDEO`.

### Domain

```kotlin
// domain/rule/RuleMatch.kt
sealed interface RuleMatch {
    
    data class AllOf(val matches: List<RuleMatch>) : RuleMatch { ... }
    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch { ... }
    data class ChannelId(val value: String) : RuleMatch { ... }
    data class ChannelName(val value: String, val ignoreCase: Boolean = true) : RuleMatch { ... }
    data class TitleRegex(val pattern: String) : RuleMatch { ... }
    data class UrlRegex(val pattern: String) : RuleMatch { ... }
    
    /** Matches on the category from user overrides. If overrides == null — does not match. */
    data class CategoryEquals(val category: Category) : RuleMatch
}
```

Specificity = 20 (lowest — broad criterion):

```kotlin
fun RuleMatch.matchSpecificity(): Int = when (this) {
    is RuleMatch.ChannelId -> 100
    is RuleMatch.ChannelName -> 80
    is RuleMatch.UrlRegex -> 60
    is RuleMatch.TitleRegex -> 40
    is RuleMatch.CategoryEquals -> 20
    is RuleMatch.AllOf -> matches.maxOfOrNull { it.matchSpecificity() } ?: 0
    is RuleMatch.AnyOf -> matches.minOfOrNull { it.matchSpecificity() } ?: 0
}
```

### MatchContext

`RuleMatch` matches on **context** — video + user overrides. This is the primary (and only) matching function:

```kotlin
// domain/rule/MatchContext.kt
data class MatchContext(
    val video: VideoInfo,
    val overrides: UserOverrides? = null,
)
```

```kotlin
// domain/rule/matches.kt
fun RuleMatch.matches(ctx: MatchContext): Boolean = when (this) {
    is RuleMatch.AllOf -> matches.all { it.matches(ctx) }
    is RuleMatch.AnyOf -> matches.any { it.matches(ctx) }
    is RuleMatch.ChannelId -> ctx.video.channelId.value == value
    is RuleMatch.ChannelName -> ctx.video.channelName.equals(value, ignoreCase = ignoreCase)
    is RuleMatch.TitleRegex -> regex.containsMatchIn(ctx.video.title)
    is RuleMatch.UrlRegex -> regex.containsMatchIn(ctx.video.webpageUrl.value)
    is RuleMatch.CategoryEquals -> ctx.overrides != null && ctx.overrides.category == category
}
```

> `CategoryEquals` matches **only** when overrides != null and the category matches.
> If overrides are not provided (first request) — `CategoryEquals` does not match.

### RuleMatchingService

```kotlin
class RuleMatchingService(
    private val ruleRepository: RuleRepository,
) {
    suspend fun findMatchingRule(
        video: VideoInfo,
        workspaceId: WorkspaceId,
        overrides: UserOverrides? = null,
    ): Rule? {
        val rules = ruleRepository.findEnabledByWorkspace(workspaceId)
        val ctx = MatchContext(video, overrides)
        return rules
            .filter { it.match.matches(ctx) }
            .maxByOrNull { it.priority * 1000 + it.match.matchSpecificity() }
    }
}
```

### API Contract (RuleMatchDto)

```kotlin
@Serializable
@SerialName("category-equals")
data class CategoryEquals(
    val category: CategoryDto,
) : RuleMatchDto
```

### Persistence Model (RuleMatchPm)

```kotlin
// server:infra/db/model/RuleMatchPm.kt
@Serializable
@SerialName("category-equals")
data class CategoryEquals(
    val category: String,   // "music-video", "series-episode", "other"
) : RuleMatchPm
```

### Rule Examples

**Default rule for music videos** (low priority):
```json
{
  "name": "Default Music Video",
  "priority": -10,
  "match": { "type": "category-equals", "category": "music-video" },
  "category": "music-video",
  "metadataTemplate": { "type": "music-video" },
  "outputs": [
    { "pathTemplate": "/media/Music Videos/original/{artist}/{title} [{videoId}].{ext}", "format": "original/webm" },
    { "pathTemplate": "/media/Music Videos/converted/{artist}/{title}.mp4", "format": "video/mp4" }
  ]
}
```

**Rule for a specific channel + category** (higher priority):
```json
{
  "name": "Rick Astley Music Videos",
  "priority": 0,
  "match": {
    "type": "all-of",
    "matches": [
      { "type": "channel-name", "value": "Rick Astley" },
      { "type": "category-equals", "category": "music-video" }
    ]
  },
  "category": "music-video",
  "metadataTemplate": { "type": "music-video", "artistOverride": "Rick Astley" }
}
```

---

## 4. PreviewUseCase

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

    /**
     * Applies user overrides on top of resolved metadata.
     * Override fields have the highest priority.
     * The sealed overrides type determines the target category.
     */
    private fun applyOverrides(
        metadata: ResolvedMetadata,
        overrides: UserOverrides?,
    ): ResolvedMetadata {
        if (overrides == null) return metadata

        return when (overrides) {
            is UserOverrides.MusicVideo -> ResolvedMetadata.MusicVideo(
                artist = overrides.artist
                    ?: (metadata as? ResolvedMetadata.MusicVideo)?.artist
                    ?: "Unknown Artist",
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
            is UserOverrides.SeriesEpisode -> ResolvedMetadata.SeriesEpisode(
                seriesName = overrides.seriesName
                    ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.seriesName
                    ?: "Unknown Series",
                season = overrides.season ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.season,
                episode = overrides.episode ?: (metadata as? ResolvedMetadata.SeriesEpisode)?.episode,
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
            is UserOverrides.Other -> ResolvedMetadata.Other(
                title = overrides.title ?: metadata.title,
                releaseDate = metadata.releaseDate,
                tags = metadata.tags,
                comment = metadata.comment,
            )
        }
    }

    private suspend fun resolveFallback(video: VideoInfo): Pair<ResolvedMetadata, MetadataSource> {
        if (llmPort != null) {
            val llmResult = llmPort.suggestMetadata(video)
            llmResult.onRight { suggestion ->
                return suggestion.metadata to MetadataSource.LLM
            }
        }
        val fallback = metadataResolver.resolve(video, MetadataTemplate.Other())
        return fallback to MetadataSource.FALLBACK
    }

    private suspend fun resolveMetadata(
        video: VideoInfo, rule: Rule?,
    ): Pair<ResolvedMetadata, MetadataSource> {
        return if (rule != null) {
            metadataResolver.resolve(video, rule.metadataTemplate) to MetadataSource.RULE
        } else {
            resolveFallback(video)
        }
    }
}
```

### Metadata Priority Order

```
1. UserOverrides (manual user input)                ← highest
2. Rule MetadataTemplate (if a rule matched)
3. LLM suggestion (if LLM is configured and no rule matched)
4. Fallback (parse title by separators)             ← lowest
```

Steps 2–4 determine "base" metadata. Step 1 (`applyOverrides`) overwrites only fields the user explicitly set (not null).

---

## 5. Frontend

### Two State Layers on PreviewScreen

| Layer         | Description                                                      |
|---------------|------------------------------------------------------------------|
| `serverPreview` | Last response from `POST /preview`                             |
| `userEdits`   | `Set<String>` — fields the user has manually changed            |

When a new server response is received:
- Fields **not** in `userEdits` — updated from the response
- Fields **in** `userEdits` — keep the user's value

### Debounce Strategy

| Trigger                                    | Debounce | Rationale                                              |
|--------------------------------------------|----------|--------------------------------------------------------|
| Category change (SegmentedButton)          | 0ms — immediate | Discrete selection, user has completed the action |
| Text fields (artist, title, album...)      | 700ms    | User is still typing                               |

### Re-preview Flow

```
User changes a field
    ↓
[debounce 0ms / 700ms]
    ↓
Collect ALL current userEdits → UserOverridesDto (sealed, type = current category)
    ↓
POST /preview { url, overrides: { type: "music-video", artist: "Rick Astley" } }
    ↓
Response received, verify appliedOverrides
    ↓
Update fields NOT in userEdits
```

### Building UserOverridesDto from userEdits

The frontend constructs overrides based on the currently selected category:

```kotlin
fun buildOverrides(
    category: CategoryDto,
    userEdits: Set<String>,
    currentValues: Map<String, String>,
): UserOverridesDto? {
    if (userEdits.isEmpty()) return null
    
    return when (category) {
        CategoryDto.MUSIC_VIDEO -> UserOverridesDto.MusicVideo(
            artist = currentValues["artist"].takeIf { "artist" in userEdits },
            title = currentValues["title"].takeIf { "title" in userEdits },
            album = currentValues["album"].takeIf { "album" in userEdits },
        )
        CategoryDto.SERIES_EPISODE -> UserOverridesDto.SeriesEpisode(
            seriesName = currentValues["seriesName"].takeIf { "seriesName" in userEdits },
            season = currentValues["season"].takeIf { "season" in userEdits },
            episode = currentValues["episode"].takeIf { "episode" in userEdits },
            title = currentValues["title"].takeIf { "title" in userEdits },
        )
        CategoryDto.OTHER -> UserOverridesDto.Other(
            title = currentValues["title"].takeIf { "title" in userEdits },
        )
    }
}
```

> When the user switches category — it **always** creates overrides (even without changing text fields), because the sealed type itself determines the category. This is why the debounce for category = 0ms.

### Race Conditions

`appliedOverrides` in the response allows the frontend to distinguish an up-to-date response from a stale one. If `appliedOverrides` does not match the frontend's current overrides — the response arrived for a stale request; ignore it.

Additionally: when sending a new request — cancel the previous in-flight request (coroutine cancellation).

### Loading Indicator

During re-preview — a subtle inline indicator (shimmer or small progress bar on the Metadata / Storage Plan sections). Fields remain editable.

---

## 6. Full Sequence Diagram

```
┌──────────┐                    ┌──────────────┐                  ┌───────────────┐
│  MiniApp │                    │   Transport  │                  │ PreviewUseCase│
│  (Front) │                    │  (Ktor route)│                  │   (Domain)    │
└────┬─────┘                    └──────┬───────┘                  └──────┬────────┘
     │                                 │                                 │
     │ POST /preview                   │                                 │
     │   { url }                       │                                 │
     │────────────────────────────────▶│                                 │
     │                                 │  preview(url, wsId)             │
     │                                 │────────────────────────────────▶│
     │                                 │                                 │──▶ PostgreSQL cache miss
     │                                 │                                 │──▶ yt-dlp extract (3-10s)
     │                                 │                                 │──▶ cache.put(url, videoInfo)
     │                                 │                                 │──▶ findMatchingRule → null
     │                                 │                                 │──▶ fallback → Other
     │      PreviewResponseDto         │      PreviewResult              │
     │◀────────────────────────────────│◀────────────────────────────────│
     │                                 │                                 │
     │  category=OTHER                 │                                 │
     │  artist="Unknown"               │                                 │
     │                                 │                                 │
     │ *** user: category=MUSIC ***    │                                 │
     │                                 │                                 │
     │ POST /preview                   │                                 │
     │   { url, overrides:             │                                 │
     │     { type: music-video } }     │                                 │
     │────────────────────────────────▶│                                 │
     │                                 │  preview(url, wsId, overrides)  │
     │                                 │────────────────────────────────▶│
     │                                 │                                 │──▶ PostgreSQL cache HIT
     │                                 │                                 │    (instant)
     │                                 │                                 │──▶ findMatchingRule(overrides)
     │                                 │                                 │    → "Default Music Video" rule
     │                                 │                                 │──▶ resolve metadata via rule
     │                                 │                                 │──▶ applyOverrides
     │      PreviewResponseDto         │      PreviewResult              │
     │◀────────────────────────────────│◀────────────────────────────────│
     │                                 │                                 │
     │  category=MUSIC_VIDEO           │                                 │
     │  artist="Rick Astley"           │                                 │
     │  storagePlan=correct paths      │                                 │
     │                                 │                                 │
     │ *** frontend: updates fields    │                                 │
     │   user didn't manually edit *** │                                 │
```

---

## Implementation Checklist

### VideoInfo Cache (PostgreSQL)
- [x] `VideoInfoCache` interface in `domain/video/`
- [x] Table `video_info_cache` in `V1__initial_schema.sql`
- [x] `VideoInfoCacheTable` in `server:infra/db/table/`
- [x] `VideoInfoCacheImpl` in `server:infra/db/repository/`
- [x] `VideoInfo` ↔ `VideoInfoPm` mapping (full, with thumbnails) in `db/mapping/videoInfo.kt`
- [x] DI wiring in `server:di`
- [ ] Unit tests for cache

### UserOverrides (sealed) + PreviewUseCase
- [x] `UserOverrides` sealed interface in `domain/preview/`
- [x] Extension property `UserOverrides.category`
- [x] `UserOverridesDto` sealed interface in `api:contract/preview/`
- [x] Mapping `UserOverridesDto` ↔ `UserOverrides` in `api:mapping`
- [x] `PreviewRequestDto` — field `overrides: UserOverridesDto?`
- [x] `PreviewResponseDto` — field `appliedOverrides: UserOverridesDto?`
- [x] `PreviewUseCase.preview()` — accepts overrides, uses cache, calls `applyOverrides()`
- [x] `previewRoutes.kt` — passes overrides
- [ ] Unit tests for `applyOverrides`

### RuleMatch.CategoryEquals
- [x] `RuleMatch.CategoryEquals` in `domain/rule/`
- [x] `matches(ctx: MatchContext)` instead of `matchesVideo(video)`
- [x] `MatchContext` data class in `domain/rule/`
- [x] `matchSpecificity()` += CategoryEquals → 20
- [x] `RuleMatchDto.CategoryEquals` in `api:contract`
- [x] `RuleMatchPm.CategoryEquals` in `server:infra/db/model/`
- [x] Mapping domain ↔ DTO ↔ Pm
- [x] `RuleMatchingService.findMatchingRule()` — accepts overrides
- [x] `Arb.ruleMatch()` generator — CategoryEquals
- [ ] Unit tests

### Frontend
- [ ] `userEdits: Set<String>` state on PreviewScreen
- [ ] `buildOverrides()` — build sealed UserOverridesDto from current state
- [ ] Debounce: category → 0ms, text → 700ms
- [ ] Repeated `POST /preview` with overrides
- [ ] Merge logic: server response + userEdits
- [ ] Check `appliedOverrides` for race conditions
- [ ] Cancel previous in-flight request
- [ ] Subtle loading indicator during re-preview

---

## Related Documents

- [DOMAIN.md](../DOMAIN.md) — §5 Rule, §6 Metadata, §9 Preview
- [API_CONTRACT.md](../API_CONTRACT.md) — §6.1 POST /preview
- [DATABASE.md](../DATABASE.md) — §2 Schema, §4 Exposed Tables
- [ADR-002: Sealed Classes](002-sealed-classes.md) — RuleMatch, ResolvedMetadata hierarchy

