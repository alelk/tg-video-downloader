# Архитектура

> **Цель документа**: описать модульную структуру, зависимости, KMP-стратегию и принципы проектирования.

---

## 1. Принципы

### 1.1 Чистая архитектура (Clean Architecture)

```
┌─────────────────────────────────────────────────────────┐
│           UI Shells (tgminiapp, web, macOS…)            │
├─────────────────────────────────────────────────────────┤
│            Features (Compose Multiplatform)              │
├─────────────────────────────────────────────────────────┤
│                API Client / Transport                    │
├─────────────────────────────────────────────────────────┤
│          Application (domain use-cases)                  │
├─────────────────────────────────────────────────────────┤
│                  Domain (domain)                         │
├─────────────────────────────────────────────────────────┤
│           Infrastructure (server:infra)                  │
└─────────────────────────────────────────────────────────┘
```

**Правило зависимостей**: внутренние слои не знают о внешних.

### 1.2 Kotlin Multiplatform

Проект использует **Kotlin Multiplatform (KMP)** для переиспользования кода между сервером (JVM), Telegram Mini App (JS) и будущими нативными клиентами.

| Модуль             | Kotlin Plugin   | Targets     | Обоснование                                        |
|--------------------|-----------------|-------------|----------------------------------------------------|
| `domain`           | `multiplatform` | `jvm`, `js` | Доменные модели шарятся между сервером и клиентами |
| `api:contract`     | `multiplatform` | `jvm`, `js` | DTO шарятся через kotlinx.serialization            |
| `api:mapping`      | `multiplatform` | `jvm`, `js` | Маппинг нужен и на сервере, и в features           |
| `api:client`       | `multiplatform` | `jvm`, `js` | HTTP-клиент работает на обеих платформах           |
| `api:client:di`    | `multiplatform` | `jvm`, `js` | Koin-модули для wiring клиента на каждой платформе |
| `features`         | `multiplatform` | `jvm`, `js` | Compose UI шарится между shell-приложениями        |
| `tgminiapp`        | `multiplatform` | `js`        | Telegram-специфичная shell, только браузер         |
| `server:infra`     | `jvm`           | `jvm`       | DB, процессы — JVM-only                            |
| `server:transport` | `jvm`           | `jvm`       | Ktor Server — JVM-only                             |
| `server:di`        | `jvm`           | `jvm`       | Серверный DI wiring                                |
| `server:app`       | `jvm`           | `jvm`       | Entrypoint, JVM-only                               |

### 1.3 Kotlin-идиоматичность

- **Sealed classes/interfaces** для полиморфных типов (RuleMatch, ResolvedMetadata, MetadataTemplate, UserOverrides, OutputFormat, DomainError)
- **Data classes** для DTO и value objects
- **Value classes** для typesafe идентификаторов и доменных примитивов (KMP-совместимые с Kotlin 2.1+)
- **Extension properties** для cheap computed values (например, `ResolvedMetadata.category`)
- **Coroutines** для асинхронности
- **Either** (Arrow) для обработки ошибок без исключений

### 1.4 Contract-first

- API-контракт (`api:contract`) определяется до реализации
- DTO стабильны и версионируются
- Изменения через `/api/v2/...` или новые optional поля

---

## 2. Модули

### 2.1 Диаграмма зависимостей

```
                     ┌──────────────────┐
                     │    tgminiapp     │  (JS only — Telegram shell)
                     │                  │
                     └────────┬─────────┘
                              │
                     ┌────────▼─────────┐
                     │    features      │  (KMP — Compose Multiplatform UI)
                     └────────┬─────────┘
                              │
              ┌───────────────┼────────────────┐
              │               │                │
              ▼               ▼                ▼
      ┌──────────────┐ ┌──────────┐  ┌──────────────┐
      │  api:client  │ │  domain  │  │ api:mapping  │
      └──────┬───────┘ └────┬─────┘  └──────┬───────┘
             │              │               │
      ┌──────▼───────┐     │        ┌──────▼───────┐
      │api:client:di │     │        │ api:contract │
      └──────────────┘     │        └──────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
  │server:transp.│ │ server:infra │ │  server:di   │
  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
         │                │                │
         └────────────────┼────────────────┘
                          │
                   ┌──────▼──────┐
                   │ server:app  │
                   └─────────────┘
```

### 2.2 Описание модулей

#### `domain` — KMP (jvm, js)

**Назначение**: Бизнес-логика, чистый Kotlin. Ядро приложения.

**Организация**: Package-by-feature (не по техническим слоям).

**Содержит**:
- `common/` — Value objects (`VideoId`, `RuleId`, `JobId`, `WorkspaceId`, `Url`, `FilePath`, `LocalDate`, `Extractor`), `Category`, `DomainError`
- `workspace/` — `Workspace`, `WorkspaceMember`, `WorkspaceRole`, `WorkspaceRepository` port
- `video/` — `VideoSource`, `VideoInfo`, `VideoInfoExtractor` port, `VideoInfoCache` port
- `rule/` — `Rule`, `RuleMatch` (sealed, вкл. `CategoryEquals`), `MatchContext`, `RuleMatchingService`, `RuleRepository` port
- `metadata/` — `ResolvedMetadata` (sealed), `MetadataTemplate` (sealed), `MetadataResolver`, `LlmPort`
- `storage/` — `StoragePlan`, `OutputRule`, `OutputFormat` (sealed), `PathTemplateEngine`, `VideoDownloader` port
- `job/` — `Job`, `JobStatus`, `CreateJobUseCase`, `JobRepository` port
- `preview/` — `UserOverrides` (sealed), `PreviewUseCase` (оркестратор)

**Зависимости**: Kotlin stdlib (`kotlin.time.Instant`, `kotlin.time.Duration`, `kotlin.uuid.Uuid`), Arrow (Either), kotlinx-coroutines.

**Не содержит**: Ktor, kotlinx.serialization, DB, файловая система.

```
domain/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── common/         # Value objects (VideoId, WorkspaceId, Url, FilePath, LocalDate, Extractor...), Category, DomainError
├── workspace/      # Workspace, WorkspaceMember, WorkspaceRole, WorkspaceRepository port
├── video/          # VideoSource, VideoInfo, VideoInfoExtractor port, VideoInfoCache port
├── rule/           # Rule, RuleMatch (sealed), MatchContext, matches.kt, RuleMatchingService, RuleRepository port
├── metadata/       # ResolvedMetadata (sealed), MetadataTemplate (sealed), MetadataResolver, LlmPort
├── storage/        # StoragePlan, OutputRule, OutputFormat (sealed), PathTemplateEngine, VideoDownloader port
├── job/            # Job, JobStatus, CreateJobUseCase, JobRepository port
└── preview/        # UserOverrides (sealed), PreviewUseCase
```

> Пакеты организованы без циклических зависимостей. Каждый пакет при росте проекта может быть извлечён в отдельный Gradle-модуль.

---

#### `api:contract` — KMP (jvm, js)

**Назначение**: DTO для HTTP API (request/response).

**Содержит**: Request/Response DTO, Sealed DTO с discriminator `type`, `ApiErrorDto`.

**Зависимости**: Kotlin stdlib, kotlinx.serialization.

---

#### `api:mapping` — KMP (jvm, js)

**Назначение**: Конвертация domain ↔ DTO.

**Зависимости**: `domain`, `api:contract`, Arrow.

> Маппинг размещён в KMP-модуле, т.к. используется и на сервере (`server:transport`), и на клиенте (`features`).

---

#### `api:client` — KMP (jvm, js)

**Назначение**: Typed HTTP-клиент для UI и тестов.

**Зависимости**: `api:contract`, Ktor Client.

---

#### `api:client:di` — KMP (jvm, js)

**Назначение**: Koin-модули для wiring `api:client`.

**Содержит**: Koin module с фабрикой `TgVideoDownloaderClient`, platform-specific Ktor engine (expect/actual).

**Зависимости**: `api:client`, Koin core, Ktor Client engine.

```kotlin
// commonMain
val apiClientModule = module {
    single<HttpClient> { createPlatformHttpClient(get()) }
    single<TgVideoDownloaderClient> { TgVideoDownloaderClientImpl(get()) }
}

// jvmMain
actual fun createPlatformHttpClient(config: ClientConfig): HttpClient =
    HttpClient(CIO) { /* ... */ }

// jsMain
actual fun createPlatformHttpClient(config: ClientConfig): HttpClient =
    HttpClient(Js) { /* ... */ }
```

---

#### `features` — KMP (jvm, js) + Compose Multiplatform

**Назначение**: Переиспользуемые UI-компоненты (Compose Multiplatform).

**Содержит**: Экраны, компоненты, state holders / ViewModels, навигация.

**Зависимости**: `domain`, `api:client`, `api:mapping`, Compose Multiplatform, Koin.

**Не содержит**: Platform-specific код (Telegram interop, Android Activity и т.д.)

```
features/src/commonMain/kotlin/io/github/alelk/tgvd/features/
├── common/
│   ├── component/
│   │   ├── WorkspaceTopBar.kt       ← текущий workspace в TopBar, смена через bottom sheet
│   │   ├── CreateWorkspaceDialog.kt  ← диалог создания нового workspace
│   │   ├── WorkspaceSelector.kt      ← dropdown для выбора workspace
│   │   ├── SectionCard.kt
│   │   ├── ErrorCard.kt
│   │   └── InfoRow.kt
│   ├── persistence/
│   │   └── PreferencesStorage.kt     ← интерфейс для сохранения настроек (KMP)
│   ├── state/
│   │   └── WorkspaceState.kt         ← shared state: workspaces + selectedWorkspace + persistence
│   └── theme/
├── navigation/
│   └── AppNavigation.kt              ← Scaffold с TopBar (workspace) + BottomBar (tabs)
├── download/
├── jobs/
├── rules/
├── settings/
└── di/
    └── FeaturesModule.kt
```

> Это ключевой модуль для мультиплатформенности. Новый UI-shell (web, macOS, Android) подключает `features` и добавляет только platform-specific glue.
> 
> `PreferencesStorage` — KMP-интерфейс. Каждый shell предоставляет свою реализацию (JS → `localStorage`, Android → `SharedPreferences`, и т.д.)

---

#### `tgminiapp` — JS only (browser)

**Назначение**: Telegram Mini App shell (тонкая обёртка).

**Содержит**: Main.kt, LocalStoragePreferences.kt, TelegramWebApp interop, DI wiring.

**Зависимости**: `features`, `api:client:di`, Compose HTML/Web runtime.

**Не содержит**: Бизнес-логику, экраны, компоненты — всё в `features`.

**Persistence**: Реализует `PreferencesStorage` через `localStorage` браузера. Выбранный workspace сохраняется между сессиями.

> В будущем рядом появятся: `webapp` (JS), `desktopapp` (JVM), `androidapp` — все зависят от `features`.

---

#### `server:infra` — JVM only

**Назначение**: Реализация доменных портов (DB, процессы, FS, LLM).

**Содержит**:
- `db/` — таблицы, репозитории, persistence-модели, маппинги
- `process/` — YtDlpRunner, FfmpegRunner, YtDlpServiceImpl
- `service/` — JobProcessor (фоновый обработчик задач)
- `config/` — data-классы конфигурации

**JobProcessor** — фоновый coroutine-цикл, который:
1. Поллит БД на наличие `PENDING` job'ов (интервал из `JobsConfig.pollIntervalMs`)
2. Ограничивает параллелизм через `Semaphore(maxConcurrentDownloads)`
3. Скачивает видео через `VideoDownloader.downloadWithProgress()` с обновлением прогресса
4. Обновляет статус job'а: `PENDING → DOWNLOADING → COMPLETED / FAILED`
5. Запускается/останавливается автоматически с lifecycle Ktor Application

**Зависимости**: `domain`, Exposed, Flyway, Ktor Client (JVM), kotlinx.serialization.

> `server:infra` **не зависит** от `api:contract` и `api:mapping`. JSONB-колонки используют собственные persistence-модели (`*Pm`), маппинг domain ↔ DB полностью изолирован от API-контракта.

---

#### `server:transport` — JVM only

**Назначение**: HTTP слой (Ktor Server routing).

**Зависимости**: `domain`, `api:contract`, `api:mapping`, Ktor Server.

---

#### `server:di` — JVM only

**Назначение**: Dependency injection wiring для серверных модулей.

**Зависимости**: `domain`, `server:infra`, `server:transport`, Koin.

---

#### `server:app` — JVM only

**Назначение**: Entrypoint, сборка серверного приложения.

**Зависимости**: Все серверные модули.

---

### 2.3 Правила зависимостей

| Модуль             | Может зависеть от                                      | НЕ может зависеть от           |
|--------------------|--------------------------------------------------------|--------------------------------|
| `domain`           | Kotlin stdlib, Arrow, kotlinx-coroutines               | Всё остальное                  |
| `api:contract`     | Kotlin stdlib, kotlinx.serialization                   | domain, server:*, features     |
| `api:mapping`      | domain, api:contract, Arrow                            | server:*, api:client, features |
| `api:client`       | api:contract, Ktor Client                              | domain, server:*, features     |
| `api:client:di`    | api:client, Koin, Ktor engine                          | domain, server:*, features     |
| `features`         | domain, api:client, api:mapping, Compose, Koin         | server:*, tgminiapp            |
| `tgminiapp`        | features, api:client:di                                | server:*, domain напрямую      |
| `server:infra`     | domain                                                 | api:*, transport, di, app      |
| `server:transport` | domain, api:contract, api:mapping, Ktor Server         | infra, di, app, features       |
| `server:di`        | domain, server:infra, server:transport, Koin           | api:*, app, features           |
| `server:app`       | domain, api:contract, server:*, Hoplite, Netty         | api:mapping, features          |

---

## 3. Принципы кодирования

### 3.1 Error handling

**В domain** (`commonMain`): `Either<DomainError, T>` (без исключений).

**В transport** (JVM): Ловим `DomainError`, мапим в HTTP-статус + `ApiErrorDto`.

### 3.2 Async

- Все I/O операции — `suspend fun`
- `kotlinx-coroutines` используется во всех KMP-модулях
- Job execution — `CoroutineDispatcher` из DI

### 3.3 Конфигурация

- Hoplite для загрузки YAML/env (только `server:app`, JVM)
- Data classes для config

### 3.4 KMP source set conventions

Весь переиспользуемый код — в `commonMain`. Platform-specific — через `expect/actual`.

---

## 4. Gradle модули

### 4.1 settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

// === Domain (KMP) ===
include(":domain")

// === API (KMP) ===
include(":api:contract")
include(":api:mapping")
include(":api:client")
include(":api:client:di")

// === Server (JVM only) ===
include(":server:infra")
include(":server:transport")
include(":server:di")
include(":server:app")

// === UI (KMP) ===
include(":features")
include(":tgminiapp")
```

### 4.2 Примеры build.gradle.kts

#### domain/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
```

#### features/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(projects.domain)
            implementation(projects.api.client)
            implementation(projects.api.mapping)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
```

#### server:infra/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.domain)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.json)
    implementation(libs.flyway.core)
    implementation(libs.ktor.client.cio)
}
```

### 4.3 Версии (libs.versions.toml)

```toml
[versions]
kotlin = "2.3.0"
ktor = "3.1.0"
exposed = "1.0.0"
koin = "4.1.0"
serialization = "1.8.0"
coroutines = "1.10.0"
arrow = "2.0.0"
kotest = "6.0.0"
logback = "1.5.0"
flyway = "10.0.0"
compose = "1.7.0"

[libraries]
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-json = { module = "org.jetbrains.exposed:exposed-json", version.ref = "exposed" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-ktor = { module = "io.insert-koin:koin-ktor", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
kotest-framework-engine = { module = "io.kotest:kotest-framework-engine", version.ref = "kotest" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotest = { id = "io.kotest", version.ref = "kotest" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.0-1.0.30" }
```

---

## 5. Потоки данных

### 5.1 Preview flow

Preview — это **диалог** между фронтом и бекендом. Пользователь может уточнять категорию и поля —
каждое уточнение повторно вызывает `POST /preview` с `overrides`.
VideoInfo кэшируется в PostgreSQL — yt-dlp вызывается однократно.

```
┌─────────┐  POST /api/v1/workspaces/{slug}/preview  ┌─────────────────┐
│  Mini   │ ──────────────────────────────────────▶ │ server:transport│
│   App   │  { url, overrides? }                    │  (Ktor route)   │
└─────────┘                                         └────────┬────────┘
                                                             │
                                                             ▼
                                                    ┌────────────────┐
                                                    │ PreviewUseCase │
                                                    │    (domain)    │
                                                    └────────┬───────┘
                                                             │
                    ┌────────────────────────────────────────┼──────────────────┐
                    │                                        │                  │
                    ▼                                        ▼                  ▼
           ┌────────────────┐                       ┌──────────────┐   ┌──────────────┐
           │ VideoInfoCache │                       │RuleMatching  │   │MetadataResolv│
           │  (PostgreSQL)  │                       │  Service     │   │ + LlmPort    │
           │ cache hit →    │                       │ (overrides)  │   │              │
           │ skip yt-dlp    │                       └──────────────┘   └──────────────┘
           │ cache miss →   │
           │ yt-dlp extract │
           └────────────────┘
```

Подробнее: [ADR/007-interactive-preview-refinement.md](./ADR/007-interactive-preview-refinement.md)

### 5.2 Job execution flow

```
JobScheduler (polls QUEUED)
       │
       ▼
JobProcessor
       │
       ├──▶ YtDlpDownloader.download()  (+ proxy, + thumbnail)
       │         │
       │         ▼
       │    downloaded file (webm/mkv — макс. качество)
       │
       ├──▶ Process original output (storagePlan.original):
       │         create directories
       │         move → original.path (rename, resolving actual filename from yt-dlp)
       │         embedMetadata? → ffmpeg embed tags (title, artist, album, ...)
       │         embedThumbnail? → ffmpeg embed cover art
       │
       ├──▶ for each additional in storagePlan.additional:
       │         check ConversionKey (format + maxQuality + encodeSettings + embed flags)
       │         if same key as previous output → file copy (skip ffmpeg)
       │         else:
       │           when (additional.format) {
       │             OriginalVideo  → copy from original
       │             ConvertedVideo → ffprobe source height
       │                              if sourceHeight ≤ maxHeight → remux (-c copy)
       │                              else → transcode (VideoEncodeSettings: codec, crf, preset, hwAccel)
       │             Audio          → ffmpeg extract audio
       │             Thumbnail      → (planned)
       │           }
       │           embedMetadata? → ffmpeg embed tags
       │           embedThumbnail? → ffmpeg embed cover art (mjpeg for MP4)
       │
       └──▶ JobRepository.updateStatus(COMPLETED)
```

> **Оптимизации**:
> - **ConversionKey deduplication**: если несколько output имеют одинаковые параметры конвертации
>   (формат, качество, encodeSettings, embed-флаги), первый полностью конвертируется,
>   последующие — просто копируются. Исключает повторный запуск ffmpeg.
> - **Smart transcoding**: перед перекодированием `ffprobe` определяет реальное разрешение источника.
>   Если оно ≤ `maxQuality` — выполняется только ремуксинг (`-c:v copy`), что несравнимо быстрее.
>
> **VideoEncodeSettings** (per-output настройки):
> - `codec`: H264, H265, VP9, AV1
> - `hwAccel`: VideoToolbox (macOS), NVENC (NVIDIA), QSV (Intel), VA-API, AMF (AMD)
> - `preset`: ultrafast → veryslow (только для SW кодеков)
> - `crf`: 0–51 (23 = YouTube-like, 18 = высокое качество)
> - `audioBitrate`: 96k, 128k, 192k, 256k, 320k

---

## 6. Расширяемость

### 6.1 Добавление новой UI-платформы

1. Создать новый shell-модуль (`:desktopapp`, `:androidapp`, `:webapp`)
2. Зависимость: `features`, `api:client:di`
3. Реализовать platform-specific glue (entry point, DI setup)
4. Все экраны и компоненты уже готовы в `features`

### 6.2 Добавление новой категории

1. Добавить в `enum Category` (domain, commonMain)
2. Добавить sealed subclass в `ResolvedMetadata` (domain)
3. Добавить sealed subclass в `MetadataTemplate` (domain)
4. Добавить sealed subclass в `ResolvedMetadataDto` (api:contract)
5. Добавить sealed subclass в `MetadataTemplateDto` (api:contract)
6. Добавить маппинг (api:mapping)
7. Обновить `MetadataResolver` (domain)
8. Обновить UI (features)

### 6.3 Добавление нового типа матчинга

1. Добавить sealed subclass в `RuleMatch` (domain)
2. Обновить `matches(ctx: MatchContext)` (domain)
3. Обновить `matchSpecificity()` (domain)
4. Добавить sealed subclass в `RuleMatchDto` (api:contract)
5. Добавить sealed subclass в `RuleMatchPm` (server:infra)
6. Добавить маппинг domain ↔ DTO ↔ Pm (api:mapping + server:infra)
7. Обновить Arb.ruleMatch() генератор (domain-test-fixtures)
8. Обновить UI rule editor (features)
