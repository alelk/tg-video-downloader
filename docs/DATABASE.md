# База данных

> **Цель документа**: Описание схемы PostgreSQL, миграций, индексов.

---

## 1. Общие требования

- **СУБД**: PostgreSQL 16+
- **Миграции**: Flyway
- **ORM**: Exposed
- **Время**: UTC (timestamptz)
- **UUID**: Нативный тип PostgreSQL

---

## 2. Схема

### 2.1 Таблица `workspaces`

```sql
CREATE TABLE workspaces (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 2.2 Таблица `workspace_members`

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

### 2.3 Таблица `rules`

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
    storage_policy       JSONB NOT NULL,
    post_process_policy  JSONB NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_rules_workspace ON rules(workspace_id);
CREATE INDEX idx_rules_enabled ON rules(enabled) WHERE enabled = true;
CREATE INDEX idx_rules_priority ON rules(priority DESC);
CREATE INDEX idx_rules_category ON rules(category);
CREATE INDEX idx_rules_match ON rules USING GIN (match);
```

### 2.4 Таблица `jobs`

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

-- Индексы
CREATE INDEX idx_jobs_workspace ON jobs(workspace_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_video_id ON jobs(video_id);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_jobs_queued ON jobs(created_at) WHERE status = 'queued';
CREATE INDEX idx_jobs_user ON jobs(created_by_telegram_user_id);

-- Partial unique для предотвращения дублей активных jobs
CREATE UNIQUE INDEX idx_jobs_active_video 
    ON jobs(video_id) 
    WHERE status IN ('queued', 'running', 'post-processing');

-- Комментарии
COMMENT ON TABLE jobs IS 'Задачи скачивания';
COMMENT ON COLUMN jobs.status IS 'queued, running, post-processing, done, failed, cancelled';
COMMENT ON COLUMN jobs.metadata IS 'ResolvedMetadataDto JSON с type';
COMMENT ON COLUMN jobs.storage_plan IS 'StoragePlanDto JSON';
COMMENT ON COLUMN jobs.created_by_telegram_user_id IS 'Telegram user id (BIGINT)';
```

### 2.5 Таблица `job_outputs` (опционально)

Для нормализованного хранения результатов:

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

COMMENT ON TABLE job_outputs IS 'Выходные файлы job';
COMMENT ON COLUMN job_outputs.format IS 'OutputFormat: original/ext, video/ext, audio/ext, image/ext';
```

---

## 3. JSONB структуры

### 3.1 rules.match

```json
{
  "type": "channel-id",
  "value": "UCq-Fj5jknLsUf-MWSy4_brA"
}
```

или

```json
{
  "type": "all-of",
  "matches": [
    { "type": "channel-name", "value": "Rick Astley" },
    { "type": "title-regex", "pattern": "Official" }
  ]
}
```

### 3.2 rules.metadata_template

> Sealed тип (полиморфный): discriminator `"type"` определяет подтип.
> Поля, специфичные для подтипа, присутствуют только в соответствующем JSON.

**MusicVideo** (с override исполнителя):
```json
{
  "type": "music-video",
  "artistOverride": "Casting Crowns",
  "defaultTags": ["worship", "ccm"]
}
```

**MusicVideo** (с regex-паттерном для парсинга):
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

**Other** (минимальный):
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

### 3.4 rules.storage_policy

```json
{
  "originalTemplate": "/media/Music Videos/original/{artist}/{title} [{videoId}].{ext}",
  "additionalOutputs": [
    {
      "pathTemplate": "/media/Music Videos/converted/{artist}/{title}.mp4",
      "format": "video/mp4"
    }
  ]
}
```

### 3.5 rules.post_process_policy

```json
{
  "embedThumbnail": true,
  "embedMetadata": true,
  "normalizeAudio": false
}
```

### 3.6 jobs.metadata

> Sealed тип (полиморфный): discriminator `"type"` определяет подтип (`music-video`, `series-episode`, `other`).

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

### 3.7 jobs.storage_plan

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

### 3.8 jobs.progress

```json
{
  "phase": "download",
  "percent": 45,
  "message": "Downloading video..."
}
```

### 3.9 jobs.error

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

Определения Exposed-таблиц расположены в `server/infra/src/main/kotlin/.../db/table/`:

| Файл                       | Описание                                   |
|----------------------------|--------------------------------------------|
| `WorkspacesTable.kt`       | `UuidTable("workspaces")`                  |
| `WorkspaceMembersTable.kt` | `Table("workspace_members")`, composite PK |
| `RulesTable.kt`            | `UuidTable("rules")`, JSONB-колонки        |
| `JobsTable.kt`             | `UuidTable("jobs")`, JSONB-колонки         |
| `JobOutputsTable.kt`       | `UuidTable("job_outputs")`                 |

**Подход к маппингу DB ↔ Domain:**
- Колонки хранят примитивные типы (`String`, `Long`, `Boolean`)
- Маппинг `String` ↔ `enum` / value class — в функциях-расширениях в пакете `db/mapping/`
- JSONB-колонки хранят persistence-модели (`*Pm`) через `jsonb<T>(name, json)` из `exposed-json`
- Persistence-модели (`db/model/`) — отдельные `@Serializable` классы, **не зависят от `api:contract`**
- Маппинг domain ↔ Pm — в `db/mapping/` (например `toPm()` / `toDomain()`)
- `timestamp()` из `exposed-kotlin-datetime` возвращает `kotlin.time.Instant`

> **Важно**: `server:infra` зависит только от `domain`. DTO из `api:contract` не используются в слое хранения — это обеспечивает независимую эволюцию API и DB schema.

---

## 5. Миграции

### 5.1 Структура

```
server/infra/src/main/resources/db/migration/
└── V1__initial_schema.sql
```

### 5.2 V1__initial_schema.sql

> Актуальная версия: `server/infra/src/main/resources/db/migration/V1__initial_schema.sql`.
> Создаёт таблицы: `workspaces`, `workspace_members`, `rules`, `jobs`, `job_outputs` с индексами.

### 5.3 Flyway конфигурация

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

## 6. Репозитории

Реализации расположены в `server/infra/src/main/kotlin/.../db/repository/`:

- **`WorkspaceRepositoryImpl`** — CRUD для workspace и workspace_members
- **`RuleRepositoryImpl`** — CRUD для правил с фильтрацией по workspace
- **`JobRepositoryImpl`** — CRUD для задач с фильтрацией по workspace, обновление статусов

Общая утилита `dbQuery(database) { ... }` (файл `db/dbQuery.kt`) оборачивает блок в `suspendTransaction` с `Dispatchers.IO`.

---

## 7. Транзакции

### 7.1 Подход

```kotlin
// db/dbQuery.kt
suspend fun <T> dbQuery(database: Database, block: suspend () -> T): T =
    withContext(Dispatchers.IO) {
        suspendTransaction(db = database) { block() }
    }
```

> `newSuspendedTransaction()` deprecated в Exposed 1.0.0. Используется `suspendTransaction()`.

---

## 8. Производительность

### 8.1 Пул соединений

HikariCP (настраивается в `DatabaseFactory`):

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

### 8.2 Рекомендации

- Использовать `SELECT ... FOR UPDATE` для блокировки job при взятии в работу
- Использовать partial indexes для фильтрации по status
- JSONB индексы только если реально нужен поиск внутри JSON

