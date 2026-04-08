# Database

> **Purpose**: PostgreSQL schema, migrations, and indexes.

---

## 1. General Requirements

- **DBMS**: PostgreSQL 16+
- **Migrations**: Flyway
- **ORM**: Exposed
- **Timestamps**: UTC (timestamptz)
- **UUID**: Native PostgreSQL type

---

## 2. Schema

### 2.1 Table `workspaces`

```sql
CREATE TABLE workspaces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 2.2 Table `workspace_members`

```sql
CREATE TABLE workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL,
    role         VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);
```

### 2.3 Table `rules`

```sql
CREATE TABLE rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         UUID NOT NULL REFERENCES workspaces(id),
    name                 TEXT NOT NULL DEFAULT '',
    enabled              BOOLEAN NOT NULL DEFAULT true,
    priority             INTEGER NOT NULL DEFAULT 0,
    match                JSONB NOT NULL,
    category             TEXT NOT NULL,
    metadata_template    JSONB NOT NULL,
    download_policy      JSONB NOT NULL DEFAULT '{}',
    outputs              JSONB NOT NULL DEFAULT '[]',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rules_workspace ON rules(workspace_id);
CREATE INDEX idx_rules_enabled ON rules(enabled) WHERE enabled = true;
CREATE INDEX idx_rules_priority ON rules(priority DESC);
CREATE INDEX idx_rules_category ON rules(category);
CREATE INDEX idx_rules_match ON rules USING GIN (match);
```

### 2.4 Table `channels`

Channel directory — channels with tags and metadata overrides.
See also: [ADR/008-channel-directory.md](./ADR/008-channel-directory.md)

```sql
CREATE TABLE channels (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id       UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    channel_id         TEXT NOT NULL,
    extractor          TEXT NOT NULL,
    name               TEXT NOT NULL,
    tags               TEXT[] NOT NULL DEFAULT '{}',
    metadata_overrides JSONB,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, channel_id, extractor)
);

CREATE INDEX idx_channels_workspace ON channels(workspace_id);
CREATE INDEX idx_channels_tags ON channels USING GIN (tags);
CREATE INDEX idx_channels_extractor ON channels(extractor);
CREATE INDEX idx_channels_channel_id ON channels(channel_id);

COMMENT ON TABLE channels IS 'Channel directory — channels with tags and metadata overrides';
COMMENT ON COLUMN channels.channel_id IS 'Platform channel ID (YouTube channel ID, etc.)';
COMMENT ON COLUMN channels.extractor IS 'Platform: youtube, rutube, vk, etc.';
COMMENT ON COLUMN channels.tags IS 'Tags for grouping channels (PostgreSQL text array)';
COMMENT ON COLUMN channels.metadata_overrides IS 'MetadataTemplatePm JSON — metadata overrides';
```

> `tags` are stored as a PostgreSQL `TEXT[]` with a GIN index for fast tag-based lookups.
> The query `tags @> ARRAY['music-video']` uses the GIN index.
> `metadata_overrides` — JSONB in the same format as `rules.metadata_template`.

### 2.5 Table `jobs`

```sql
CREATE TABLE jobs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id               UUID NOT NULL REFERENCES workspaces(id),
    status                     TEXT NOT NULL DEFAULT 'queued',
    video_id                   TEXT NOT NULL,
    source_url                 TEXT NOT NULL,
    source_extractor           TEXT NOT NULL,  -- "youtube", "rutube", "vk", ...
    rule_id                    UUID REFERENCES rules(id) ON DELETE SET NULL,
    category                   TEXT NOT NULL,
    raw_info                   JSONB NOT NULL,
    metadata                   JSONB NOT NULL,
    storage_plan               JSONB NOT NULL,
    progress                   JSONB,
    error                      JSONB,
    attempt                    INTEGER NOT NULL DEFAULT 0,
    created_by_telegram_user_id BIGINT NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at                 TIMESTAMPTZ,
    finished_at                TIMESTAMPTZ
);

-- Indexes
CREATE INDEX idx_jobs_workspace ON jobs(workspace_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_video_id ON jobs(video_id);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_jobs_queued ON jobs(created_at) WHERE status = 'queued';
CREATE INDEX idx_jobs_user ON jobs(created_by_telegram_user_id);

-- Partial unique index to prevent duplicate active jobs
CREATE UNIQUE INDEX idx_jobs_active_video 
    ON jobs(video_id) 
    WHERE status IN ('queued', 'running', 'post-processing');

-- Comments
COMMENT ON TABLE jobs IS 'Download jobs';
COMMENT ON COLUMN jobs.status IS 'queued, running, post-processing, done, failed, cancelled';
COMMENT ON COLUMN jobs.metadata IS 'ResolvedMetadataDto JSON with type discriminator';
COMMENT ON COLUMN jobs.storage_plan IS 'StoragePlanDto JSON';
COMMENT ON COLUMN jobs.created_by_telegram_user_id IS 'Telegram user id (BIGINT)';
```

### 2.6 Table `job_outputs` (optional)

Normalized storage for job results:

```sql
CREATE TABLE job_outputs (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id   UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    format   TEXT NOT NULL,    -- "original/webm", "video/mp4", "audio/m4a", "image/jpg"
    path     TEXT NOT NULL,
    size     BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_outputs_job_id ON job_outputs(job_id);

COMMENT ON TABLE job_outputs IS 'Output files produced by a job';
COMMENT ON COLUMN job_outputs.format IS 'OutputFormat: original/ext, video/ext, audio/ext, image/ext';
```

### 2.7 Table `video_info_cache`

Cache for VideoInfo from yt-dlp to avoid redundant calls during interactive preview.

```sql
CREATE TABLE video_info_cache (
    url         TEXT PRIMARY KEY,
    video_info  JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE video_info_cache IS 'VideoInfo cache from yt-dlp to avoid redundant calls';
COMMENT ON COLUMN video_info_cache.video_info IS 'VideoInfoPm JSON';
```

> PK on `url` — simple text key. `video_info` stores `VideoInfoPm` (same model as `jobs.raw_info`).
> No TTL — records are stored indefinitely.

---

## 3. JSONB Structures

### 3.1 rules.match

```json
{
  "type": "channel-id",
  "value": "UCq-Fj5jknLsUf-MWSy4_brA"
}
```

or

```json
{
  "type": "all-of",
  "matches": [
    { "type": "channel-name", "value": "Rick Astley" },
    { "type": "title-regex", "pattern": "Official" }
  ]
}
```

or (match on user-overridden category):

```json
{
  "type": "category-equals",
  "category": "music-video"
}
```

or (match on tag from the channel directory):

```json
{
  "type": "has-tag",
  "tag": "music-video"
}
```

### 3.2 rules.metadata_template

> Sealed type (polymorphic): the `"type"` discriminator determines the subtype.
> Subtype-specific fields are only present in the corresponding JSON.

**MusicVideo** (with artist override):
```json
{
  "type": "music-video",
  "artistOverride": "Casting Crowns",
  "defaultTags": ["worship", "ccm"]
}
```

**MusicVideo** (with regex pattern for parsing):
```json
{
  "type": "music-video",
  "artistPattern": "^(.+?)\\s*[-–—]",
  "titlePattern": "[-–—]\\s*(.+)$"
}
```

**SeriesEpisode**:
```json
{
  "type": "series-episode",
  "seriesNameOverride": "Tech News Weekly",
  "seasonPattern": "S(\\d+)",
  "episodePattern": "E(\\d+)"
}
```

**Other** (minimal):
```json
{
  "type": "other"
}
```

### 3.3 rules.download_policy

```json
{
  "maxQuality": "best",
  "preferredContainer": "mp4",
  "downloadSubtitles": false,
  "subtitleLanguages": []
}
```

### 3.4 rules.outputs

```json
[
  {
    "pathTemplate": "/media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
    "format": "original/webm",
    "maxQuality": null,
    "encodeSettings": null,
    "embedThumbnail": false,
    "embedMetadata": false,
    "embedSubtitles": false,
    "normalizeAudio": false
  },
  {
    "pathTemplate": "/media/Music Videos/converted/{artist}/{title}.mp4",
    "format": "video/mp4",
    "maxQuality": "hd_1080",
    "encodeSettings": { "codec": "h264", "crf": 23, "preset": "medium", "audioBitrate": "192k" },
    "embedThumbnail": true,
    "embedMetadata": true,
    "embedSubtitles": false,
    "normalizeAudio": false
  }
]
```

### 3.5 jobs.metadata

> Sealed type (polymorphic): the `"type"` discriminator determines the subtype (`music-video`, `series-episode`, `other`).

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

### 3.6 jobs.storage_plan

```json
{
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
}
```

### 3.7 jobs.progress

```json
{
  "phase": "download",
  "percent": 45,
  "message": "Downloading video..."
}
```

### 3.8 jobs.error

```json
{
  "code": "DOWNLOAD_FAILED",
  "message": "Network error",
  "details": "Connection timeout after 30s",
  "retryable": true
}
```

---

## 4. Exposed Tables

Exposed table definitions are located in `server/infra/src/main/kotlin/.../db/table/`:

| File                       | Description                                     |
|----------------------------|-------------------------------------------------|
| `WorkspacesTable.kt`       | `UuidTable("workspaces")`                       |
| `WorkspaceMembersTable.kt` | `Table("workspace_members")`, composite PK      |
| `RulesTable.kt`            | `UuidTable("rules")`, JSONB columns             |
| `JobsTable.kt`             | `UuidTable("jobs")`, JSONB columns              |
| `JobOutputsTable.kt`       | `UuidTable("job_outputs")`                      |
| `VideoInfoCacheTable.kt`   | `Table("video_info_cache")`, text PK            |

**DB ↔ Domain mapping approach:**
- Columns store primitive types (`String`, `Long`, `Boolean`)
- `String` ↔ `enum` / value class mapping — in extension functions in `db/mapping/`
- JSONB columns store persistence models (`*Pm`) via `jsonb<T>(name, json)` from `exposed-json`
- Persistence models (`db/model/`) — separate `@Serializable` classes, **do not depend on `api:contract`**
- Domain ↔ Pm mapping — in `db/mapping/` (e.g., `toPm()` / `toDomain()`)
- `timestamp()` from `exposed-kotlin-datetime` returns `kotlin.time.Instant`

> **Important**: `server:infra` depends only on `domain`. DTOs from `api:contract` are not used in the storage layer — this ensures independent evolution of the API and DB schema.

---

## 5. Migrations

### 5.1 Structure

```
server/infra/src/main/resources/db/migration/
└── V1__initial_schema.sql
```

### 5.2 V1__initial_schema.sql

> Current version: `server/infra/src/main/resources/db/migration/V1__initial_schema.sql`.
> Creates tables: `workspaces`, `workspace_members`, `rules`, `jobs`, `job_outputs`, `video_info_cache` with indexes.

### 5.3 Flyway Configuration

```kotlin
// DatabaseFactory.kt
class DatabaseFactory(private val config: DbConfig) {
    fun create(): Database {
        val dataSource = HikariDataSource(HikariConfig().apply { ... })
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()
            .migrate()
        return Database.connect(dataSource)
    }
}
```

---

## 6. Repositories

Implementations are located in `server/infra/src/main/kotlin/.../db/repository/`:

- **`WorkspaceRepositoryImpl`** — CRUD for workspaces and workspace_members
- **`RuleRepositoryImpl`** — CRUD for rules, filtered by workspace
- **`JobRepositoryImpl`** — CRUD for jobs, filtered by workspace, status updates
- **`VideoInfoCacheImpl`** — VideoInfo cache from yt-dlp (port `VideoInfoCache`)

The utility function `dbQuery(database) { ... }` (in `db/dbQuery.kt`) wraps a block in `suspendTransaction` with `Dispatchers.IO`.

---

## 7. Transactions

### 7.1 Approach

```kotlin
// db/dbQuery.kt
suspend fun <T> dbQuery(database: Database, block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction(db = database) { block() }
    }
```

> `newSuspendedTransaction()` is deprecated in Exposed 1.0.0. Use `suspendTransaction()` instead.

---

## 8. Performance

### 8.1 Connection Pool

HikariCP (configured in `DatabaseFactory`):

```kotlin
val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = config.url
    username = config.user
    password = config.password
    maximumPoolSize = config.poolSize
    minimumIdle = config.minIdle
    idleTimeout = 60000
    connectionTimeout = 30000
    driverClassName = "org.postgresql.Driver"
})
```

### 8.2 Recommendations

- Use `SELECT ... FOR UPDATE` to lock a job when picking it up for processing
- Use partial indexes to filter by status
- Add JSONB indexes only when actual JSON field searches are required
