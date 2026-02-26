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

### 2.1 Таблица `rules`

```sql
CREATE TABLE rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enabled              BOOLEAN NOT NULL DEFAULT true,
    priority             INTEGER NOT NULL DEFAULT 0,
    match                JSONB NOT NULL,
    category             TEXT NOT NULL,
    metadata_template    JSONB NOT NULL,          -- sealed: требует "type" discriminator
    download_policy      JSONB NOT NULL DEFAULT '{}',
    storage_policy       JSONB NOT NULL,          -- originalTemplate обязателен
    post_process_policy  JSONB NOT NULL DEFAULT '{}',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Индексы
CREATE INDEX idx_rules_enabled ON rules(enabled) WHERE enabled = true;
CREATE INDEX idx_rules_priority ON rules(priority DESC);
CREATE INDEX idx_rules_category ON rules(category);

-- GIN индекс для поиска по match (опционально)
CREATE INDEX idx_rules_match ON rules USING GIN (match);

-- Комментарии
COMMENT ON TABLE rules IS 'Правила обработки видео';
COMMENT ON COLUMN rules.match IS 'Критерии матчинга (RuleMatchDto JSON)';
COMMENT ON COLUMN rules.category IS 'Категория: music-video, series, other';
```

### 2.2 Таблица `jobs`

```sql
CREATE TABLE jobs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

### 2.3 Таблица `job_outputs` (опционально)

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

### 4.1 RulesTable

```kotlin
internal object CategoryTransformer : ColumnTransformer<String, Category> {
    override fun wrap(value: String): Category = Category.parse(value) // принимает kebab-case: "music-video"
    override fun unwrap(value: Category): String = value.serialized     // возвращает kebab-case
}

object RulesTable : UUIDTable("rules") {
    val enabled = bool("enabled").default(true)
    val priority = integer("priority").default(0)
    val match = jsonb<RuleMatchDto>("match", json)

    // Храним в БД как TEXT (kebab-case), работаем в коде как Category
    val category = varchar("category", 50).transform(CategoryTransformer)

    val metadataTemplate = jsonb<MetadataTemplateDto>("metadata_template", json)
    val downloadPolicy = jsonb<DownloadPolicyDto>("download_policy", json)
    val storagePolicy = jsonb<StoragePolicyDto>("storage_policy", json)
    val postProcessPolicy = jsonb<PostProcessPolicyDto>("post_process_policy", json)

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}
```

### 4.2 JobsTable

```kotlin
internal object JobStatusTransformer : ColumnTransformer<String, JobStatus> {
    override fun wrap(value: String): JobStatus = JobStatus.parse(value) // "queued", "post-processing", ...
    override fun unwrap(value: JobStatus): String = value.serialized
}

internal object VideoIdTransformer : ColumnTransformer<String, VideoId> {
    override fun wrap(value: String): VideoId = VideoId(value)
    override fun unwrap(value: VideoId): String = value.value
}

internal object UrlTransformer : ColumnTransformer<String, Url> {
    override fun wrap(value: String): Url = Url(value)
    override fun unwrap(value: Url): String = value.value
}

internal object ExtractorTransformer : ColumnTransformer<String, Extractor> {
    override fun wrap(value: String): Extractor = Extractor(value)
    override fun unwrap(value: Extractor): String = value.value
}

internal object TelegramUserIdTransformer : ColumnTransformer<Long, TelegramUserId> {
    override fun wrap(value: Long): TelegramUserId = TelegramUserId(value)
    override fun unwrap(value: TelegramUserId): Long = value.value
}

object JobsTable : UUIDTable("jobs") {
    // status / category в БД — TEXT (kebab-case), в коде — enum
    val status = varchar("status", 20).default("queued").transform(JobStatusTransformer)

    // video_id / urls / extractor — доменные value classes
    val videoId = varchar("video_id", 50).transform(VideoIdTransformer)
    val sourceUrl = text("source_url").transform(UrlTransformer)
    val sourceExtractor = varchar("source_extractor", 50).transform(ExtractorTransformer)

    val ruleId = reference("rule_id", RulesTable).nullable()

    val category = varchar("category", 50).transform(CategoryTransformer)

    // Для крупных структур (yt-dlp info, metadata, storage plan) — JSONB
    val rawInfo = jsonb<VideoInfoDto>("raw_info", json)
    val metadata = jsonb<ResolvedMetadataDto>("metadata", json)
    val storagePlan = jsonb<StoragePlanDto>("storage_plan", json)

    val progress = jsonb<JobProgressDto>("progress", json).nullable()
    val error = jsonb<JobErrorDto>("error", json).nullable()

    val attempt = integer("attempt").default(0)

    val createdByTelegramUserId = long("created_by_telegram_user_id").transform(TelegramUserIdTransformer)

    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val startedAt = timestamp("started_at").nullable()
    val finishedAt = timestamp("finished_at").nullable()
}
```

### 4.3 JobOutputsTable (опционально)

Если используется нормализованная таблица `job_outputs`, также имеет смысл типизировать `format`:

```kotlin
internal object OutputFormatTransformer : ColumnTransformer<String, OutputFormat> {
    override fun wrap(value: String): OutputFormat = OutputFormat.parse(value) // "video/mp4", "audio/m4a"...
    override fun unwrap(value: OutputFormat): String = value.serialized
}

object JobOutputsTable : UUIDTable("job_outputs") {
    val jobId = reference("job_id", JobsTable)
    val format = varchar("format", 32).transform(OutputFormatTransformer)
    val path = text("path")
    val size = long("size").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
```

### 4.4 JSONB Extension

```kotlin
// Используем встроенный jsonb() из exposed-json
// import org.jetbrains.exposed.v1.json.jsonb
// import kotlinx.serialization.serializer

// Вариант с Json + KSerializer
val match = jsonb<RuleMatchDto>("match", json)                 // KSerializer<T> будет взят автоматически
val storagePlan = jsonb<StoragePlanDto>("storage_plan", json)

// Вариант с кастомными serialize/deserialize (если нужно):
val metadata = jsonb("metadata",
    serialize = { json.encodeToString(ResolvedMetadataDto.serializer(), it) },
    deserialize = { json.decodeFromString(ResolvedMetadataDto.serializer(), it) }
)
```

> Требуется зависимость `org.jetbrains.exposed:exposed-json`.

---

## 5. Миграции

### 5.1 Структура

```
server/infra/src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__add_job_outputs.sql
└── V3__add_rules_name.sql
```

### 5.2 V1__initial_schema.sql

```sql
-- Rules table
CREATE TABLE rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

CREATE INDEX idx_rules_enabled ON rules(enabled) WHERE enabled = true;
CREATE INDEX idx_rules_priority ON rules(priority DESC);

-- Jobs table
CREATE TABLE jobs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_video_id ON jobs(video_id);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX idx_jobs_queued ON jobs(created_at) WHERE status = 'queued';

CREATE UNIQUE INDEX idx_jobs_active_video 
    ON jobs(video_id) 
    WHERE status IN ('queued', 'running', 'post-processing');
```

### 5.3 Flyway конфигурация

```kotlin
// В Application.kt
fun Application.configureFlyway() {
    val config = environment.config
    val flyway = Flyway.configure()
        .dataSource(
            config.property("db.url").getString(),
            config.property("db.user").getString(),
            config.property("db.password").getString()
        )
        .locations("classpath:db/migration")
        .baselineOnMigrate(true)
        .load()
    
    flyway.migrate()
}
```

---

## 6. Репозитории

### 6.1 RuleRepositoryImpl

```kotlin
class RuleRepositoryImpl(
    private val database: Database,
) : RuleRepository {
    
    override suspend fun findById(id: RuleId): Rule? = dbQuery {
        RulesTable.selectAll()
            .where { RulesTable.id eq id.value }
            .singleOrNull()
            ?.toRule()
    }
    
    override suspend fun findAll(enabled: Boolean?): List<Rule> = dbQuery {
        RulesTable.selectAll()
            .apply { 
                if (enabled != null) {
                    andWhere { RulesTable.enabled eq enabled }
                }
            }
            .orderBy(RulesTable.priority, SortOrder.DESC)
            .map { it.toRule() }
    }
    
    override suspend fun save(rule: Rule): Rule = dbQuery {
        val exists = RulesTable.selectAll()
            .where { RulesTable.id eq rule.id.value }
            .count() > 0
        
        if (exists) {
            RulesTable.update({ RulesTable.id eq rule.id.value }) {
                it[enabled] = rule.enabled
                it[priority] = rule.priority
                it[match] = rule.match.toDto()
                it[category] = rule.category
                it[metadataTemplate] = rule.metadataTemplate.toDto()
                it[downloadPolicy] = rule.downloadPolicy.toDto()
                it[storagePolicy] = rule.storagePolicy.toDto()
                it[postProcessPolicy] = rule.postProcessPolicy.toDto()
                it[updatedAt] = Instant.now()
            }
        } else {
            RulesTable.insert {
                it[id] = rule.id.value
                it[enabled] = rule.enabled
                it[priority] = rule.priority
                it[match] = rule.match.toDto()
                it[category] = rule.category
                it[metadataTemplate] = rule.metadataTemplate.toDto()
                it[downloadPolicy] = rule.downloadPolicy.toDto()
                it[storagePolicy] = rule.storagePolicy.toDto()
                it[postProcessPolicy] = rule.postProcessPolicy.toDto()
                it[createdAt] = rule.createdAt
                it[updatedAt] = rule.updatedAt
            }
        }
        rule
    }
    
    override suspend fun delete(id: RuleId) = dbQuery {
        RulesTable.deleteWhere { RulesTable.id eq id.value }
        Unit
    }
    
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
    
    private fun ResultRow.toRule(): Rule = Rule(
        id = RuleId(this[RulesTable.id].value),
        enabled = this[RulesTable.enabled],
        priority = this[RulesTable.priority],
        match = this[RulesTable.match].toDomain().getOrElse { 
            throw IllegalStateException("Invalid match in DB") 
        },
        category = this[RulesTable.category], // уже Category благодаря transform
        metadataTemplate = this[RulesTable.metadataTemplate].toDomain(),
        downloadPolicy = this[RulesTable.downloadPolicy].toDomain(),
        storagePolicy = this[RulesTable.storagePolicy].toDomain(),
        postProcessPolicy = this[RulesTable.postProcessPolicy].toDomain(),
        createdAt = this[RulesTable.createdAt],
        updatedAt = this[RulesTable.updatedAt],
    )
}
```

---

## 7. Транзакции

### 7.1 Подход

```kotlin
suspend fun <T> transactional(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO, database) {
        block()
    }
```

### 7.2 Пример использования

```kotlin
class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val transactionManager: TransactionManager,
) {
    suspend fun execute(request: CreateJobRequest): Either<DomainError, Job> = either {
        transactionManager.transactional {
            // Проверки и создание в одной транзакции
            val existing = jobRepository.findActiveByVideoId(request.source.videoId)
            if (existing != null) {
                raise(DomainError.JobAlreadyExists(...))
            }
            jobRepository.save(newJob)
        }
    }
}
```

---

## 8. Производительность

### 8.1 Пул соединений

HikariCP (уже включён в Exposed):

```kotlin
val database = Database.connect(
    HikariDataSource(HikariConfig().apply {
        jdbcUrl = config.db.url
        username = config.db.user
        password = config.db.password
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 60000
        connectionTimeout = 30000
    })
)
```

### 8.2 Рекомендации

- Использовать `SELECT ... FOR UPDATE` для блокировки job при взятии в работу
- Использовать partial indexes для фильтрации по status
- JSONB индексы только если реально нужен поиск внутри JSON

