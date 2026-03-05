# ADR-008: Справочник каналов (Channel Directory)

**Статус**: Принято, реализовано  
**Дата**: 2026-03-05  
**Авторы**: Alex Elkin

---

## Контекст

### Проблема

Правила (`Rule`) позволяют тонко настраивать скачивание видео: выбор формата, конвертация, метаданные, пути сохранения. Каждое правило — сложная конфигурация.

Типичный сценарий: пользователь хочет **одно правило** для 30+ музыкальных YouTube-каналов. Сейчас для этого нужно:

```kotlin
RuleMatch.AnyOf(listOf(
    RuleMatch.ChannelId("UC1..."),
    RuleMatch.ChannelId("UC2..."),
    // ... ещё 28 каналов
))
```

**Проблемы этого подхода:**

1. **Громоздкий match** — десятки каналов в одном JSON. Неудобно читать, редактировать, отлаживать.
2. **Нет переопределения метаданных per-channel** — если название артиста отличается от канала (например, канал "VEVO" → артист "Adele"), нужно отдельное правило для каждого канала.
3. **Нет удобного UI** — нельзя просто "добавить канал в список", нужно редактировать JSON-дерево match.
4. **Нет переиспользования** — один канал нельзя использовать в нескольких правилах без дублирования.

### Желаемый UX

Пользователь:
1. Создаёт **справочник каналов** — коллекцию каналов с тегами и метаданными
2. Присваивает каналам **теги** (`music-video`, `lofi`, `tech-review`)
3. Для каждого канала опционально задаёт **переопределения метаданных** (артист, серия, etc.)
4. В правиле указывает match: `HasTag("music-video")` — и правило автоматически применяется ко всем каналам с этим тегом
5. При матче — переопределения из канала в справочнике автоматически используются как начальные overrides

---

## Решение

### Обзор

Вводим новую сущность **`Channel`** — запись в справочнике каналов workspace.

```
Channel Directory (справочник)
    │
    ├── Channel { channelId="UC_adele", name="Adele", tags=["music-video"], overrides=MusicVideo(artist="Adele") }
    ├── Channel { channelId="UC_lofi", name="Lofi Girl", tags=["music-video", "lofi"], overrides=MusicVideo(artist="Lofi Girl") }
    └── Channel { channelId="UC_tech", name="MKBHD", tags=["tech-review"], overrides=null }

Rule { match = HasTag("music-video"), ... }
    → матчит: Adele, Lofi Girl
    → НЕ матчит: MKBHD

Rule { match = AllOf(HasTag("music-video"), HasTag("lofi")), ... }
    → матчит: Lofi Girl
    → НЕ матчит: Adele, MKBHD
```

При матче правила через `HasTag`:
1. Ищем канал из видео в справочнике (по `channelId` + `extractor`)
2. Проверяем, есть ли у канала нужный тег
3. Если матч — используем `channel.metadataOverrides` как дополнительный слой в pipeline метаданных

### Архитектурные принципы

- **Channel** — сущность домена, living в `domain/channel/`
- **Справочник привязан к workspace** (как и правила)
- **Теги — plain strings** (не отдельная сущность). Гибко, не требует миграций при добавлении нового тега
- **Переопределения метаданных** переиспользуют существующий sealed `MetadataTemplate` — те же поля `artistOverride`, `seriesNameOverride` и т.д.
- **Матчинг обогащается**, а не заменяется — `HasTag` — новый лист в `RuleMatch` sealed hierarchy

---

## 1. Domain Model

### 1.1 Новые типы в `common/`

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

> **Tag** — value class с валидацией. Lowercase, alphanumeric + hyphens. Примеры: `music-video`, `lofi`, `tech`, `series`.
> Это не enum — пользователь создаёт теги свободно. Но формат нормализован для надёжного поиска.

### 1.2 Channel (сущность справочника)

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

**Ключевые решения:**

| Поле                      | Тип                       | Зачем                                                                                                                                                |
|---------------------------|---------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `channelId` + `extractor` | `ChannelId` + `Extractor` | Уникальный идентификатор канала на платформе. YouTube channelId ≠ RuTube channelId                                                                   |
| `name`                    | `String`                  | Человекочитаемое название (может отличаться от `channelName` в yt-dlp)                                                                               |
| `tags`                    | `Set<Tag>`                | Теги для группировки. Unordered, unique                                                                                                              |
| `metadataOverrides`       | `MetadataTemplate?`       | Переопределения метаданных. Sealed — тип определяет категорию (MusicVideo, SeriesEpisode, Other). Переиспользуем уже существующий `MetadataTemplate` |
| `notes`                   | `String?`                 | Произвольные заметки пользователя                                                                                                                    |

> **Почему `MetadataTemplate` для overrides, а не `UserOverrides`?**
> `MetadataTemplate` содержит и override-поля (`artistOverride`), и pattern-поля (`artistPattern`). Для справочника каналов patterns тоже полезны — если канал загружает видео в формате "Artist - Title", pattern пригодится. `UserOverrides` — слишком упрощённый (только значения, без patterns).

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

### 1.4 RuleMatch.HasTag (новый лист)

```kotlin
// domain/rule/RuleMatch.kt (дополнение)
sealed interface RuleMatch {
    // ...existing variants...

    /**
     * Матчит если канал видео зарегистрирован в справочнике и имеет указанный тег.
     * Матчинг: channelId + extractor из VideoInfo → поиск в ChannelRepository → проверка tag.
     */
    data class HasTag(val tag: Tag) : RuleMatch
}
```

**Специфичность**: 70 — между `ChannelName` (80) и `UrlRegex` (60).

> Обоснование: `HasTag` — менее точный, чем конкретный канал (`ChannelId`/`ChannelName`), 
> но более целенаправленный, чем регулярные выражения на URL/title. Тег подразумевает сознательную группировку каналов.

### 1.5 MatchContext (обогащение)

Для матчинга `HasTag` нужен доступ к справочнику каналов. Добавляем в контекст:

```kotlin
// domain/rule/MatchContext.kt
data class MatchContext(
    val video: VideoInfo,
    val overrides: UserOverrides? = null,
    val channel: Channel? = null,  // NEW: канал из справочника (если найден)
)
```

> **`channel` загружается один раз** при формировании `MatchContext`, а не при каждом вызове `matches()`.
> `RuleMatchingService` ищет канал по `video.channelId` + `video.extractor` в `ChannelRepository`, затем передаёт в контекст.

**Матчинг `HasTag`:**

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

### 1.6 RuleMatchingService (изменение)

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
 * Результат матчинга — правило + опционально найденный канал из справочника.
 * Канал нужен для применения channel-level overrides к метаданным.
 */
data class MatchResult(
    val rule: Rule,
    val channel: Channel?,
)
```

> **Изменение сигнатуры**: `findMatchingRule` теперь возвращает `MatchResult?` вместо `Rule?`.
> Это ломающее изменение — но мы не заботимся об обратной совместимости (по условию задачи).

### 1.7 Metadata Resolution Pipeline (обогащение)

Текущий pipeline метаданных:

```
VideoInfo → MetadataResolver(template from Rule) → ResolvedMetadata → applyOverrides(UserOverrides) → final
```

Новый pipeline с Channel:

```
VideoInfo → MetadataResolver(effectiveTemplate) → ResolvedMetadata → applyOverrides(UserOverrides) → final
                                    ↑
                        mergeTemplates(rule.metadataTemplate, channel.metadataOverrides)
```

Если канал найден и имеет `metadataOverrides` — они **мержатся** с template правила:

```kotlin
// domain/metadata/MetadataTemplateMerger.kt

/**
 * Мержит два MetadataTemplate.
 * Поля из [overlay] имеют приоритет над [base].
 * Оба должны быть одного типа (category). Если типы разные — overlay побеждает.
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

**Приоритет слоёв (от низшего к высшему):**

```
1. Rule.metadataTemplate          ← базовые настройки правила
2. Channel.metadataOverrides      ← per-channel переопределения (из справочника)
3. UserOverrides                  ← ручные правки пользователя в UI (высший приоритет)
```

### 1.8 PreviewUseCase (изменение)

```kotlin
// domain/preview/PreviewUseCase.kt (обновлённый фрагмент)

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

### 2.1 Таблица `channels`

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

    -- Один канал (channel_id + extractor) — одна запись в workspace
    UNIQUE (workspace_id, channel_id, extractor)
);

-- Индексы
CREATE INDEX idx_channels_workspace ON channels(workspace_id);
CREATE INDEX idx_channels_tags ON channels USING GIN (tags);
CREATE INDEX idx_channels_extractor ON channels(extractor);
CREATE INDEX idx_channels_channel_id ON channels(channel_id);
```

> **Теги как `TEXT[]`** (PostgreSQL array) — выбрано осознанно:
> - GIN-индекс по array поддерживает оператор `@>` (contains) → быстрый поиск по тегу
> - `SELECT * FROM channels WHERE workspace_id = ? AND tags @> ARRAY['music-video']` — использует оба индекса
> - Проще, чем JSONB для списка строк
> - Нативная поддержка в Exposed через `arrayLiteral`

> **`metadata_overrides` как JSONB** — sealed тип MetadataTemplate, та же структура что `rules.metadata_template`.

### 2.2 Примеры SQL-запросов

```sql
-- Найти канал по channelId + extractor в workspace
SELECT * FROM channels 
WHERE workspace_id = $1 AND channel_id = $2 AND extractor = $3;

-- Найти все каналы с тегом
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags @> ARRAY[$2];

-- Найти каналы с ВСЕМИ указанными тегами (AND)
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags @> ARRAY['music-video', 'lofi'];

-- Найти каналы с ЛЮБЫМ из указанных тегов (OR)
SELECT * FROM channels 
WHERE workspace_id = $1 AND tags && ARRAY['music-video', 'lofi'];

-- Все уникальные теги в workspace (для autocomplete в UI)
SELECT DISTINCT unnest(tags) AS tag FROM channels WHERE workspace_id = $1 ORDER BY tag;
```

### 2.3 Миграция

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

COMMENT ON TABLE channels IS 'Справочник каналов — каналы с тегами и переопределениями метаданных';
COMMENT ON COLUMN channels.channel_id IS 'ID канала на платформе (YouTube channel ID, RuTube channel ID, etc.)';
COMMENT ON COLUMN channels.extractor IS 'Платформа: youtube, rutube, vk, etc.';
COMMENT ON COLUMN channels.tags IS 'Теги для группировки каналов (PostgreSQL text array)';
COMMENT ON COLUMN channels.metadata_overrides IS 'MetadataTemplatePm JSON — переопределения метаданных для канала';
```

---

## 3. API Contract

### 3.1 DTO

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
// api:contract/rule/RuleMatchDto.kt (дополнение)
@Serializable
@SerialName("has-tag")
data class HasTag(val tag: String) : RuleMatchDto
```

### 3.3 Endpoints

```
# Channel Directory CRUD
GET    /api/v1/workspaces/{workspaceId}/channels                  — список каналов (фильтр по тегу: ?tag=music-video)
GET    /api/v1/workspaces/{workspaceId}/channels/{channelId}      — канал по ID
POST   /api/v1/workspaces/{workspaceId}/channels                  — создать канал
PUT    /api/v1/workspaces/{workspaceId}/channels/{channelId}      — обновить канал
DELETE /api/v1/workspaces/{workspaceId}/channels/{channelId}      — удалить канал

# Теги (вспомогательный)
GET    /api/v1/workspaces/{workspaceId}/channels/tags             — все уникальные теги в workspace
```

---

## 4. Infra (server:infra)

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
    
    // ... остальные методы
}
```

---

## 5. Полный Flow (пример)

### Сценарий: пользователь скачивает видео с канала Adele

**Подготовка (один раз):**

1. Пользователь добавляет канал в справочник:
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

2. Пользователь создаёт правило:
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

**При скачивании:**

1. Пользователь отправляет URL: `https://youtube.com/watch?v=abc`
2. `yt-dlp` извлекает `VideoInfo`: `channelId=UCKiHMVB6VWzjmOMKOFED2wA`, `extractor=youtube`
3. `RuleMatchingService`:
   - Ищет канал в справочнике по `channelId` + `extractor` → находит "Adele"
   - Формирует `MatchContext(video, overrides=null, channel=Adele)`
   - Правило "Music Videos" с `HasTag("music-video")` → проверяет `"music-video" in channel.tags` → **match!**
   - Возвращает `MatchResult(rule="Music Videos", channel=Adele)`
4. `PreviewUseCase`:
   - `mergeTemplates(rule.template, channel.metadataOverrides)` → `MusicVideo(artistOverride="Adele", defaultTags=["music"])`
   - `MetadataResolver.resolve(video, effectiveTemplate)` → `artist="Adele"`, `title="Hello"`
   - Путь: `/media/Music/Adele/Hello [abc].m4a`

**Без справочника** (тот же URL):

- Канал не найден в справочнике → `channel = null`
- `HasTag` не матчит → правило не срабатывает
- Fallback на LLM или generic metadata

---

## 6. Модули и файлы (план реализации)

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
│   └── TagTest.kt                   (new — в ValueClassValidationTest или отдельно)
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

## 7. Порядок реализации

1. **Domain**: `Tag`, `ChannelDirectoryEntryId`, `Channel`, `ChannelRepository`, тесты
2. **Domain**: `RuleMatch.HasTag`, `MatchContext.channel`, обновление `matches()` и `matchSpecificity()`, тесты
3. **Domain**: `MetadataTemplateMerger`, тесты
4. **Domain**: `MatchResult`, обновление `RuleMatchingService`, тесты
5. **Domain**: обновление `PreviewUseCase`
6. **DB**: миграция `V2__channel_directory.sql`
7. **Infra**: `ChannelsTable`, `ChannelPm`, маппинги, `ChannelRepositoryImpl`
8. **Infra**: обновление `RuleMatchPm` (HasTag)
9. **API Contract**: `ChannelDto`, `CreateChannelDto`, `UpdateChannelDto`, `RuleMatchDto.HasTag`
10. **API Mapping**: маппинги channel, маппинг HasTag
11. **Transport**: `channelRoutes.kt`
12. **DI**: wiring
13. **Features (UI)**: экраны управления каналами
14. **Документация**: обновить `DOMAIN.md`, `DATABASE.md`, `API_CONTRACT.md`

---

## 8. Альтернативы (отвергнутые)

### 8.1 RuleMatch.ChannelIdList (вариант 1 из запроса)

```kotlin
data class ChannelIdList(val channelIds: List<String>) : RuleMatch
```

**Отвергнуто**: не решает проблему per-channel переопределений метаданных. Список ID без контекста — не сильно лучше `AnyOf(ChannelId(...), ChannelId(...))`.

### 8.2 Теги как отдельная таблица (нормализация)

```sql
CREATE TABLE tags (id SERIAL PRIMARY KEY, name TEXT UNIQUE);
CREATE TABLE channel_tags (channel_id UUID, tag_id INT, ...);
```

**Отвергнуто**: overhead на JOIN, усложнение без выгоды. PostgreSQL `TEXT[]` + GIN — быстрее и проще для нашего масштаба (сотни каналов, десятки тегов). Если понадобится — можно мигрировать позже.

### 8.3 Теги в JSONB вместо TEXT[]

```sql
tags JSONB NOT NULL DEFAULT '[]'
```

**Отвергнуто**: `TEXT[]` + GIN — нативнее для PostgreSQL, оператор `@>` работает напрямую. JSONB `@>` тоже работает, но `TEXT[]` семантически точнее для списка строк.

### 8.4 Channel.metadataOverrides как UserOverrides

**Отвергнуто**: `UserOverrides` содержит только plain values (`artist`, `title`). `MetadataTemplate` содержит ещё patterns (`artistPattern`, `titlePattern`), что полезнее для справочника — один канал может иметь стабильный формат заголовков, для которого regex-pattern уместен.

---

## 9. Открытые вопросы

1. **Auto-discovery каналов**: стоит ли при первом скачивании с нового канала автоматически предлагать добавить его в справочник? (Можно реализовать в UI позже, не блокирует MVP.)

2. **Bulk import**: нужен ли импорт каналов из файла (CSV/JSON)? (Можно добавить отдельным endpoint позже.)

3. **Channel URL**: стоит ли хранить URL канала (например, `https://youtube.com/@adele`) для удобства навигации? (Можно добавить как optional field позже.)

