# ADR-007: Интерактивный Preview с User Overrides

**Статус**: Предложено  
**Дата**: 2026-03-04  
**Авторы**: Alex Elkin

---

## Контекст

Preview flow — ключевой сценарий приложения:
1. Пользователь вводит URL видео
2. Сервер извлекает метаданные через yt-dlp, подбирает правило, определяет категорию, резолвит metadata и storage plan
3. Пользователь видит результат и может отредактировать перед скачиванием

Но preview — это не одноразовое действие. Пользователь **взаимодействует** с формой: меняет категорию, уточняет артиста, корректирует название. Каждое уточнение должно **переоцениваться сервером** — найти более подходящее правило, пересчитать пути.

При этом вызов yt-dlp — дорогая операция (3-10 сек). Метаданные видео не меняются между уточнениями — кэширование обязательно.

---

## Решение

### Обзор

Preview — это **диалог** между фронтом и бекендом:

```
  Фронт                                   Бекенд
    │                                        │
    │  POST /preview {url}                   │
    │───────────────────────────────────────▶│──▶ yt-dlp (медленно)
    │                                        │──▶ кэш VideoInfo в PostgreSQL
    │◀───────────────────────────────────────│    rule matching → fallback
    │  category=OTHER, artist="?"            │
    │                                        │
    │  *** пользователь: category=music ***  │
    │                                        │
    │  POST /preview {url, overrides}        │
    │───────────────────────────────────────▶│──▶ кэш HIT (мгновенно)
    │                                        │──▶ rule matching + overrides → Rule!
    │◀───────────────────────────────────────│    metadata + storage plan
    │  category=MUSIC_VIDEO                  │
    │  artist="Rick Astley"                  │
    │  storagePlan=correct paths             │
```

Единый endpoint `POST /preview` принимает URL и optional user overrides. Сервер кэширует VideoInfo в PostgreSQL, при повторном запросе не обращается к yt-dlp.

---

## 1. Кэш VideoInfo (PostgreSQL)

### Цель

Не вызывать yt-dlp повторно для одного и того же URL.

### Порт (domain)

```kotlin
// domain/video/VideoInfoCache.kt
interface VideoInfoCache {
    suspend fun get(url: String): VideoInfo?
    suspend fun put(url: String, videoInfo: VideoInfo)
}
```

Размещение в `domain/video/` — рядом с `VideoInfoExtractor` port. Это доменный порт, который `PreviewUseCase` использует напрямую.

### Таблица PostgreSQL

```sql
-- V1__initial_schema.sql (добавляется в существующую миграцию)

CREATE TABLE video_info_cache (
    url         TEXT PRIMARY KEY,
    video_info  JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE video_info_cache IS 'Кэш VideoInfo из yt-dlp для избежания повторных вызовов';
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

### Реализация (server:infra)

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

`VideoInfoPm` уже существует в `server:infra/db/model/`. Маппинг `VideoInfo` ↔ `VideoInfoPm` добавляется в `db/mapping/videoInfo.kt`.

---

## 2. User Overrides (sealed)

### Концепция

Пользователь может уточнить категорию и поля метаданных. Overrides — sealed по категории (зеркалит `ResolvedMetadata`), потому что набор доступных полей зависит от категории:
- `MusicVideo` → artist, title, album
- `SeriesEpisode` → seriesName, season, episode, title
- `Other` → title

Если пользователь не уточнял ничего — overrides == null.

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

/** Категория, подразумеваемая overrides. */
val UserOverrides.category: Category get() = when (this) {
    is UserOverrides.MusicVideo -> Category.MUSIC_VIDEO
    is UserOverrides.SeriesEpisode -> Category.SERIES
    is UserOverrides.Other -> Category.OTHER
}
```

> Категория **не передаётся отдельным полем** — она определяется по типу sealed. Если пользователь переключил category на `MUSIC_VIDEO` → фронт создаёт `UserOverrides.MusicVideo(...)`.

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

`appliedOverrides` — эхо overrides, которые сервер учёл в ответе. Фронт сверяет: если его текущие overrides расходятся с `appliedOverrides` — ответ устарел (из-за debounce race condition), игнорировать.

### JSON примеры

**Первый запрос** (без overrides):
```json
{ "url": "https://youtube.com/watch?v=dQw4w9WgXcQ" }
```

**Повторный запрос** (пользователь выбрал music-video):
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

**Повторный запрос** (пользователь выбрал music-video и уточнил artist):
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

### Мотивация

Правила матчат по свойствам видео (канал, title, url). Но иногда правило должно срабатывать по **контексту запроса** — если пользователь явно выбрал категорию.

Пример: «Дефолтное правило для музыкальных видео» — задаёт пути `/media/Music Videos/...` и шаблон `MetadataTemplate.MusicVideo`. Не привязано к конкретному каналу. Срабатывает когда пользователь выбрал `MUSIC_VIDEO`.

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
    
    /** Матчит по категории из user overrides. Если overrides == null — не матчит. */
    data class CategoryEquals(val category: Category) : RuleMatch
}
```

Специфичность = 20 (самая низкая — широкий критерий):

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

`RuleMatch` матчит по **контексту** — видео + user overrides. Это основная (и единственная) функция матчинга:

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

> `CategoryEquals` матчит **только** когда overrides != null и категория совпадает.
> Если overrides не предоставлены (первый запрос) — `CategoryEquals` не матчит.

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

### Примеры правил

**Дефолтное правило для музыкальных видео** (низкий приоритет):
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

**Правило для конкретного канала + категории** (высокий приоритет):
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
        // 1. VideoInfo: кэш (PostgreSQL) или yt-dlp
        val videoInfo = videoInfoCache.get(url)
            ?: videoInfoExtractor.extract(url).bind().also { videoInfoCache.put(url, it) }

        // 2. Rule matching с учётом overrides
        val matchedRule = ruleMatchingService.findMatchingRule(videoInfo, workspaceId, overrides)

        // 3. Resolve metadata (rule → LLM → fallback)
        val (metadata, source) = resolveMetadata(videoInfo, matchedRule)

        // 4. Apply user overrides поверх resolved metadata
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
     * Применяет user overrides поверх resolved metadata.
     * Override-поля имеют наивысший приоритет.
     * Тип sealed overrides определяет целевую категорию.
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

### Порядок приоритетов метаданных

```
1. UserOverrides (ручной ввод пользователя)          ← наивысший
2. Rule MetadataTemplate (если правило найдено)
3. LLM suggestion (если LLM настроен, правило не найдено)
4. Fallback (парсинг title по разделителям)           ← наименьший
```

Шаги 2–4 определяют «базовые» метаданные. Шаг 1 (`applyOverrides`) перезаписывает только те поля, которые пользователь явно задал (не null).

---

## 5. Фронтенд

### Два слоя state на PreviewScreen

| Слой | Описание |
|------|----------|
| `serverPreview` | Последний ответ от `POST /preview` |
| `userEdits` | `Set<String>` — поля, которые пользователь менял вручную |

При получении нового ответа от сервера:
- Поля **не** в `userEdits` — обновляются из ответа
- Поля **в** `userEdits` — сохраняют значение пользователя

### Debounce-стратегия

| Триггер | Debounce | Обоснование |
|---------|----------|-------------|
| Смена category (SegmentedButton) | 0ms — сразу | Дискретный выбор, пользователь завершил действие |
| Текстовые поля (artist, title, album...) | 700ms | Пользователь ещё печатает |

### Re-preview flow

```
Пользователь меняет поле
    ↓
[debounce 0ms/700ms]
    ↓
Собираются ВСЕ текущие userEdits → UserOverridesDto (sealed, тип = текущая category)
    ↓
POST /preview { url, overrides: { type: "music-video", artist: "Rick Astley" } }
    ↓
Ответ получен, сверяем appliedOverrides
    ↓
Обновляем поля НЕ из userEdits
```

### Построение UserOverridesDto из userEdits

Фронт собирает overrides на основе текущей выбранной категории:

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

> Когда пользователь переключает category — это **всегда** создаёт overrides (даже без изменения текстовых полей), потому что сам тип sealed определяет категорию. Именно поэтому debounce для category = 0ms.

### Race conditions

`appliedOverrides` в ответе позволяет фронту отличить актуальный ответ от устаревшего. Если `appliedOverrides` не совпадает с текущими overrides фронта — ответ пришёл на устаревший запрос, игнорируем.

Дополнительно: при отправке нового запроса — отменяем предыдущий in-flight запрос (coroutine cancellation).

### Индикация загрузки

При re-preview — subtle inline indicator (shimmer или маленький progress на секциях Metadata / Storage Plan). Поля остаются редактируемыми.

---

## 6. Полный Sequence Diagram

```
┌──────────┐                    ┌──────────────┐                  ┌──────────────┐
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
     │                                 │                                 │    (мгновенно)
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
     │ *** фронт: обновляет поля,      │                                 │
     │   которые user не менял ***     │                                 │
```

---

## Чек-лист реализации

### Кэш VideoInfo (PostgreSQL)
- [x] `VideoInfoCache` interface в `domain/video/`
- [x] Таблица `video_info_cache` в `V1__initial_schema.sql`
- [x] `VideoInfoCacheTable` в `server:infra/db/table/`
- [x] `VideoInfoCacheImpl` в `server:infra/db/repository/`
- [x] Маппинг `VideoInfo` ↔ `VideoInfoPm` (полный, с thumbnails) в `db/mapping/videoInfo.kt`
- [x] DI wiring в `server:di`
- [ ] Unit тесты для кэша

### UserOverrides (sealed) + PreviewUseCase
- [x] `UserOverrides` sealed interface в `domain/preview/`
- [x] Extension property `UserOverrides.category`
- [x] `UserOverridesDto` sealed interface в `api:contract/preview/`
- [x] Маппинг `UserOverridesDto` ↔ `UserOverrides` в `api:mapping`
- [x] `PreviewRequestDto` — поле `overrides: UserOverridesDto?`
- [x] `PreviewResponseDto` — поле `appliedOverrides: UserOverridesDto?`
- [x] `PreviewUseCase.preview()` — приём overrides, кэш, `applyOverrides()`
- [x] `previewRoutes.kt` — передача overrides
- [ ] Unit тесты для `applyOverrides`

### RuleMatch.CategoryEquals
- [x] `RuleMatch.CategoryEquals` в `domain/rule/`
- [x] `matches(ctx: MatchContext)` вместо `matchesVideo(video)`
- [x] `MatchContext` data class в `domain/rule/`
- [x] `matchSpecificity()` += CategoryEquals → 20
- [x] `RuleMatchDto.CategoryEquals` в `api:contract`
- [x] `RuleMatchPm.CategoryEquals` в `server:infra/db/model/`
- [x] Маппинг domain ↔ DTO ↔ Pm
- [x] `RuleMatchingService.findMatchingRule()` → принимает overrides
- [x] Arb.ruleMatch() генератор — CategoryEquals
- [ ] Unit тесты

### Фронтенд
- [ ] `userEdits: Set<String>` state на PreviewScreen
- [ ] `buildOverrides()` — сборка sealed UserOverridesDto из текущего state
- [ ] Debounce: category → 0ms, текст → 700ms
- [ ] Повторный `POST /preview` с overrides
- [ ] Логика merge: ответ сервера + userEdits
- [ ] Проверка `appliedOverrides` для race conditions
- [ ] Cancel предыдущего in-flight запроса
- [ ] Subtle loading indicator при re-preview

---

## Связанные документы

- [DOMAIN.md](../DOMAIN.md) — §5 Rule, §6 Metadata, §9 Preview
- [API_CONTRACT.md](../API_CONTRACT.md) — §6.1 POST /preview
- [DATABASE.md](../DATABASE.md) — §2 Схема, §4 Exposed Tables
- [ADR-002: Sealed Classes](002-sealed-classes.md) — RuleMatch, ResolvedMetadata hierarchy

