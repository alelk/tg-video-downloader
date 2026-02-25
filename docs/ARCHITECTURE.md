# Архитектура

> **Цель документа**: описать модульную структуру, зависимости и принципы проектирования.

---

## 1. Принципы

### 1.1 Чистая архитектура (Clean Architecture)

```
┌─────────────────────────────────────────────────────────┐
│                    UI (tgminiapp)                       │
├─────────────────────────────────────────────────────────┤
│               Transport (server-transport)              │
├─────────────────────────────────────────────────────────┤
│          Application (domain use-cases)                 │
├─────────────────────────────────────────────────────────┤
│                  Domain (domain)                        │
├─────────────────────────────────────────────────────────┤
│           Infrastructure (server-infra)                 │
└─────────────────────────────────────────────────────────┘
```

**Правило зависимостей**: внутренние слои не знают о внешних.

### 1.2 Kotlin-идиоматичность

- **Sealed classes/interfaces** для полиморфных типов (RuleMatch, ResolvedMetadata, DomainError)
- **Data classes** для DTO и value objects
- **Value classes** для typesafe идентификаторов
- **Extension functions** для утилит
- **Coroutines** для асинхронности
- **Result/Either** для обработки ошибок без исключений

### 1.3 Contract-first

- API-контракт (`api-contract`) определяется до реализации
- DTO стабильны и версионируются
- Изменения через `/api/v2/...` или новые optional поля

---

## 2. Модули

### 2.1 Диаграмма зависимостей

```
                         ┌─────────────┐
                         │  tgminiapp  │
                         │  (JS/Wasm)  │
                         └──────┬──────┘
                                │
                         ┌──────▼──────┐
                         │ api-client  │
                         └──────┬──────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
         ▼                      ▼                      │
┌─────────────────┐    ┌─────────────────┐            │
│  api-contract   │    │   api-mapping   │            │
└────────┬────────┘    └────────┬────────┘            │
         │                      │                      │
         │              ┌───────▼───────┐              │
         │              │    domain     │◄─────────────┘
         │              └───────┬───────┘
         │                      │
         │     ┌────────────────┼────────────────┐
         │     │                │                │
         ▼     ▼                ▼                ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│server-transport │    │  server-infra   │    │    server-di    │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         └──────────────────────┼──────────────────────┘
                                │
                         ┌──────▼──────┐
                         │ server-app  │
                         └─────────────┘
```

### 2.2 Описание модулей

#### `domain`

**Назначение**: Бизнес-логика, чистый Kotlin.

**Содержит**:
- Доменные модели (sealed classes, data classes)
- Value objects (`VideoId`, `ChannelId`)
- Доменные сервисы (`RuleMatchingService`, `MetadataResolver`)
- Порты для внешних сервисов (`LlmProviderPort`, `RuleRepository`)
- Use-cases (`CreateJobUseCase`, `PreviewUseCase`) -> `PreviewUseCase` вызывает `MetadataResolver`, который при отсутствии правил может дернуть `LlmProviderPort`
- Доменные ошибки (`sealed interface DomainError`)
- Политики (`RetryPolicy`, `StoragePolicy`)

**Зависимости**: Только Kotlin stdlib.

**Не содержит**: Ktor, kotlinx.serialization, DB, файловая система.

```kotlin
// Пример структуры пакетов
domain/
├── model/
│   ├── Rule.kt
│   ├── RuleMatch.kt          // sealed interface
│   ├── Category.kt           // enum
│   ├── ResolvedMetadata.kt   // sealed interface
│   ├── Job.kt
│   └── VideoInfo.kt
├── service/
│   ├── RuleMatchingService.kt
│   └── MetadataResolver.kt
├── usecase/
│   ├── PreviewUseCase.kt
│   └── CreateJobUseCase.kt
├── error/
│   └── DomainError.kt        // sealed interface
├── policy/
│   ├── DownloadPolicy.kt
│   ├── StoragePolicy.kt
│   └── PostProcessPolicy.kt
└── port/
    ├── RuleRepository.kt     // interface
    ├── JobRepository.kt      // interface
    └── VideoInfoExtractor.kt // interface
```

---

#### `api-contract`

**Назначение**: DTO для HTTP API (request/response).

**Содержит**:
- Request/Response DTO
- Sealed DTO с discriminator `type`
- `ApiErrorDto`
- Константы версий API

**Зависимости**: Kotlin stdlib, kotlinx.serialization.

```kotlin
// Пример
api-contract/
├── v1/
│   ├── PreviewRequestDto.kt
│   ├── PreviewResponseDto.kt
│   ├── CreateJobRequestDto.kt
│   ├── JobDto.kt
│   ├── RuleDto.kt
│   ├── RuleMatchDto.kt       // sealed with @SerialName
│   ├── ResolvedMetadataDto.kt // sealed with @SerialName
│   └── ApiErrorDto.kt
└── common/
    └── PaginationDto.kt
```

---

#### `api-mapping`

**Назначение**: Конвертация domain ↔ DTO.

**Содержит**:
- Extension functions: `RuleMatch.toDto()`, `RuleMatchDto.toDomain()`
- Валидация DTO перед конвертацией
- Адаптация `DomainError` → `ApiErrorDto`

**Зависимости**: `domain`, `api-contract`.

```kotlin
// Пример
fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.ChannelId -> RuleMatchDto.ChannelId(value)
    is RuleMatch.AllOf -> RuleMatchDto.AllOf(matches.map { it.toDto() })
    // ...
}

fun RuleMatchDto.toDomain(): Either<ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.ChannelId -> 
        if (value.isBlank()) ValidationError("value is blank").left()
        else RuleMatch.ChannelId(value).right()
    // ...
}
```

---

#### `api-client`

**Назначение**: Typed HTTP-клиент для UI.

**Содержит**:
- `TgVideoDownloaderClient` interface + Ktor implementation
- Автоматическая передача `initData`
- Retry на уровне HTTP
- (De)сериализация

**Зависимости**: `api-contract`, Ktor Client (KMP).

**Платформы**: JVM, JS, (Wasm).

```kotlin
interface TgVideoDownloaderClient {
    suspend fun preview(request: PreviewRequestDto): ApiResult<PreviewResponseDto>
    suspend fun createJob(request: CreateJobRequestDto): ApiResult<JobDto>
    suspend fun getJob(id: String): ApiResult<JobDto>
    suspend fun listJobs(status: String? = null, limit: Int = 20, offset: Int = 0): ApiResult<List<JobDto>>
    // ...
}
```

---

#### `server-infra`

**Назначение**: Реализация портов (DB, внешние процессы, FS).

**Содержит**:
- `RuleRepositoryImpl` (Exposed)
- `JobRepositoryImpl` (Exposed)
- `YtDlpVideoInfoExtractor`
- `YtDlpDownloader`
- `FfmpegConverter`
- `FileStorageService`
- DB схема (Exposed tables)
- Flyway миграции

**Зависимости**: `domain`, Exposed, Flyway, kotlinx.serialization.

```kotlin
server-infra/
├── db/
│   ├── tables/
│   │   ├── RulesTable.kt
│   │   └── JobsTable.kt
│   ├── repository/
│   │   ├── RuleRepositoryImpl.kt
│   │   └── JobRepositoryImpl.kt
│   └── migration/
│       └── V1__initial.sql
├── process/
│   ├── YtDlpVideoInfoExtractor.kt
│   ├── YtDlpDownloader.kt
│   └── FfmpegConverter.kt
├── storage/
│   └── FileStorageService.kt
└── json/
    └── JsonColumnType.kt  // Exposed JSONB support
```

---

#### `server-transport`

**Назначение**: HTTP слой (Ktor routing).

**Содержит**:
- Ktor routes (`/api/v1/...`)
- `TelegramAuthPlugin` (проверка initData)
- Request validation
- Error handling (exception → ApiErrorDto)
- CORS, logging plugins

**Зависимости**: `domain`, `api-contract`, `api-mapping`, Ktor Server.

```kotlin
server-transport/
├── plugin/
│   ├── TelegramAuthPlugin.kt
│   ├── ErrorHandlingPlugin.kt
│   └── CorrelationIdPlugin.kt
├── route/
│   ├── PreviewRoutes.kt
│   ├── JobRoutes.kt
│   └── RuleRoutes.kt
└── validation/
    └── RequestValidation.kt
```

---

#### `server-di`

**Назначение**: Dependency injection wiring.

**Содержит**:
- Koin modules
- Factory functions

**Зависимости**: `domain`, `server-infra`, `server-transport`, Koin.

```kotlin
val domainModule = module {
    single { RuleMatchingService() }
    single { MetadataResolver(get()) }
    factory { PreviewUseCase(get(), get(), get()) }
    factory { CreateJobUseCase(get(), get()) }
}

val infraModule = module {
    single<RuleRepository> { RuleRepositoryImpl(get()) }
    single<JobRepository> { JobRepositoryImpl(get()) }
    single<VideoInfoExtractor> { YtDlpVideoInfoExtractor(get()) }
}
```

---

#### `server-app`

**Назначение**: Entrypoint, сборка приложения.

**Содержит**:
- `Application.kt` (Ktor entrypoint)
- Configuration loading (Hoplite)
- Server startup

**Зависимости**: Все серверные модули.

```kotlin
fun main() {
    embeddedServer(Netty, port = config.server.port) {
        configureDI()
        configureSerialization()
        configureRouting()
        configureAuth()
    }.start(wait = true)
}
```

---

#### `tgminiapp`

**Назначение**: Telegram Mini App UI.

**Содержит**:
- Compose Multiplatform Web UI
- Screens: UrlInput, Preview, JobList, RuleEditor
- ViewModels / State holders
- Telegram WebApp JS interop

**Зависимости**: `domain` (models), `api-client`, Compose Multiplatform.

**Target**: JS (Browser), возможно Wasm в будущем.

```kotlin
tgminiapp/
├── src/jsMain/kotlin/
│   ├── Main.kt
│   ├── App.kt
│   ├── screen/
│   │   ├── UrlInputScreen.kt
│   │   ├── PreviewScreen.kt
│   │   ├── JobListScreen.kt
│   │   └── RuleEditorScreen.kt
│   ├── component/
│   │   ├── MetadataEditor.kt
│   │   └── ProgressBar.kt
│   ├── state/
│   │   ├── AppState.kt
│   │   └── JobState.kt
│   └── telegram/
│       └── TelegramWebApp.kt  // JS interop
└── src/jsMain/resources/
    └── index.html
```

---

## 3. Принципы кодирования

### 3.1 Error handling

**В domain**: `Either<DomainError, T>` или `Result<T>` (без исключений).

```kotlin
sealed interface DomainError {
    data class ValidationError(val field: String, val message: String) : DomainError
    data class VideoUnavailable(val videoId: String, val reason: String) : DomainError
    data class RuleNotFound(val id: UUID) : DomainError
    // ...
}

suspend fun PreviewUseCase.execute(url: String): Either<DomainError, PreviewResult>
```

**В transport**: Ловим `DomainError`, мапим в HTTP-статус + `ApiErrorDto`.

### 3.2 Async

- Все I/O операции — `suspend fun`
- Для параллельных задач — `coroutineScope { async { ... } }`
- Job execution — `CoroutineDispatcher` из DI

### 3.3 Конфигурация

- Hoplite для загрузки YAML/env
- Data classes для config
- Defaults через `@DefaultValue`

```kotlin
data class AppConfig(
    val server: ServerConfig,
    val telegram: TelegramConfig,
    val db: DbConfig,
    val storage: StorageConfig,
    val ytDlp: YtDlpConfig,
)

data class TelegramConfig(
    val botToken: String,
    val allowedUserIds: List<String>,
    @DefaultValue("false")
    val devMode: Boolean,
)
```

### 3.4 Логирование

- `kotlin-logging` (обёртка над SLF4J)
- Structured logging (JSON в production)
- `correlationId` через MDC

```kotlin
private val logger = KotlinLogging.logger {}

logger.info { "Job started" }
logger.withLoggingContext("jobId" to job.id.toString()) {
    logger.info { "Downloading..." }
}
```

---

## 4. Gradle модули

### 4.1 settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

include(
    ":domain",
    ":api-contract",
    ":api-mapping",
    ":api-client",
    ":server-infra",
    ":server-transport",
    ":server-di",
    ":server-app",
    ":tgminiapp",
)
```

### 4.2 Версии (libs.versions.toml)

```toml
[versions]
kotlin = "2.3.0"
ktor = "3.1.0"
exposed = "0.58.0"
koin = "4.1.0"
serialization = "1.8.0"
coroutines = "1.10.0"
arrow = "2.0.0"
kotest = "5.9.0"
logback = "1.5.0"
flyway = "10.0.0"
compose = "1.7.0"

[libraries]
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }

exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-dao = { module = "org.jetbrains.exposed:exposed-dao", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-json = { module = "org.jetbrains.exposed:exposed-json", version.ref = "exposed" }
exposed-java-time = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-ktor = { module = "io.insert-koin:koin-ktor", version.ref = "koin" }

kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }

arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }

kotest-runner = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
```

---

## 5. Потоки данных

### 5.1 Preview flow

```
┌─────────┐    POST /api/v1/preview    ┌─────────────────┐
│  Mini   │ ─────────────────────────▶ │ server-transport│
│   App   │                            │  (Ktor route)   │
└─────────┘                            └────────┬────────┘
                                                │
                                                ▼
                                       ┌────────────────┐
                                       │ PreviewUseCase │
                                       │    (domain)    │
                                       └────────┬───────┘
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    │                           │                           │
                    ▼                           ▼                           ▼
           ┌────────────────┐          ┌────────────────┐          ┌────────────────┐
           │VideoInfoExtrac │          │RuleRepository  │          │RuleMatching    │
           │    tor.extract │          │    .findAll    │          │   Service      │
           └────────────────┘          └────────────────┘          └────────────────┘
                    │                           │                           │
                    │                           │                           │
                    ▼                           ▼                           ▼
           ┌────────────────┐          ┌────────────────┐          ┌────────────────┐
           │   yt-dlp       │          │   PostgreSQL   │          │ MetadataResolver│
           │  subprocess    │          │                │          │                │
           └────────────────┘          └────────────────┘          └────────────────┘
```

### 5.2 Job execution flow

```
┌──────────────┐
│ JobScheduler │  (polls QUEUED jobs)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ JobExecutor  │
└──────┬───────┘
       │
       ├──▶ YtDlpDownloader.download()
       │         │
       │         ▼
       │    (temp file)
       │
       ├──▶ FfmpegConverter.convert()  (if needed)
       │         │
       │         ▼
       │    (converted file)
       │
       ├──▶ MetadataTagger.tag()  (if needed)
       │
       ├──▶ FileStorageService.moveToFinal()
       │
       └──▶ JobRepository.updateStatus(DONE)
```

---

## 6. Расширяемость

### 6.1 Добавление нового источника видео

1. Реализовать `VideoInfoExtractor` для нового источника
2. Добавить `extractor` в `VideoSource`
3. Зарегистрировать в DI

### 6.2 Добавление новой категории

1. Добавить в `enum Category`
2. Добавить sealed subclass в `ResolvedMetadata`
3. Добавить sealed subclass в `ResolvedMetadataDto`
4. Добавить маппинг
5. Обновить UI для новых полей

### 6.3 Добавление нового типа матчинга

1. Добавить sealed subclass в `RuleMatch`
2. Добавить sealed subclass в `RuleMatchDto`
3. Добавить маппинг
4. Реализовать логику в `RuleMatchingService`
5. Обновить UI rule editor

