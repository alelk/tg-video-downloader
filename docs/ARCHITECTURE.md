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

| Module             | Convention (§4.2)        | Targets     | Rationale                                              |
|--------------------|--------------------------|-------------|--------------------------------------------------------|
| `domain`           | `tgvd.kmp`               | `jvm`, `js` | Domain models shared between server and clients        |
| `api:contract`     | `tgvd.kmp.serialization` | `jvm`, `js` | DTOs shared via kotlinx.serialization                  |
| `api:mapping`      | `tgvd.kmp`               | `jvm`, `js` | Mapping needed on both server and in features          |
| `api:client`       | `tgvd.kmp.serialization` | `jvm`, `js` | HTTP client works on both platforms                    |
| `features`         | `tgvd.compose`           | `jvm`, `js` | Compose UI shared between shell applications           |
| `tgminiapp`        | `tgvd.compose.js`        | `js`        | Telegram-specific shell, browser only                  |
| `server:infra`     | `tgvd.jvm.serialization` | `jvm`       | DB, processes — JVM-only                               |
| `server:transport` | `tgvd.jvm.serialization` | `jvm`       | Ktor Server — JVM-only                                 |
| `server:di`        | `tgvd.jvm`               | `jvm`       | Server-side DI wiring                                  |
| `server:app`       | `tgvd.jvm.serialization` | `jvm`       | Entrypoint, JVM-only                                   |

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
  server:transport ─▶ domain, api:contract, api:mapping
  server:infra ─────▶ domain

Test support:
  domain:domain-test-fixtures ─▶ domain   (used by commonTest of domain)
```

> Since stage 01.7 `server:transport` does not depend on `server:infra` and no route injects a
> repository; both rules are fitness tests (`TransportSourceGuardTest` in `server:transport`,
> `DomainPurityTest` in `domain` for the purity of `domain/commonMain`).

### 2.2 Module Descriptions

#### `domain` — KMP (jvm, js)

**Purpose**: Business logic, pure Kotlin. The core of the application.

**Organization**: Package-by-feature (not by technical layers).

**Contains**:
- `common/` — `Category`, `DomainError`, `Tag`, value objects (`WorkspaceId`, `JobId`, etc.)
- `workspace/` — `Workspace`, `WorkspaceMember`, `WorkspaceRole`, `WorkspaceRepository` port, `ListWorkspacesUseCase`, `CreateWorkspaceUseCase`, `ListWorkspaceMembersUseCase`, `AddWorkspaceMemberUseCase`, `RemoveWorkspaceMemberUseCase`, `WorkspaceAccess` (membership check)
- `channel/` — `Channel`, `ChannelRepository` port, `ListChannelsUseCase` (`ChannelFilter`), `ListChannelTagsUseCase`, `GetChannelUseCase`, `CreateChannelUseCase`, `UpdateChannelUseCase`, `DeleteChannelUseCase`, request models
- `video/` — `VideoSource`, `VideoInfo`, `VideoInfoExtractor` port, `VideoInfoCache` port, `VideoDownloader` port
- `rule/` — `Rule`, `RuleMatch` (sealed, incl. `HasTag`, `CategoryEquals`), `MatchContext`, `MatchResult`, `RuleMatchingService`, `RuleRepository` port, `ListRulesUseCase`, `GetRuleUseCase`, `CreateRuleUseCase`, `UpdateRuleUseCase`, `DeleteRuleUseCase`, request models
- `metadata/` — `ResolvedMetadata` (sealed), `MetadataTemplate` (sealed), `MetadataTemplateMerger`, `MetadataResolver`, `LlmPort` (never nullable: `UnconfiguredLlmPort` in infra when no LLM)
- `storage/` — `StoragePlan`, `OutputRule`, `OutputFormat` (sealed), `PathTemplateEngine`, `VideoDownloader` port, `validateStoragePaths()`
- `job/` — `Job`, `JobStatus`, `CreateJobUseCase` (validation + `saveAsRule`), `ListJobsUseCase`, `GetJobUseCase`, `CancelJobUseCase`, `RetryJobUseCase`, `JobRepository` port, `CreateJobRequest` + `toJob()`
- `preview/` — `UserOverrides` (sealed), `PreviewUseCase` (orchestrator), `PreviewVideoUseCase` (what `POST …/preview` returns)
- `track/` — `AudioTrackSelector`, `SubtitleSelector`, `TrackSelectionSettings`, `TrackSelectionSettingsProvider` port
- `system/` — `SystemSettings` + `SystemSettingsStore` port, `Get/UpdateSystemSettingsUseCase`, `YtDlpService` port, `GetYtDlpStatusUseCase`, `UpdateYtDlpUseCase`, `ReadinessProbe` port
- `tx/` — `TransactionRunner`, `RoTransactionScope`, `RwTransactionScope` (the test-only `NoopTransactionRunner` is in `domain-test-fixtures`)

```
├── common/         # Shared types: Category, DomainError, Tag, value objects
├── workspace/      # Workspace, WorkspaceRepository port, WorkspaceAccess + List/Create workspace, List/Add/Remove member use-cases
├── channel/        # Channel, ChannelRepository port + List/ListTags/Get/Create/Update/Delete channel use-cases
├── video/          # VideoSource, VideoInfo, VideoInfoExtractor port, VideoInfoCache port, VideoDownloader port
├── rule/           # Rule, RuleMatch (sealed), RuleMatchingService, RuleRepository port + List/Get/Create/Update/Delete rule use-cases
├── metadata/       # ResolvedMetadata (sealed), MetadataTemplate (sealed), MetadataResolver, LlmPort
├── storage/        # StoragePlan, OutputRule, OutputFormat (sealed), PathTemplateEngine, validateStoragePaths()
├── job/            # Job, JobStatus, JobRepository port + CreateJobUseCase, ListJobsUseCase, GetJobUseCase, CancelJobUseCase, RetryJobUseCase
├── preview/        # UserOverrides (sealed), PreviewUseCase, PreviewVideoUseCase
├── track/          # AudioTrackSelector, SubtitleSelector, TrackSelectionSettings (+ provider port)
├── system/         # SystemSettings + SystemSettingsStore port, yt-dlp status/update use-cases, ReadinessProbe
└── tx/             # TransactionRunner, RoTransactionScope, RwTransactionScope
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

**Dependencies**: `api:contract`, Ktor Client, Arrow.

Every `TgVideoDownloaderClient` method returns `Either<ApiError, T>` and never throws (except coroutine
cancellation). `ApiError` is sealed: `Http(status, code, message, correlationId)` for a non-2xx response
(from `ApiErrorDto`, or `HTTP_<status>` when the body is not one), `Network` when no response arrived
(including the Ktor JS engine's `kotlin.Error("Fail to fetch")`), `Decoding` for an undecodable 2xx body.
The single conversion point is `ApiCall.kt`; screens `fold` the result (no `try/catch`, no `runCatching`).

---

> There is no `api:client:di` module. The Koin binding of `TgVideoDownloaderClient` (with the
> Ktor `Js` engine, base URL and `initData` provider) lives in the shell — `tgminiapp/Main.kt`.

---

#### `features` — KMP (jvm, js) + Compose Multiplatform

**Purpose**: Reusable UI components (Compose Multiplatform).

**Contains**: Screens, components, state holders, navigation, and the app root
(`app/TgvdApp.kt` — theme + `WorkspaceGate` + navigation; `app/WorkspaceGate.kt` — loads or creates the
workspace before the UI renders).

**Screen state (G12, Fork 3).** Voyager stays; a screen's state holder is a Voyager `ScreenModel` built by
Koin (`koinScreenModel`, factories in `di/FeaturesModule.kt`) on the base `common/FeatureScreenModel.kt`: one
`StateFlow` state implementing `common/Async.kt` (`loading` / `busy` / `error`), one-shot effects through a
`Channel`, one `onEvent`, and `runRequest`/`launchRequest` that lower the in-flight flag on success, failure
and cancellation. A screen is split into `XxxEntry` (acquires the model, collects state, turns effects into
navigation), `XxxContent` (pure render of the state + `onEvent` + navigation lambdas), `XxxScreenModel`,
`XxxUiState` (screen models mapped from the DTOs) / `XxxEvent`, and section files. Preview
(`download/screen/Preview*`) and Settings (`settings/screen/Settings*`, form model `settings/model/SettingsForm.kt`)
follow it; the other screens still call the client from composition and are listed in the
`UiConventionsTest` ratchet (`features/src/jvmTest`), which only shrinks.

**Dependencies**: `domain`, `api:contract`, `api:client`, Compose Multiplatform, Voyager, Koin.

**Does NOT contain**: Platform-specific code (Telegram interop, Android Activity, etc.)

```
├── app/
│   ├── TgvdApp.kt                   ← the root composable a shell renders
│   └── WorkspaceGate.kt             ← load/create workspace, restore selection, scope the client
├── common/
│   ├── Async.kt                     ← loading / busy / error — the fields every screen state shares
│   ├── FeatureScreenModel.kt        ← ScreenModel base: StateFlow state, Channel effects, runRequest
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
│   ├── model/                       ← PreviewEditorValues, media options (pure)
│   └── screen/                      ← UrlInputScreen; PreviewScreen (Voyager screen) → PreviewEntry →
│                                      PreviewContent + sections; PreviewScreenModel, PreviewUiState, PreviewEvent
├── jobs/
├── rules/
├── settings/
│   ├── model/SettingsForm.kt        ← editable fields ↔ SystemSettingsDto
│   └── screen/                      ← SettingsTab → SettingsEntry → SettingsContent + sections; SettingsScreenModel
└── di/
    └── FeaturesModule.kt            ← WorkspaceState, LocaleState, screen-model factories
```

> This is the key module for multiplatform support. A new UI shell (web, macOS, Android) simply depends on `features` and adds only platform-specific glue.
>
> `PreferencesStorage` is a KMP interface. Each shell provides its own implementation (JS → `localStorage`, Android → `SharedPreferences`, etc.)

---

#### `tgminiapp` — JS only (browser)

**Purpose**: Telegram Mini App shell (thin wrapper).

**Contains**: `Main.kt` (entry point, Koin modules incl. the API client, a single `TgvdApp(...)` call),
`LocalStoragePreferences.kt`, `TelegramTheme.kt` (Telegram colors → `TelegramThemeColors`),
`TelegramPlatform.kt` (`PlatformCallbacks`: haptics, deep-link URL, clipboard). `initData` is read on
every request through the client's provider.

**Dependencies**: `features`, `api:contract`, `api:client`, Compose Multiplatform (web), Koin.

**Does NOT contain**: Business logic, screens, components — all of that lives in `features`.
Guarded by `ShellSourceGuardTest` (`features/src/jvmTest`): no `@Composable` declarations and no Compose
foundation/material imports in `tgminiapp/src`.

**Persistence**: Implements `PreferencesStorage` via the browser's `localStorage`. The selected workspace is persisted across sessions.

> Future shells: `webapp` (JS), `desktopapp` (JVM), `androidapp` — all depending on `features`.

---

#### `server:infra` — JVM only

**Purpose**: Implementation of domain ports (DB, processes, filesystem). No LLM adapter exists yet — `LlmPort` is bound to `UnconfiguredLlmPort` (`llm/`), which refuses every suggestion, so previews fall back to `MetadataResolver`.

**Contains**:
- `db/` — tables, repositories, persistence models, mappings
- `process/` — `YtDlpRunner`, `FfmpegRunner`, `YtDlpServiceImpl`
- `service/` — `JobProcessor` (background job handler), `SystemSettingsHolder` (implements `SystemSettingsStore` and `TrackSelectionSettingsProvider`)
- `llm/` — `UnconfiguredLlmPort`
- `config/` — configuration data classes and their mapping to domain settings

**JobProcessor** — a background coroutine loop; its full lifecycle is §5.3. In short: at start it
returns interrupted jobs to the queue, then every `pollIntervalMs` stops the jobs that are no longer
processing (cancelled through the API) and claims pending jobs atomically while fewer than
`maxConcurrentDownloads` run; every status write is a compare-and-set; `stop()` requeues the running
jobs. yt-dlp and ffmpeg processes die with the job's coroutine (`process/CancellableProcess.kt`).

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
| `api:client`       | api:contract, Ktor Client, Arrow                       | domain, server:*, features     |
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

### 3.3 Transactions

- **Only `TransactionRunner` opens a transaction** (`ExposedTransactionRunner` in `server:infra`).
  Use-cases wrap each command/query in one `inRwTransaction {}` / `inRoTransaction {}`; background
  code outside a use-case (`JobProcessor`, the start-up load of `SystemSettingsHolder`) opens short
  transactions through the same runner. Repositories run in the transaction bound to the coroutine and
  never open one; without a transaction they fail.
- A runner called inside another joins the outer transaction.
- **A `Left` commits**: the runner commits whatever the block returns. Every check comes before the
  first write; a use-case never swallows a `DatabaseFailed` after a write (`CreateJobUseCase` fails the
  whole call when saving the "save as rule" rule hits a database error).
- Database failures: `catchingDb` rolls the transaction back and maps SQLSTATE to `DomainError`
  (`23505` on a known unique index → the existing conflict error, else `DatabaseFailed` → `500`).
- yt-dlp, ffmpeg, LLM and HTTP never run inside a transaction.

Details: [DATABASE.md §6–7](./DATABASE.md).

### 3.4 Configuration

- Hoplite for loading YAML/env (only in `server:app`, JVM)
- Data classes for config

See [CONFIGURATION.md](./CONFIGURATION.md).

### 3.5 KMP Source Set Conventions

All reusable code goes in `commonMain`. Platform services are `commonMain` interfaces implemented in the shell and bound in its Koin module (e.g. `PreferencesStorage` → `LocalStoragePreferences`); the project has no hand-written `expect/actual`.

Do NOT use JVM-only classes in `commonMain`:
- `java.util.UUID` → `kotlin.uuid.Uuid`
- `java.time.Instant` → `kotlin.time.Instant`
- `java.time.Duration` → `kotlin.time.Duration`

---

## 4. Gradle Modules

Build logic lives in the included build [`convention-plugins/`](../convention-plugins/)
(precompiled script plugins, id prefix `tgvd.`). A module's `plugins {}` block names one convention
and its build file lists only dependencies (plus module-specific bits such as the `features`
`BuildConfig` generator or the `tgminiapp` webpack output name). The root `build.gradle.kts` only
sets `group` and `version` (from `app.version`); repositories are declared once, in
`settings.gradle.kts`.

### 4.1 settings.gradle.kts

```kotlin
pluginManagement {
    includeBuild("convention-plugins")          // tgvd.* convention plugins
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // Node.js / Yarn distributions for Kotlin/JS (ivy), GitHub Packages for tg-mini-app,
        // mavenLocal() last and only for io.github.alelk — see the file itself
    }
}

// ../tg-mini-app checked out next to this repo → composite build instead of the Maven artifact
rootProject.name = "tg-video-downloader"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")   // projects.api.contract, never project(":…")

include(":domain", ":domain:domain-test-fixtures")
include(":api:contract", ":api:mapping", ":api:client")
include(":server:infra", ":server:transport", ":server:di", ":server:app")
include(":features", ":tgminiapp")
```

### 4.2 Convention plugins

| Plugin id                | Applies                                                        | Used by                                   |
|--------------------------|----------------------------------------------------------------|-------------------------------------------|
| `tgvd.kmp`               | Kotlin Multiplatform: `jvm()` + `js(IR) { browser() }`, JDK 21 toolchain, JUnit Platform for tests, JS test runner disabled, Detekt + ktlint | `domain`, `domain-test-fixtures`, `api:mapping` |
| `tgvd.kmp.serialization` | `tgvd.kmp` + kotlinx.serialization                             | `api:contract`, `api:client`              |
| `tgvd.compose`           | `tgvd.kmp` + Compose Multiplatform + Compose compiler + common Compose deps | `features`                     |
| `tgvd.compose.js`        | `js(IR) { browser() }` only + Compose + Compose compiler + serialization, Detekt + ktlint | `tgminiapp`                       |
| `tgvd.jvm`               | Kotlin/JVM, JDK 21 toolchain, JUnit Platform for tests, Detekt + ktlint | `server:di`                               |
| `tgvd.jvm.serialization` | `tgvd.jvm` + kotlinx.serialization                             | `server:infra`, `server:transport`, `server:app` |

- Kotlin-family and Compose plugins are applied **only** through these conventions (their markers
  are on the convention build's classpath). `server:app` applies Ktor and Shadow by id
  (`apply(plugin = …)`) from the same classpath, so the Kotlin Gradle plugin loads once.
- Kotest runs on the JVM through JUnit 5 (`kotest-runner-junit5` in `jvmTest`/`test`); the Kotest
  Gradle plugin and KSP are not used. The JS test runner is disabled; `compileTestKotlinJs` (part
  of `./gradlew build`) keeps `commonMain`/`commonTest` free of JVM-only APIs.
- The JDK toolchain version is `JVM_TOOLCHAIN_VERSION` in `convention-plugins/src/main/kotlin/BuildConventions.kt`.
- Detekt (profile `detekt.yml`) and ktlint (`.editorconfig`, `intellij_idea` style, 120 columns) run
  in `./gradlew build` over main **and** test sources of every module; the wiring is in
  `convention-plugins/src/main/kotlin/StaticAnalysis.kt`. Each module has `detekt-baseline.xml` and
  `ktlint-baseline.xml` with the findings that pre-date the gate: baselines only shrink, never
  regenerate one to absorb new findings. Generated sources are excluded from ktlint. A `@Suppress`
  carries a one-line reason.
- `:server:app:shadowJar` (→ `server/app/build/libs/tgvd-server.jar`) is **not** part of `build`;
  CI and the Dockerfiles call it explicitly.

#### domain/build.gradle.kts

```kotlin
plugins {
    id("tgvd.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.arrow.core)
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(projects.domain.domainTestFixtures)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner)       // kotest-runner-junit5
        }
    }
}
```

#### server/infra/build.gradle.kts

```kotlin
plugins {
    id("tgvd.jvm.serialization")
}

dependencies {
    api(projects.domain)
    api(libs.bundles.exposed)
    api(libs.flyway.core)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
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
JobProcessor.pollOnce ── JobRepository.claimNext()  (PENDING → DOWNLOADING, atomic)
       │
       ▼
JobProcessor.processJob
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
       └──▶ JobRepository.transition({DOWNLOADING, POST_PROCESSING} → COMPLETED)   (CAS)
```

Every repository call of `JobProcessor` is its own short transaction through `TransactionRunner`
(reads RO, writes RW); the download and ffmpeg run between them, outside any transaction.

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


### 5.3 Job lifecycle (Stage 01.9)

Status table (`domain/job/JobStatus.kt`, tested pair by pair in `JobStatusTest`):

| From              | May move to                                                           | Who                                   |
|-------------------|-----------------------------------------------------------------------|---------------------------------------|
| `PENDING`         | `DOWNLOADING`, `CANCELLED`                                            | claim (processor); `CancelJobUseCase` |
| `DOWNLOADING`     | `DOWNLOADING`, `POST_PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `PENDING` | processor; cancel; shutdown/restart   |
| `POST_PROCESSING` | `POST_PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED`, `PENDING`      | the same                              |
| `FAILED`          | `PENDING`                                                             | `RetryJobUseCase` (`attempt + 1`)     |
| `CANCELLED`       | `PENDING`                                                             | `RetryJobUseCase` (`attempt + 1`)     |
| `COMPLETED`       | —                                                                     |                                       |

Rules:

- **Every status write is a compare-and-set**: `JobRepository.transition(id, expected, to, patch)` is one
  `UPDATE … WHERE id = ? AND status IN (expected)`; no matching row → `JobStatusConflict` (use-cases
  report it as the existing `JobCannotBeCancelled`/`JobCannotBeRetried`, same HTTP answer). There is no
  unconditional status write (`updateStatus` is gone).
- **Claim**: `claimNext()` — `SELECT … WHERE status = 'pending' ORDER BY created_at LIMIT 1 FOR UPDATE
  SKIP LOCKED` + update of that row (`started_at`, phase `DOWNLOAD` 0 %); served by `idx_jobs_pending`
  (V3), no new migration. Two concurrent claims never get the same job.
- **Cancel**: only `CancelJobUseCase` writes `CANCELLED`. The processor notices it two ways: its next
  CAS write (progress, outcome) fails → the job's coroutine is cancelled and writes nothing more; or,
  for a silent download, the next poll reads the running jobs' statuses and cancels the coroutines of
  those no longer `DOWNLOADING`/`POST_PROCESSING`. Cancelling the coroutine terminates the yt-dlp/ffmpeg
  process tree: descendants and process get `SIGTERM`, up to 5 s, then `SIGKILL`; the stdout read loop
  ends because the pipe closes. Waiting for a process is `onExit().await()`, never a blocking `waitFor()`.
- **Stop** (`ApplicationStopping` → `runBlocking { withTimeout(15 s) { stop() } }`): no more claims; the
  running jobs are cancelled with `ShutdownCancellation`, given up to 10 s (`stop(grace)`), then — in
  `NonCancellable` — put back to `PENDING` by a CAS from the processing statuses (same `attempt`, progress
  cleared). The pool closes only on `ApplicationStopped`, after that.
- **Start**: before the first claim, `requeueInterrupted()` returns every `DOWNLOADING`/`POST_PROCESSING`
  row to `PENDING` with the same attempt (Fork 2) — a job interrupted by a crash or a `SIGKILL` is
  downloaded again, not stuck.
- **One server instance per database** is a deployment condition: the start-up recovery would requeue
  jobs a second live instance is processing. No automatic retries (`maxAttempts`/`retryDelayMs` unused).

Tests: `JobLifecycleTest` (server:app — the mine: cancel through the route mid-download, `stop()`
mid-download, a row left in `downloading`; proven red on an unconditional `UPDATE`),
`JobStatusWritesTest` (CAS, claim under concurrency, requeue), `ProcessCancellationTest` (the process
tree dies on cancellation).

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
