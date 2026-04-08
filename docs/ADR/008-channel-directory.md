# ADR-008: Channel Directory

**Status**: Accepted, implemented  
**Date**: 2026-03-05  
**Authors**: Alex Elkin

---

## Context

### Problem

Rules (`Rule`) allow fine-grained control over video downloading: format selection, conversion, metadata, storage paths. Each rule is a complex configuration.

A typical scenario: the user wants **one rule** for 30+ music YouTube channels. Currently this requires:

```kotlin
RuleMatch.AnyOf(listOf(
    RuleMatch.ChannelId("UC1..."),
    RuleMatch.ChannelId("UC2..."),
    // ... 28 more channels
))
```

**Problems with this approach:**

1. **Unwieldy match** — dozens of channels in a single JSON. Hard to read, edit, and debug.
2. **No per-channel metadata overrides** — if the artist name differs from the channel (e.g. channel "VEVO" → artist "Adele"), a separate rule is needed for each channel.
3. **No convenient UI** — you can't simply "add a channel to a list"; you have to edit the JSON match tree.
4. **No reuse** — a single channel can't be used across multiple rules without duplication.

### Desired UX

The user:
1. Creates a **channel directory** — a collection of channels with tags and metadata
2. Assigns **tags** to channels (`music-video`, `lofi`, `tech-review`)
3. Optionally sets **metadata overrides** per channel (artist, series name, etc.)
4. Specifies in the rule: `HasTag("music-video")` — and the rule automatically applies to all channels with that tag
5. On match — overrides from the channel directory are automatically applied as an additional layer in the metadata pipeline

---

## Decision

### Overview

We introduce a new entity **`Channel`** — a record in the workspace's channel directory.

```
Channel Directory
    │
    ├── Channel { channelId="UC_adele", name="Adele", tags=["music-video"], overrides=MusicVideo(artist="Adele") }
    ├── Channel { channelId="UC_lofi", name="Lofi Girl", tags=["music-video", "lofi"], overrides=MusicVideo(artist="Lofi Girl") }
    └── Channel { channelId="UC_tech", name="MKBHD", tags=["tech-review"], overrides=null }

Rule { match = HasTag("music-video"), ... }
    → matches: Adele, Lofi Girl
    → does NOT match: MKBHD

Rule { match = AllOf(HasTag("music-video"), HasTag("lofi")), ... }
    → matches: Lofi Girl
    → does NOT match: Adele, MKBHD
```

When a rule matches via `HasTag`:
1. Look up the video's channel in the directory (by `channelId` + `extractor`)
2. Check whether the channel has the required tag
3. On match — use `channel.metadataOverrides` as an additional layer in the metadata pipeline

### Architectural Principles

- **Channel** is a domain entity, living in `domain/channel/`
- **The directory is scoped to a workspace** (just like rules)
- **Tags are plain strings** (not a separate entity). Flexible, no migrations needed when adding a new tag
- **Metadata overrides** reuse the existing sealed `MetadataTemplate` — same fields: `artistOverride`, `seriesNameOverride`, etc.
- **Matching is enriched, not replaced** — `HasTag` is a new leaf in the `RuleMatch` sealed hierarchy

---

## 1. Domain Model

### 1.1 New Types in `common/`

```kotlin
// domain/common/ChannelDirectoryId.kt
@JvmInline
value class ChannelDirectoryEntryId(val value: Uuid)

// domain/common/Tag.kt
@JvmInline
value class Tag(val value: String) {
    init {
        require(value.isNotBlank()) { "Tag cannot be blank" }
        require(value.length <= 50) { "Tag too long (max 50)" }
        require(value.matches(TAG_REGEX)) { "Tag must be lowercase alphanumeric with hyphens: $value" }
    }
    companion object {
        private val TAG_REGEX = Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$")
    }
}
```

> **Tag** — a value class with validation. Lowercase, alphanumeric + hyphens. Examples: `music-video`, `lofi`, `tech`, `series`.
> Not an enum — users create tags freely. But the format is normalized for reliable lookup.

### 1.2 Channel (directory entity)

```kotlin
// domain/channel/Channel.kt
package io.github.alelk.tgvd.domain.channel

data class Channel(
    val id: ChannelDirectoryEntryId,
    val workspaceId: WorkspaceId,
    val channelId: ChannelId,
    val extractor: Extractor,
    val name: String,
    val tags: Set<Tag>,
    val metadataOverrides: MetadataTemplate? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name.isNotBlank()) { "Channel name cannot be blank" }
    }
}
```

**Key decisions:**

| Field                     | Type                      | Purpose                                                                                                                                               |
|---------------------------|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `channelId` + `extractor` | `ChannelId` + `Extractor` | Unique channel identifier on the platform. YouTube channelId ≠ RuTube channelId                                                                      |
| `name`                    | `String`                  | Human-readable name (may differ from `channelName` in yt-dlp)                                                                                        |
| `tags`                    | `Set<Tag>`                | Tags for grouping. Unordered, unique                                                                                                                  |
| `metadataOverrides`       | `MetadataTemplate?`       | Metadata overrides. Sealed — the type determines the category (MusicVideo, SeriesEpisode, Other). Reuses the existing `MetadataTemplate`              |
| `notes`                   | `String?`                 | Arbitrary user notes                                                                                                                                  |

> **Why `MetadataTemplate` for overrides instead of `UserOverrides`?**
> `MetadataTemplate` contains both override fields (`artistOverride`) and pattern fields (`artistPattern`). For the channel directory, patterns are also useful — if a channel uploads videos in the "Artist - Title" format, a regex pattern is appropriate. `UserOverrides` is too simplified (values only, no patterns).

### 1.3 ChannelRepository (port)

```kotlin
// domain/channel/ChannelRepository.kt
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

### 1.4 RuleMatch.HasTag (new leaf)

```kotlin
// domain/rule/RuleMatch.kt (addition)
sealed interface RuleMatch {
    // ...existing variants...

    /**
     * Matches if the video's channel is registered in the directory and has the given tag.
     * Matching: channelId + extractor from VideoInfo → lookup in ChannelRepository → check tag.
     */
    data class HasTag(val tag: Tag) : RuleMatch
}
```

**Specificity**: 70 — between `ChannelName` (80) and `UrlRegex` (60).

> Rationale: `HasTag` is less precise than a specific channel (`ChannelId`/`ChannelName`),
> but more targeted than regular expressions on URL/title. A tag implies intentional channel grouping.

### 1.5 MatchContext (enriched)

To match `HasTag`, access to the channel directory is needed. We add to the context:

```kotlin
// domain/rule/MatchContext.kt
data class MatchContext(
    val video: VideoInfo,
    val overrides: UserOverrides? = null,
    val channel: Channel? = null,  // NEW: channel from the directory (if found)
)
```

> **`channel` is loaded once** when forming `MatchContext`, not on every `matches()` call.
> `RuleMatchingService` looks up the channel by `video.channelId` + `video.extractor` in `ChannelRepository`, then passes it in the context.

**Matching `HasTag`:**

```kotlin
fun RuleMatch.matches(ctx: MatchContext): Boolean = when (this) {
    // ...existing...
    is RuleMatch.HasTag -> ctx.channel != null && tag in ctx.channel.tags
}

fun RuleMatch.matchSpecificity(): Int = when (this) {
    // ...existing...
    is RuleMatch.HasTag -> 70
}
```

### 1.6 RuleMatchingService (updated)

```kotlin
class RuleMatchingService(
    private val ruleRepository: RuleRepository,
    private val channelRepository: ChannelRepository,  // NEW
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

/**
 * Matching result — matched rule + optionally the channel from the directory.
 * The channel is needed to apply channel-level metadata overrides.
 */
data class MatchResult(
    val rule: Rule,
    val channel: Channel?,
)
```

> **Signature change**: `findMatchingRule` now returns `MatchResult?` instead of `Rule?`.
> This is a breaking change — but backward compatibility is not a concern here.

### 1.7 Metadata Resolution Pipeline (enriched)

Current pipeline:

```
VideoInfo → MetadataResolver(template from Rule) → ResolvedMetadata → applyOverrides(UserOverrides) → final
```

New pipeline with Channel:

```
VideoInfo → MetadataResolver(effectiveTemplate) → ResolvedMetadata → applyOverrides(UserOverrides) → final
                                    ↑
                        mergeTemplates(rule.metadataTemplate, channel.metadataOverrides)
```

If the channel is found and has `metadataOverrides` — they are **merged** with the rule's template:

```kotlin
// domain/metadata/MetadataTemplateMerger.kt

/**
 * Merges two MetadataTemplates.
 * Fields from [overlay] take priority over [base].
 * Both should be the same type (category). If types differ — overlay wins completely.
 */
fun mergeTemplates(base: MetadataTemplate, overlay: MetadataTemplate?): MetadataTemplate {
    if (overlay == null) return base
    return when (overlay) {
        is MetadataTemplate.MusicVideo -> {
            val b = base as? MetadataTemplate.MusicVideo
            MetadataTemplate.MusicVideo(
                artistOverride = overlay.artistOverride ?: b?.artistOverride,
                artistPattern = overlay.artistPattern ?: b?.artistPattern,
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
        is MetadataTemplate.SeriesEpisode -> {
            val b = base as? MetadataTemplate.SeriesEpisode
            MetadataTemplate.SeriesEpisode(
                seriesNameOverride = overlay.seriesNameOverride ?: b?.seriesNameOverride,
                seasonPattern = overlay.seasonPattern ?: b?.seasonPattern,
                episodePattern = overlay.episodePattern ?: b?.episodePattern,
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
        is MetadataTemplate.Other -> {
            val b = base as? MetadataTemplate.Other
            MetadataTemplate.Other(
                titleOverride = overlay.titleOverride ?: b?.titleOverride,
                titlePattern = overlay.titlePattern ?: b?.titlePattern,
                defaultTags = overlay.defaultTags.ifEmpty { b?.defaultTags ?: emptyList() },
            )
        }
    }
}
```

**Layer priority (lowest to highest):**

```
1. Rule.metadataTemplate          ← base rule settings
2. Channel.metadataOverrides      ← per-channel overrides (from directory)
3. UserOverrides                  ← manual user edits in UI (highest priority)
```

### 1.8 PreviewUseCase (updated)

```kotlin
// domain/preview/PreviewUseCase.kt (updated fragment)

private suspend fun resolveMetadata(
    video: VideoInfo, matchResult: MatchResult?,
): Pair<ResolvedMetadata, MetadataSource> {
    return if (matchResult != null) {
        val effectiveTemplate = mergeTemplates(
            base = matchResult.rule.metadataTemplate,
            overlay = matchResult.channel?.metadataOverrides,
        )
        metadataResolver.resolve(video, effectiveTemplate) to MetadataSource.RULE
    } else {
        resolveFallback(video)
    }
}
```

---

## 2. Database

### 2.1 Table `channels`

```sql
CREATE TABLE channels (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    channel_id     TEXT NOT NULL,
    extractor      TEXT NOT NULL,
    name           TEXT NOT NULL,
    tags           TEXT[] NOT NULL DEFAULT '{}',
    metadata_overrides JSONB,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One channel (channel_id + extractor) per workspace
    UNIQUE (workspace_id, channel_id, extractor)
);

-- Indexes
CREATE INDEX idx_channels_workspace ON channels(workspace_id);
CREATE INDEX idx_channels_tags ON channels USING GIN (tags);
CREATE INDEX idx_channels_extractor ON channels(extractor);
CREATE INDEX idx_channels_channel_id ON channels(channel_id);
```

> **Tags as `TEXT[]`** (PostgreSQL array) — a deliberate choice:
> - GIN index on array supports `@>` operator (contains) → fast tag-based lookup
> - `SELECT * FROM channels WHERE workspace_id = ? AND tags @> ARRAY['music-video']` — uses both indexes
> - Simpler than JSONB for a list of strings
> - Native support in Exposed via `arrayLiteral`

> **`metadata_overrides` as JSONB** — sealed type MetadataTemplate, same structure as `rules.metadata_template`.

### 2.2 Example SQL Queries

```sql
-- Find a channel by channelId + extractor in a workspace
SELECT * FROM channels 
WHERE workspace_id = $1 AND channel_id = $2 AND extractor = $3;

-- Find all channels with a tag
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags @> ARRAY[$2];

-- Find channels with ALL specified tags (AND)
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags @> ARRAY['music-video', 'lofi'];

-- Find channels with ANY of the specified tags (OR)
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags && ARRAY['music-video', 'lofi'];

-- All unique tags in a workspace (for autocomplete in UI)
SELECT DISTINCT unnest(tags) AS tag FROM channels WHERE workspace_id = $1 ORDER BY tag;
```

### 2.3 Migration

```
server/infra/src/main/resources/db/migration/
├── V1__initial_schema.sql     (existing)
└── V2__channel_directory.sql  (new)
```

```sql
-- V2__channel_directory.sql

CREATE TABLE channels (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    channel_id     TEXT NOT NULL,
    extractor      TEXT NOT NULL,
    name           TEXT NOT NULL,
    tags           TEXT[] NOT NULL DEFAULT '{}',
    metadata_overrides JSONB,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, channel_id, extractor)
);

CREATE INDEX idx_channels_workspace ON channels(workspace_id);
CREATE INDEX idx_channels_tags ON channels USING GIN (tags);
CREATE INDEX idx_channels_extractor ON channels(extractor);
CREATE INDEX idx_channels_channel_id ON channels(channel_id);

COMMENT ON TABLE channels IS 'Channel directory — channels with tags and metadata overrides';
COMMENT ON COLUMN channels.channel_id IS 'Platform channel ID (YouTube channel ID, RuTube channel ID, etc.)';
COMMENT ON COLUMN channels.extractor IS 'Platform: youtube, rutube, vk, etc.';
COMMENT ON COLUMN channels.tags IS 'Tags for grouping channels (PostgreSQL text array)';
COMMENT ON COLUMN channels.metadata_overrides IS 'MetadataTemplatePm JSON — metadata overrides for the channel';
```

---

## 3. API Contract

### 3.1 DTOs

```kotlin
// api:contract/channel/ChannelDto.kt
@Serializable
data class ChannelDto(
    val id: String,
    val workspaceId: String,
    val channelId: String,
    val extractor: String,
    val name: String,
    val tags: List<String>,
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class CreateChannelDto(
    val channelId: String,
    val extractor: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateChannelDto(
    val name: String? = null,
    val tags: List<String>? = null,
    val metadataOverrides: MetadataTemplateDto? = null,
    val notes: String? = null,
)
```

### 3.2 RuleMatchDto.HasTag

```kotlin
// api:contract/rule/RuleMatchDto.kt (addition)
@Serializable
@SerialName("has-tag")
data class HasTag(val tag: String) : RuleMatchDto
```

### 3.3 Endpoints

```
# Channel Directory CRUD
GET    /api/v1/workspaces/{workspaceId}/channels                  — list channels (filter by tag: ?tag=music-video)
GET    /api/v1/workspaces/{workspaceId}/channels/{channelId}      — get channel by ID
POST   /api/v1/workspaces/{workspaceId}/channels                  — create channel
PUT    /api/v1/workspaces/{workspaceId}/channels/{channelId}      — update channel
DELETE /api/v1/workspaces/{workspaceId}/channels/{channelId}      — delete channel

# Tags (utility)
GET    /api/v1/workspaces/{workspaceId}/channels/tags             — all unique tags in workspace
```

---

## 4. Infrastructure (server:infra)

### 4.1 Exposed Table

```kotlin
// server/infra/db/table/ChannelsTable.kt
object ChannelsTable : Table("channels") {
    val id = uuid("id").autoGenerate()
    val workspaceId = uuid("workspace_id").references(WorkspacesTable.id)
    val channelId = text("channel_id")
    val extractor = text("extractor")
    val name = text("name")
    val tags = array<String>("tags", TextColumnType())
    val metadataOverrides = jsonb<MetadataTemplatePm>("metadata_overrides", Json).nullable()
    val notes = text("notes").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
```

### 4.2 ChannelRepositoryImpl

```kotlin
class ChannelRepositoryImpl(private val database: Database) : ChannelRepository {

    override suspend fun findByChannelId(
        workspaceId: WorkspaceId, channelId: ChannelId, extractor: Extractor
    ): Channel? = dbQuery(database) {
        ChannelsTable.selectAll()
            .where {
                (ChannelsTable.workspaceId eq workspaceId.value) and
                (ChannelsTable.channelId eq channelId.value) and
                (ChannelsTable.extractor eq extractor.value)
            }
            .singleOrNull()
            ?.toChannel()
    }

    override suspend fun findByTag(
        workspaceId: WorkspaceId, tag: Tag
    ): List<Channel> = dbQuery(database) {
        ChannelsTable.selectAll()
            .where {
                (ChannelsTable.workspaceId eq workspaceId.value) and
                (ChannelsTable.tags contains arrayOf(tag.value))
            }
            .map { it.toChannel() }
    }
    
    // ... other methods
}
```

---

## 5. Full Flow Example

### Scenario: User downloads a video from Adele's channel

**One-time setup:**

1. User adds the channel to the directory:
   ```json
   POST /api/v1/workspaces/{wsId}/channels
   {
     "channelId": "UCKiHMVB6VWzjmOMKOFED2wA",
     "extractor": "youtube",
     "name": "Adele",
     "tags": ["music-video", "pop"],
     "metadataOverrides": {
       "type": "music-video",
       "artistOverride": "Adele"
     }
   }
   ```

2. User creates a rule:
   ```json
   POST /api/v1/workspaces/{wsId}/rules
   {
     "name": "Music Videos",
     "match": { "type": "has-tag", "tag": "music-video" },
     "metadataTemplate": {
       "type": "music-video",
       "defaultTags": ["music"]
     },
     "outputs": [
       {
         "pathTemplate": "/media/Music/{artist}/{title} [{videoId}].{ext}",
         "format": { "type": "audio", "container": "m4a" }
       }
     ]
   }
   ```

**On download:**

1. User submits URL: `https://youtube.com/watch?v=abc`
2. `yt-dlp` extracts `VideoInfo`: `channelId=UCKiHMVB6VWzjmOMKOFED2wA`, `extractor=youtube`
3. `RuleMatchingService`:
   - Looks up the channel in the directory by `channelId` + `extractor` → finds "Adele"
   - Forms `MatchContext(video, overrides=null, channel=Adele)`
   - Rule "Music Videos" with `HasTag("music-video")` → checks `"music-video" in channel.tags` → **match!**
   - Returns `MatchResult(rule="Music Videos", channel=Adele)`
4. `PreviewUseCase`:
   - `mergeTemplates(rule.template, channel.metadataOverrides)` → `MusicVideo(artistOverride="Adele", defaultTags=["music"])`
   - `MetadataResolver.resolve(video, effectiveTemplate)` → `artist="Adele"`, `title="Hello"`
   - Path: `/media/Music/Adele/Hello [abc].m4a`

**Without the directory** (same URL):

- Channel not found in directory → `channel = null`
- `HasTag` does not match → rule does not fire
- Fallback to LLM or generic metadata

---

## 6. Modules and Files (Implementation Plan)

### domain/ (commonMain)

```
domain/src/commonMain/kotlin/.../domain/
├── common/
│   ├── ChannelDirectoryEntryId.kt  (new)
│   └── Tag.kt                      (new)
├── channel/                         (new package)
│   ├── Channel.kt
│   └── ChannelRepository.kt
├── rule/
│   ├── RuleMatch.kt                (add HasTag)
│   ├── MatchContext.kt             (add channel field)
│   ├── RuleMatchingService.kt      (inject ChannelRepository, return MatchResult)
│   └── MatchResult.kt              (new)
├── metadata/
│   └── MetadataTemplateMerger.kt   (new)
└── preview/
    └── PreviewUseCase.kt           (adapt to MatchResult)
```

### domain/domain-test-fixtures/ (commonMain)

```
├── channel/
│   └── channel.kt                   (Arb.channel())
└── common/
    └── tag.kt                       (Arb.tag())
```

### domain/ (commonTest)

```
domain/src/commonTest/kotlin/.../domain/
├── channel/
│   └── ChannelTest.kt               (new)
├── common/
│   └── TagTest.kt                   (new)
├── rule/
│   ├── RuleMatchTest.kt             (add HasTag tests)
│   └── RuleMatchingServiceTest.kt   (add channel lookup tests)
└── metadata/
    └── MetadataTemplateMergerTest.kt (new)
```

### api:contract/ (commonMain)

```
api/contract/src/commonMain/kotlin/.../api/contract/
├── channel/
│   ├── ChannelDto.kt                (new)
│   ├── CreateChannelDto.kt          (new)
│   └── UpdateChannelDto.kt          (new)
└── rule/
    └── RuleMatchDto.kt              (add HasTag)
```

### api:mapping/ (commonMain)

```
api/mapping/src/commonMain/kotlin/.../api/mapping/
├── channel/
│   ├── toDto.kt                     (new)
│   └── toDomain.kt                  (new)
└── rule/
    ├── toDto.kt                     (add HasTag case)
    └── toDomain.kt                  (add HasTag case)
```

### server:infra/

```
server/infra/src/main/kotlin/.../server/infra/
├── db/
│   ├── table/
│   │   └── ChannelsTable.kt         (new)
│   ├── model/
│   │   └── ChannelPm.kt             (new)
│   ├── mapping/
│   │   ├── channel.kt               (new)
│   │   └── ruleMatch.kt             (add HasTag case)
│   └── repository/
│       └── ChannelRepositoryImpl.kt  (new)
└── resources/db/migration/
    └── V2__channel_directory.sql     (new)
```

### server:transport/

```
server/transport/src/main/kotlin/.../server/transport/routes/
└── channelRoutes.kt                  (new — CRUD endpoints)
```

### server:di/

```
server/di/src/main/kotlin/.../server/di/
├── infraModule.kt                    (add ChannelRepositoryImpl binding)
└── domainModule.kt                   (RuleMatchingService: add ChannelRepository dep)
```

### features/ (UI)

```
features/src/commonMain/kotlin/.../features/
└── channel/                          (new — Compose screens for channel management)
    ├── screen/
    │   ├── ChannelListScreen.kt
    │   ├── ChannelDetailScreen.kt
    │   └── ChannelEditScreen.kt
    └── viewmodel/
        └── ChannelViewModel.kt
```

---

## 7. Implementation Order

1. **Domain**: `Tag`, `ChannelDirectoryEntryId`, `Channel`, `ChannelRepository`, tests
2. **Domain**: `RuleMatch.HasTag`, `MatchContext.channel`, update `matches()` and `matchSpecificity()`, tests
3. **Domain**: `MetadataTemplateMerger`, tests
4. **Domain**: `MatchResult`, update `RuleMatchingService`, tests
5. **Domain**: update `PreviewUseCase`
6. **DB**: migration `V2__channel_directory.sql`
7. **Infra**: `ChannelsTable`, `ChannelPm`, mappings, `ChannelRepositoryImpl`
8. **Infra**: update `RuleMatchPm` (HasTag)
9. **API Contract**: `ChannelDto`, `CreateChannelDto`, `UpdateChannelDto`, `RuleMatchDto.HasTag`
10. **API Mapping**: channel mappings, HasTag mapping
11. **Transport**: `channelRoutes.kt`
12. **DI**: wiring
13. **Features (UI)**: channel management screens
14. **Docs**: update `DOMAIN.md`, `DATABASE.md`, `API_CONTRACT.md`

---

## 8. Rejected Alternatives

### 8.1 RuleMatch.ChannelIdList

```kotlin
data class ChannelIdList(val channelIds: List<String>) : RuleMatch
```

**Rejected**: does not solve the per-channel metadata override problem. A list of IDs without context is not significantly better than `AnyOf(ChannelId(...), ChannelId(...))`.

### 8.2 Tags as a Separate Table (normalized)

```sql
CREATE TABLE tags (id SERIAL PRIMARY KEY, name TEXT UNIQUE);
CREATE TABLE channel_tags (channel_id UUID, tag_id INT, ...);
```

**Rejected**: JOIN overhead, unnecessary complexity. PostgreSQL `TEXT[]` + GIN is faster and simpler at our scale (hundreds of channels, dozens of tags). Can be migrated later if needed.

### 8.3 Tags as JSONB Instead of TEXT[]

```sql
tags JSONB NOT NULL DEFAULT '[]'
```

**Rejected**: `TEXT[]` + GIN is more idiomatic in PostgreSQL; the `@>` operator works directly. JSONB `@>` also works, but `TEXT[]` is semantically more precise for a list of strings.

### 8.4 Channel.metadataOverrides as UserOverrides

**Rejected**: `UserOverrides` contains only plain values (`artist`, `title`). `MetadataTemplate` also contains pattern fields (`artistPattern`, `titlePattern`), which are more useful for the directory — a channel may have a consistent title format for which a regex pattern is appropriate.

---

## 9. Open Questions

1. **Channel auto-discovery**: should the first download from a new channel automatically prompt the user to add it to the directory? (Can be implemented in UI later, not blocking MVP.)

2. **Bulk import**: is there a need to import channels from a file (CSV/JSON)? (Can be added as a separate endpoint later.)

3. **Channel URL**: should the channel URL be stored (e.g. `https://youtube.com/@adele`) for convenience? (Can be added as an optional field later.)
