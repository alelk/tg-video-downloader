---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ PROJECT_CONTEXT.md, ../AGENTS.md, ADR/009-engineering-skills-baseline.md ]
---

# Architecture

> **Purpose**: Module structure, KMP strategy, dependency rules, and design principles.

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

### 1.2 Kotlin Multiplatform (KMP)

The project uses **Kotlin Multiplatform** to share code between the server (JVM), the Telegram Mini App (JS), and future native clients.

| Module             | Kotlin Plugin   | Targets     | Rationale                                              |
|--------------------|-----------------|-------------|--------------------------------------------------------|
| `domain`           | `multiplatform` | `jvm`, `js` | Domain models shared between server and clients        |
| `api:contract`     | `multiplatform` | `jvm`, `js` | DTOs shared via kotlinx.serialization                  |
| `api:mapping`      | `multiplatform` | `jvm`, `js` | Mapping needed on both server and in features          |
| `api:client`       | `multiplatform` | `jvm`, `js` | HTTP client works on both platforms                    |
| `features`         | `multiplatform` | `jvm`, `js` | Compose UI shared between shell applications           |
| `tgminiapp`        | `multiplatform` | `js`        | Telegram-specific shell, browser only                  |
| `server:infra`     | `jvm`           | `jvm`       | DB, processes — JVM-only                               |
| `server:transport` | `jvm`           | `jvm`       | Ktor Server — JVM-only                                 |
| `server:di`        | `jvm`           | `jvm`       | Server-side DI wiring                                  |
| `server:app`       | `jvm`           | `jvm`       | Entrypoint, JVM-only                                   |

### 1.3 Kotlin Idioms

- **Sealed classes/interfaces** for polymorphic types (`RuleMatch`, `ResolvedMetadata`, `MetadataTemplate`, `UserOverrides`, `OutputFormat`, `DomainError`)
- **Data classes** for DTOs and value objects
- **Value classes** for type-safe identifiers and domain primitives (KMP-compatible since Kotlin 2.1+)
- **Extension properties** for cheap computed values (e.g. `ResolvedMetadata.category`)
- **Coroutines** for async operations
- **Either** (Arrow) for error handling without exceptions

### 1.4 Contract-First

- The API contract (`api:contract`) is defined before the implementation
- DTOs are stable and versioned
- Breaking changes go through `/api/v2/...` or new optional fields

---

## 2. Modules

### 2.1 Dependency Diagram

```
Client side (KMP):
  tgminiapp ──▶ features, api:client, api:contract
  features  ──▶ domain, api:client, api:contract
  api:client ─▶ api:contract
  api:mapping ▶ domain, api:contract

Server side (JVM):
  server:app ───────▶ server:di, server:transport, server:infra, domain, api:contract
  server:di ────────▶ server:transport, server:infra, domain
  server:transport ─▶ domain, api:contract, api:mapping   (+ server:infra today, see note)
  server:infra ─────▶ domain

Test support:
  domain:domain-test-fixtures ─▶ domain   (used by commonTest of domain)
```

> Note: `server:transport` currently depends on `server:infra` — a violation of the rule in §2.3,
> removed in Step 01 (stage 01.7, [`plans/step-01/`](plans/step-01/README.md)).

### 2.2 Module Descriptions

#### `domain` — KMP (jvm, js)

**Purpose**: Business logic, pure Kotlin. The core of the application.

**Organization**: Package-by-feature (not by technical layers).

**Contains**:
- `common/` — `Category`, `DomainError`, `Tag`, value objects (`WorkspaceId`, `JobId`, etc.)
- `workspace/` — `Workspace`, `WorkspaceMember`, `WorkspaceRole`, `WorkspaceRepository` port, `CreateWorkspaceUseCase`, `AddWorkspaceMemberUseCase`, `RemoveWorkspaceMemberUseCase`
- `channel/` — `Channel`, `ChannelRepository` port, `CreateChannelUseCase`, `UpdateChannelUseCase`, `DeleteChannelUseCase`, request models
- `video/` — `VideoSource`, `VideoInfo`, `VideoInfoExtractor` port, `VideoInfoCache` port, `VideoDownloader` port
- `rule/` — `Rule`, `RuleMatch` (sealed, incl. `HasTag`, `CategoryEquals`), `MatchContext`, `MatchResult`, `RuleMatchingService`, `RuleRepository` port, `CreateRuleUseCase`, `UpdateRuleUseCase`, `DeleteRuleUseCase`, request models
- `metadata/` — `ResolvedMetadata` (sealed), `MetadataTemplate` (sealed), `MetadataTemplateMerger`, `MetadataResolver`, `LlmPort`
- `storage/` — `StoragePlan`, `OutputRule`, `OutputFormat` (sealed), `PathTemplateEngine`, `VideoDownloader` port, `validateStoragePaths()`
- `job/` — `Job`, `JobStatus`, `CreateJobUseCase`, `CancelJobUseCase`, `RetryJobUseCase`, `JobRepository` port, `CreateJobRequest` + `toJob()`
- `preview/` — `UserOverrides` (sealed), `PreviewUseCase` (orchestrator)
- `tx/` — `TransactionRunner`, `RoTransactionScope`, `RwTransactionScope`, `NoopTransactionRunner`

```
├── common/         # Shared types: Category, DomainError, Tag, value objects
├── workspace/      # Workspace, WorkspaceRepository port + CreateWorkspaceUseCase, AddWorkspaceMemberUseCase, RemoveWorkspaceMemberUseCase
├── channel/        # Channel, ChannelRepository port + CreateChannelUseCase, UpdateChannelUseCase, DeleteChannelUseCase
├── video/          # VideoSource, VideoInfo, VideoInfoExtractor port, VideoInfoCache port, VideoDownloader port
├── rule/           # Rule, RuleMatch (sealed), RuleMatchingService, RuleRepository port + CreateRuleUseCase, UpdateRuleUseCase, DeleteRuleUseCase
├── metadata/       # ResolvedMetadata (sealed), MetadataTemplate (sealed), MetadataResolver, LlmPort
├── storage/        # StoragePlan, OutputRule, OutputFormat (sealed), PathTemplateEngine, validateStoragePaths()
├── job/            # Job, JobStatus, JobRepository port + CreateJobUseCase, CancelJobUseCase, RetryJobUseCase
├── preview/        # UserOverrides (sealed), PreviewUseCase
└── tx/             # TransactionRunner, RoTransactionScope, RwTransactionScope, NoopTransactionRunner
```

**Dependencies**: Kotlin stdlib (`kotlin.time.Instant`, `kotlin.time.Duration`, `kotlin.uuid.Uuid`), Arrow (Either), kotlinx-coroutines.

**Does NOT contain**: Ktor, kotlinx.serialization, database, filesystem.

> Packages are organized without circular dependencies. Each package can be extracted into a separate Gradle module as the project grows.

---

#### `api:contract` — KMP (jvm, js)

**Purpose**: DTOs for the HTTP API (request/response).

**Contains**: Request/Response DTOs, sealed DTOs with `type` discriminator, `ApiErrorDto`.

**Dependencies**: Kotlin stdlib, kotlinx.serialization.

---

#### `api:mapping` — KMP (jvm, js)

**Purpose**: Domain ↔ DTO conversion.

**Dependencies**: `domain`, `api:contract`, Arrow.

> Mapping lives in a KMP module because it is used on both the server (`server:transport`) and the client (`features`).

---

#### `api:client` — KMP (jvm, js)

**Purpose**: Typed HTTP client for UI and tests.

**Dependencies**: `api:contract`, Ktor Client.

---

> There is no `api:client:di` module. The Koin binding of `TgVideoDownloaderClient` (with the
> Ktor `Js` engine, base URL and `initData` provider) lives in the shell — `tgminiapp/Main.kt`.

---

#### `features` — KMP (jvm, js) + Compose Multiplatform

**Purpose**: Reusable UI components (Compose Multiplatform).

**Contains**: Screens, components, state holders / ViewModels, navigation.

**Dependencies**: `domain`, `api:contract`, `api:client`, Compose Multiplatform, Voyager, Koin.

**Does NOT contain**: Platform-specific code (Telegram interop, Android Activity, etc.)

```
├── common/
│   ├── component/
│   │   ├── WorkspaceTopBar.kt       ← current workspace in TopBar, switch via bottom sheet
│   │   ├── CreateWorkspaceDialog.kt ← dialog for creating a new workspace
│   │   ├── WorkspaceSelector.kt     ← dropdown for workspace selection
│   │   └── InfoRow.kt
│   ├── persistence/
│   │   ├── PreferencesStorage.kt    ← KMP interface for persisting settings
│   │   └── WorkspaceState.kt        ← shared state: workspaces + selectedWorkspace + persistence
│   └── theme/
├── navigation/
│   └── AppNavigation.kt             ← Scaffold with TopBar (workspace) + BottomBar (tabs)
├── download/
├── jobs/
├── rules/
├── settings/
└── di/
    └── FeaturesModule.kt
```

> This is the key module for multiplatform support. A new UI shell (web, macOS, Android) simply depends on `features` and adds only platform-specific glue.
>
> `PreferencesStorage` is a KMP interface. Each shell provides its own implementation (JS → `localStorage`, Android → `SharedPreferences`, etc.)

---

#### `tgminiapp` — JS only (browser)

**Purpose**: Telegram Mini App shell (thin wrapper).

**Contains**: `Main.kt`, `LocalStoragePreferences.kt`, TelegramWebApp interop, DI wiring (including the API client).

**Dependencies**: `features`, `api:contract`, `api:client`, Compose Multiplatform (web), Koin.

**Does NOT contain**: Business logic, screens, components — all of that lives in `features`.

**Persistence**: Implements `PreferencesStorage` via the browser's `localStorage`. The selected workspace is persisted across sessions.

> Future shells: `webapp` (JS), `desktopapp` (JVM), `androidapp` — all depending on `features`.

---

#### `server:infra` — JVM only

**Purpose**: Implementation of domain ports (DB, processes, filesystem). No LLM adapter exists yet — `LlmPort` has no implementation.

**Contains**:
- `db/` — tables, repositories, persistence models, mappings
- `process/` — `YtDlpRunner`, `FfmpegRunner`, `YtDlpServiceImpl`
- `service/` — `JobProcessor` (background job handler)
- `config/` — configuration data classes

**JobProcessor** — a background coroutine loop that:
1. Polls the DB for `PENDING` jobs (interval from `JobsConfig.pollIntervalMs`)
2. Limits concurrency via `Semaphore(maxConcurrentDownloads)`
3. Downloads video via `VideoDownloader.downloadWithProgress()` with progress updates
4. Updates job status: `PENDING → DOWNLOADING → POST_PROCESSING → COMPLETED / FAILED` (`CANCELLED` on user cancel)
5. Starts and stops automatically with the Ktor Application lifecycle

**Dependencies**: `domain`, Exposed, Flyway, Ktor Client (JVM), kotlinx.serialization.

> `server:infra` does **not** depend on `api:contract` or `api:mapping`. JSONB columns use their own persistence models (`*Pm`); domain ↔ DB mapping is fully isolated from the API contract.

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
| `features`         | domain, api:contract, api:client, Compose, Koin        | server:*                       |
| `tgminiapp`        | features, api:contract, api:client                     | server:*, domain directly      |
| `server:infra`     | domain                                                 | api:*, transport, di, app      |
| `server:transport` | domain, api:contract, api:mapping, Ktor Server         | infra, di, app, features       |
| `server:di`        | domain, server:infra, server:transport, Koin           | api:*, app, features           |

---

## 3. Coding Principles

### 3.1 Error Handling

- **In domain** (`commonMain`): `Either<DomainError, T>` — no exceptions for business errors.
- **In transport** (JVM): catch `DomainError`, map to HTTP status + `ApiErrorDto`.

See [ADR/004-error-handling.md](./ADR/004-error-handling.md).

### 3.2 Async

- All I/O operations are `suspend fun`
- `kotlinx-coroutines` is used in all KMP modules
- Job execution uses a `CoroutineDispatcher` from DI

### 3.3 Configuration

- Hoplite for loading YAML/env (only in `server:app`, JVM)
- Data classes for config

See [CONFIGURATION.md](./CONFIGURATION.md).

### 3.4 KMP Source Set Conventions

All reusable code goes in `commonMain`. Platform services are `commonMain` interfaces implemented in the shell and bound in its Koin module (e.g. `PreferencesStorage` → `LocalStoragePreferences`); the project has no hand-written `expect/actual`.

Do NOT use JVM-only classes in `commonMain`:
- `java.util.UUID` → `kotlin.uuid.Uuid`
- `java.time.Instant` → `kotlin.time.Instant`
- `java.time.Duration` → `kotlin.time.Duration`

---

## 4. Gradle Modules

### 4.1 settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

// === Domain (KMP) ===
include(":domain")
include(":domain:domain-test-fixtures")

// === API (KMP) ===
include(":api:contract")
include(":api:mapping")
include(":api:client")

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
}
```

### 4.3 Versions (libs.versions.toml)

All dependency and plugin versions live only in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml);
the product version lives only in [`app.version`](../app.version). This document does not copy them.

---

## 5. Data Flows

### 5.1 Preview Flow

Preview is an **interactive dialog** between the frontend and backend. The user can refine the category and metadata fields — each change re-invokes `POST /preview` with `overrides`. `VideoInfo` is cached in PostgreSQL — `yt-dlp` is called only once per URL.

```
┌─────────┐                                              ┌─────────────────┐
│  Mini   │ ───────────────────────────────────────────▶ │ server:transport│
│   App   │  { url, overrides? }                         │  (Ktor route)   │
└─────────┘                                              └────────┬────────┘
                                                                  │
                                                                  ▼
                                                         ┌────────────────┐
                                                         │ PreviewUseCase │
                                                         │    (domain)    │
                                                         └────────┬───────┘
                                                                  │
                      ┌───────────────────────────────────────────┼──────────────────┐
                      │                                           │                  │
                      ▼                                           ▼                  ▼
             ┌────────────────┐                         ┌──────────────┐   ┌──────────────┐
             │ VideoInfoCache │                         │RuleMatching  │   │MetadataResolv│
             │  (PostgreSQL)  │                         │  Service     │   │ + LlmPort    │
             │ cache hit →    │                         │ (overrides)  │   │              │
             │ skip yt-dlp    │                         └──────────────┘   └──────────────┘
             │ cache miss →   │
             │ yt-dlp extract │
             └────────────────┘
```

See [ADR/007-interactive-preview-refinement.md](./ADR/007-interactive-preview-refinement.md).

### 5.2 Job Execution Flow

```
JobProcessor.pollLoop (polls PENDING)
       │
       ▼
JobProcessor
       ├──▶ YtDlpDownloader.download()  (+ proxy, + thumbnail)
       │         │
       │         ▼
       │    downloaded file (webm/mkv — maximum quality)
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
> - **ConversionKey deduplication**: if multiple outputs share identical conversion parameters
>   (format, maxQuality, encodeSettings, embed flags), the first is fully converted and subsequent
>   ones are just copied. Eliminates redundant ffmpeg invocations.
> - **Smart transcoding**: before re-encoding, `ffprobe` determines the actual source resolution.
>   If it is ≤ `maxQuality`, only a remux (`-c:v copy`) is performed — much faster than full transcoding.
>
> **VideoEncodeSettings** (per-output settings):
> - `hwAccel`: VideoToolbox (macOS), NVENC (NVIDIA), QSV (Intel), VA-API, AMF (AMD)
> - `preset`: ultrafast → veryslow (software codecs only)
> - `crf`: 0–51 (23 = YouTube-like quality, 18 = high quality)
> - `audioBitrate`: 96k, 128k, 192k, 256k, 320k

---

## 6. Extensibility

### 6.1 Adding a New UI Platform

1. Create a new shell module (`:desktopapp`, `:androidapp`, `:webapp`)
2. Depend on: `features`, `api:client` (bind `TgVideoDownloaderClient` in the shell's Koin module)
3. Implement platform-specific glue (entry point, DI setup)
4. All screens and components are already in `features`

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
