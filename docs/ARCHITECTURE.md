# Architecture

> **Purpose**: Describes the module structure, dependencies, KMP strategy, and design principles.

---

## 1. Principles

### 1.1 Clean Architecture

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

**Dependency rule**: inner layers have no knowledge of outer layers.

### 1.2 Kotlin Multiplatform

The project uses **Kotlin Multiplatform (KMP)** to share code between the server (JVM), Telegram Mini App (JS), and future native clients.

| Module             | Kotlin Plugin   | Targets     | Rationale                                              |
|--------------------|-----------------|-------------|--------------------------------------------------------|
| `domain`           | `multiplatform` | `jvm`, `js` | Domain models shared between server and clients        |
| `api:contract`     | `multiplatform` | `jvm`, `js` | DTOs shared via kotlinx.serialization                  |
| `api:mapping`      | `multiplatform` | `jvm`, `js` | Mapping needed on both server and in features          |
| `api:client`       | `multiplatform` | `jvm`, `js` | HTTP client works on both platforms                    |
| `api:client:di`    | `multiplatform` | `jvm`, `js` | Koin modules for wiring the client on each platform    |
| `features`         | `multiplatform` | `jvm`, `js` | Compose UI shared between shell applications           |
| `tgminiapp`        | `multiplatform` | `js`        | Telegram-specific shell, browser only                  |
| `server:infra`     | `jvm`           | `jvm`       | DB, processes — JVM-only                               |
| `server:transport` | `jvm`           | `jvm`       | Ktor Server — JVM-only                                 |
| `server:di`        | `jvm`           | `jvm`       | Server DI wiring                                       |
| `server:app`       | `jvm`           | `jvm`       | Entrypoint, JVM-only                                   |

### 1.3 Kotlin Idioms

- **Sealed classes/interfaces** for polymorphic types (RuleMatch, ResolvedMetadata, MetadataTemplate, UserOverrides, OutputFormat, DomainError)
- **Data classes** for DTOs and value objects
- **Value classes** for typesafe identifiers and domain primitives (KMP-compatible with Kotlin 2.1+)
- **Extension properties** for cheap computed values (e.g., `ResolvedMetadata.category`)
- **Coroutines** for async operations
- **Either** (Arrow) for error handling without exceptions

### 1.4 Contract-First

- The API contract (`api:contract`) is defined before implementation
- DTOs are stable and versioned
- Changes via `/api/v2/...` or new optional fields

---

## 2. Modules

### 2.1 Dependency Graph

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

### 2.2 Module Descriptions

#### `domain` — KMP (jvm, js)

**Purpose**: Business logic, pure Kotlin. Core of the application.

**Organization**: Package-by-feature (not by technical layers).

**Contains**:
- `common/` — Value objects (`VideoId`, `RuleId`, `JobId`, `WorkspaceId`, `ChannelDirectoryEntryId`, `Url`, `FilePath`, `LocalDate`, `Extractor`, `Tag`), `Category`, `DomainError`
- `workspace/` — `Workspace`, `WorkspaceMember`, `WorkspaceRole`, `WorkspaceRepository` port
- `channel/` — `Channel` (channel directory), `ChannelRepository` port
- `video/` — `VideoSource`, `VideoInfo`, `VideoInfoExtractor` port, `VideoInfoCache` port
- `rule/` — `Rule`, `RuleMatch` (sealed, incl. `HasTag`, `CategoryEquals`), `MatchContext`, `MatchResult`, `RuleMatchingService`, `RuleRepository` port
- `metadata/` — `ResolvedMetadata` (sealed), `MetadataTemplate` (sealed), `MetadataResolver`, `MetadataTemplateMerger`, `LlmPort`
- `storage/` — `StoragePlan`, `OutputRule`, `OutputFormat` (sealed), `PathTemplateEngine`, `VideoDownloader` port
- `job/` — `Job`, `JobStatus`, `CreateJobUseCase`, `JobRepository` port
- `preview/` — `UserOverrides` (sealed), `PreviewUseCase` (orchestrator)

**Dependencies**: Kotlin stdlib (`kotlin.time.Instant`, `kotlin.time.Duration`, `kotlin.uuid.Uuid`), Arrow (Either), kotlinx-coroutines.

**Does NOT contain**: Ktor, kotlinx.serialization, DB, filesystem.

```
domain/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── common/         # Value objects (VideoId, WorkspaceId, Tag, Url, FilePath, LocalDate, Extractor...), Category, DomainError
├── workspace/      # Workspace, WorkspaceMember, WorkspaceRole, WorkspaceRepository port
├── channel/        # Channel (channel directory), ChannelRepository port
├── video/          # VideoSource, VideoInfo, VideoInfoExtractor port, VideoInfoCache port
├── rule/           # Rule, RuleMatch (sealed), MatchContext, MatchResult, matches.kt, RuleMatchingService, RuleRepository port
├── metadata/       # ResolvedMetadata (sealed), MetadataTemplate (sealed), MetadataTemplateMerger, MetadataResolver, LlmPort
├── storage/        # StoragePlan, OutputRule, OutputFormat (sealed), PathTemplateEngine, VideoDownloader port
├── job/            # Job, JobStatus, CreateJobUseCase, JobRepository port
└── preview/        # UserOverrides (sealed), PreviewUseCase
```

> Packages are organized without circular dependencies. Each package can be extracted into a separate Gradle module as the project grows.

---

#### `api:contract` — KMP (jvm, js)

**Purpose**: DTOs for the HTTP API (request/response).

**Contains**: Request/Response DTOs, Sealed DTOs with `type` discriminator, `ApiErrorDto`.

**Dependencies**: Kotlin stdlib, kotlinx.serialization.

---

#### `api:mapping` — KMP (jvm, js)

**Purpose**: Domain ↔ DTO conversion.

**Dependencies**: `domain`, `api:contract`, Arrow.

> Mapping is placed in a KMP module because it is used both on the server (`server:transport`) and on the client (`features`).

---

#### `api:client` — KMP (jvm, js)

**Purpose**: Typed HTTP client for UI and tests.

**Dependencies**: `api:contract`, Ktor Client.

---

#### `api:client:di` — KMP (jvm, js)

**Purpose**: Koin modules for wiring `api:client`.

**Contains**: Koin module with `TgVideoDownloaderClient` factory, platform-specific Ktor engine (expect/actual).

**Dependencies**: `api:client`, Koin core, Ktor Client engine.

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

**Purpose**: Reusable UI components (Compose Multiplatform).

**Contains**: Screens, components, state holders / ViewModels, navigation.

**Dependencies**: `domain`, `api:client`, `api:mapping`, Compose Multiplatform, Koin.

**Does NOT contain**: Platform-specific code (Telegram interop, Android Activity, etc.)

```
features/src/commonMain/kotlin/io/github/alelk/tgvd/features/
├── common/
│   ├── component/
│   │   ├── WorkspaceTopBar.kt       ← current workspace in TopBar, switch via bottom sheet
│   │   ├── CreateWorkspaceDialog.kt  ← dialog for creating a new workspace
│   │   ├── WorkspaceSelector.kt      ← dropdown for workspace selection
│   │   ├── SectionCard.kt
│   │   ├── ErrorCard.kt
│   │   └── InfoRow.kt
│   ├── persistence/
│   │   └── PreferencesStorage.kt     ← interface for persisting settings (KMP)
│   ├── state/
│   │   └── WorkspaceState.kt         ← shared state: workspaces + selectedWorkspace + persistence
│   └── theme/
├── navigation/
│   └── AppNavigation.kt              ← Scaffold with TopBar (workspace) + BottomBar (tabs)
├── download/
├── jobs/
├── rules/
├── settings/
└── di/
    └── FeaturesModule.kt
```

> This is the key module for multiplatform support. A new UI shell (web, macOS, Android) depends on `features` and only adds platform-specific glue.
>
> `PreferencesStorage` — KMP interface. Each shell provides its own implementation (JS → `localStorage`, Android → `SharedPreferences`, etc.)

---

#### `tgminiapp` — JS only (browser)

**Purpose**: Telegram Mini App shell (thin wrapper).

**Contains**: Main.kt, LocalStoragePreferences.kt, TelegramWebApp interop, DI wiring.

**Dependencies**: `features`, `api:client:di`, Compose HTML/Web runtime.

**Does NOT contain**: Business logic, screens, components — all in `features`.

**Persistence**: Implements `PreferencesStorage` via browser `localStorage`. The selected workspace is persisted between sessions.

> Future shells will follow the same pattern: `webapp` (JS), `desktopapp` (JVM), `androidapp` — all depending on `features`.

---

#### `server:infra` — JVM only

**Purpose**: Implementation of domain ports (DB, processes, FS, LLM).

**Contains**:
- `db/` — tables, repositories, persistence models, mappings
- `process/` — YtDlpRunner, FfmpegRunner, YtDlpServiceImpl
- `service/` — JobProcessor (background task handler)
- `config/` — configuration data classes

**JobProcessor** — background coroutine loop that:
1. Polls the DB for `PENDING` jobs (interval from `JobsConfig.pollIntervalMs`)
2. Limits concurrency via `Semaphore(maxConcurrentDownloads)`
3. Downloads video via `VideoDownloader.downloadWithProgress()` with progress updates
4. Updates job status: `PENDING → DOWNLOADING → COMPLETED / FAILED`
5. Starts/stops automatically with the Ktor Application lifecycle

**Dependencies**: `domain`, Exposed, Flyway, Ktor Client (JVM), kotlinx.serialization.

> `server:infra` does **not** depend on `api:contract` or `api:mapping`. JSONB columns use their own persistence models (`*Pm`); the domain ↔ DB mapping is fully isolated from the API contract.

---

#### `server:transport` — JVM only

**Purpose**: HTTP layer (Ktor Server routing).

**Dependencies**: `domain`, `api:contract`, `api:mapping`, Ktor Server.

---

#### `server:di` — JVM only

**Purpose**: Dependency injection wiring for server modules.

**Dependencies**: `domain`, `server:infra`, `server:transport`, Koin.

---

#### `server:app` — JVM only

**Purpose**: Entrypoint, server application assembly.

**Dependencies**: All server modules.

---

### 2.3 Dependency Rules

| Module             | May depend on                                          | Must NOT depend on             |
|--------------------|--------------------------------------------------------|--------------------------------|
| `domain`           | Kotlin stdlib, Arrow, kotlinx-coroutines               | Everything else                |
| `api:contract`     | Kotlin stdlib, kotlinx.serialization                   | domain, server:*, features     |
| `api:mapping`      | domain, api:contract, Arrow                            | server:*, api:client, features |
| `api:client`       | api:contract, Ktor Client                              | domain, server:*, features     |
| `api:client:di`    | api:client, Koin, Ktor engine                          | domain, server:*, features     |
| `features`         | domain, api:client, api:mapping, Compose, Koin         | server:*, tgminiapp            |
| `tgminiapp`        | features, api:client:di                                | server:*, domain directly      |
| `server:infra`     | domain                                                 | api:*, transport, di, app      |
| `server:transport` | domain, api:contract, api:mapping, Ktor Server         | infra, di, app, features       |
| `server:di`        | domain, server:infra, server:transport, Koin           | api:*, app, features           |
| `server:app`       | domain, api:contract, server:*, Hoplite, Netty         | api:mapping, features          |

---

## 3. Coding Principles

### 3.1 Error Handling

**In domain** (`commonMain`): `Either<DomainError, T>` (no exceptions).

**In transport** (JVM): Catch `DomainError`, map to HTTP status + `ApiErrorDto`.

### 3.2 Async

- All I/O operations — `suspend fun`
- `kotlinx-coroutines` used across all KMP modules
- Job execution — `CoroutineDispatcher` from DI

### 3.3 Configuration

- Hoplite for loading YAML/env (only `server:app`, JVM)
- Data classes for config

### 3.4 KMP Source Set Conventions

All reusable code in `commonMain`. Platform-specific code via `expect/actual`.

---

## 4. Gradle Modules

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

### 4.2 build.gradle.kts Examples

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

### 4.3 Versions (libs.versions.toml)

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

## 5. Data Flows

### 5.1 Preview Flow

Preview is a **dialogue** between the frontend and backend. The user can refine the category and metadata fields —
each refinement re-invokes `POST /preview` with `overrides`.
VideoInfo is cached in PostgreSQL — yt-dlp is called only once.

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

See also: [ADR/007-interactive-preview-refinement.md](./ADR/007-interactive-preview-refinement.md)

### 5.2 Job Execution Flow

```
JobScheduler (polls QUEUED)
       │
       ▼
JobProcessor
       │
       ├──▶ YtDlpDownloader.download()  (+ proxy, + thumbnail)
       │         │
       │         ▼
       │    downloaded file (webm/mkv — maximum quality)
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

> **Optimizations**:
> - **ConversionKey deduplication**: if multiple outputs share the same conversion parameters
>   (format, quality, encodeSettings, embed flags), the first is fully converted
>   and subsequent outputs are simply copied — eliminating redundant ffmpeg invocations.
> - **Smart transcoding**: before encoding, `ffprobe` determines the actual source resolution.
>   If it is ≤ `maxQuality`, only remuxing (`-c:v copy`) is performed, which is significantly faster.
>
> **VideoEncodeSettings** (per-output settings):
> - `codec`: H264, H265, VP9, AV1
> - `hwAccel`: VideoToolbox (macOS), NVENC (NVIDIA), QSV (Intel), VA-API, AMF (AMD)
> - `preset`: ultrafast → veryslow (software codecs only)
> - `crf`: 0–51 (23 = YouTube-like quality, 18 = high quality)
> - `audioBitrate`: 96k, 128k, 192k, 256k, 320k

---

## 6. Extensibility

### 6.1 Adding a New UI Platform

1. Create a new shell module (`:desktopapp`, `:androidapp`, `:webapp`)
2. Depend on: `features`, `api:client:di`
3. Implement platform-specific glue (entry point, DI setup)
4. All screens and components are already available in `features`

### 6.2 Adding a New Category

1. Add to `enum Category` (domain, commonMain)
2. Add sealed subclass to `ResolvedMetadata` (domain)
3. Add sealed subclass to `MetadataTemplate` (domain)
4. Add sealed subclass to `ResolvedMetadataDto` (api:contract)
5. Add sealed subclass to `MetadataTemplateDto` (api:contract)
6. Add mapping (api:mapping)
7. Update `MetadataResolver` (domain)
8. Update UI (features)

### 6.3 Adding a New Match Type

1. Add sealed subclass to `RuleMatch` (domain)
2. Update `matches(ctx: MatchContext)` (domain)
3. Update `matchSpecificity()` (domain)
4. Add sealed subclass to `RuleMatchDto` (api:contract)
5. Add sealed subclass to `RuleMatchPm` (server:infra)
6. Add mapping domain ↔ DTO ↔ Pm (api:mapping + server:infra)
7. Update `Arb.ruleMatch()` generator (domain-test-fixtures)
8. Update UI rule editor (features)
