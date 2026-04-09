# AGENTS.md — Instructions for AI Agents

> **Purpose**: This is the primary instruction file for AI agents working with this project.
> Humans may read it too, but the main documentation lives in `docs/`.

---

## 🎯 Project Overview

**TG Video Downloader** — a self-hosted service for downloading videos from various platforms
(YouTube, RuTube, VK Video, and [1000+ others](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md))
managed through a Telegram Mini App. Supports LLM (Gemini/OpenAI) for metadata extraction and HTTP/SOCKS5 proxies.

**Stack**: Kotlin 2.3+ (Multiplatform), Ktor 3, Compose Multiplatform, PostgreSQL, yt-dlp.

---

## 📚 Where to Find Information

| What you need                   | Where to look                                        |
|---------------------------------|------------------------------------------------------|
| Project overview                | [`README.md`](./README.md)                           |
| Architecture, KMP, modules      | [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)     |
| Domain models (sealed classes)  | [`docs/DOMAIN.md`](./docs/DOMAIN.md)                 |
| HTTP API and DTOs               | [`docs/API_CONTRACT.md`](./docs/API_CONTRACT.md)     |
| Database                        | [`docs/DATABASE.md`](./docs/DATABASE.md)             |
| Configuration                   | [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md)   |
| Security and authorization      | [`docs/SECURITY.md`](./docs/SECURITY.md)             |
| Testing                         | [`docs/TESTING.md`](./docs/TESTING.md)               |
| Deployment                      | [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md)         |
| Architecture decisions          | [`docs/ADR/`](./docs/ADR/)                           |

---

## 🏗️ Module Structure

```
tg-video-downloader/
├── domain/              # Domain models, use-cases (KMP: jvm, js)
├── api/
│   ├── contract/        # HTTP API DTOs (KMP: jvm, js)
│   ├── mapping/         # Domain ↔ DTO mapping (KMP: jvm, js)
│   ├── client/          # Ktor HTTP client (KMP: jvm, js)
│   └── client/di/       # Koin modules for API client (KMP: jvm, js)
├── features/            # UI components, Compose Multiplatform (KMP: jvm, js)
├── tgminiapp/           # Telegram Mini App shell (JS only)
├── server/
│   ├── infra/           # Repositories, DB, yt-dlp, ffmpeg, LLM (JVM only)
│   ├── transport/       # Ktor routing, auth middleware (JVM only)
│   ├── di/              # Server Koin modules (JVM only)
│   └── app/             # Entrypoint, Application.kt (JVM only)
└── docs/                # Documentation
```

**KMP rule**: `domain`, `api:*`, `features` — Kotlin Multiplatform (jvm + js). `server:*` — JVM only. `tgminiapp` — JS only.

---

## ⚡ Key Principles

### 1. Kotlin Multiplatform

- All reusable code goes in the `commonMain` source set
- Platform-specific code via `expect/actual`
- `java.util.UUID` → `kotlin.uuid.Uuid` (Kotlin 2.0+)
- `java.time.*` → `kotlin.time.Instant` (in stdlib since Kotlin 2.1.20+)
- Do NOT use JVM-only classes in `commonMain`

### 2. Kotlin Idioms

- **Sealed classes** for polymorphic types (`RuleMatch`, `ResolvedMetadata`, `MetadataTemplate`, `UserOverrides`, `OutputFormat`, `DomainError`)
- **Value classes** for typesafe IDs and value objects (`VideoId`, `RuleId`, `JobId`, `ChannelDirectoryEntryId`, `Tag`, `Url`, `FilePath`, `LocalDate`, `Extractor`)
- **Data classes** for DTOs and value objects
- **Either<Error, T>** for error handling (Arrow)
- **Coroutines** for async operations
- **`val` (extension property)** for cheap computed values instead of `fun` with no arguments

### 3. Layer Separation

```
UI Shell (tgminiapp) → features (Compose)
                            ↓
              api:client → api:contract
                            ↓
              api:mapping → domain
                            ↓
              server:transport → server:infra (DB, yt-dlp, LLM, Proxy)
```

### 4. Contract-First & Workspace-Scoped API

- Define DTOs in `api:contract` before implementing
- Use `type` discriminator for sealed DTOs in JSON
- All domain resources are scoped to workspace: `/api/v1/workspaces/{workspaceId}/...`
- API versioning via `/api/v1/`, `/api/v2/`

---

## 📝 Implementation Guidelines

### Creating a new KMP module

1. Add to `settings.gradle.kts`
2. Use `kotlin("multiplatform")` plugin
3. Declare targets: `jvm()`, `js(IR) { browser() }`
4. All code in `commonMain`, platform-specific via `expect/actual`

### Adding a new type to a sealed hierarchy

1. Add to domain (`commonMain`)
2. Add to DTO (`api:contract`, `commonMain`) with `@SerialName`
3. Add mapping (`api:mapping`, `commonMain`)
4. Add tests (`commonTest`)
5. Update UI (`features`)

### Creating a new endpoint

1. Define DTO in `api:contract`
2. Add route in `server:transport`
3. Implement use-case in `domain` (if business logic is needed)
   - Inject `TransactionRunner`; use `inRwTransaction` for writes, `inRoTransaction` for reads
   - Expose a single `suspend operator fun invoke(...)` method
4. Add tests
5. Update [API_CONTRACT.md](./docs/API_CONTRACT.md)

### Working with errors

- In domain: return `Either<DomainError, T>`
- In mapping: return `Either<ValidationError, T>`
- In transport: map `DomainError` → HTTP status + `ApiErrorDto`
- Never use exceptions for business errors

### Use Case conventions

- One class = one use case
- Single public method: `suspend operator fun invoke(...)`  — call sites use `useCase(args)` syntax
- Always inject `TransactionRunner txRunner`; wrap business logic in `txRunner.inRwTransaction { }` or `txRunner.inRoTransaction { }`
- In tests: use `NoopTransactionRunner()` from `domain/tx`

---

## 🔑 Important Implementation Details

### LlmPort (Optional)

```kotlin
// domain/metadata/LlmPort.kt (commonMain)
interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}
```

Implementations (`GeminiLlmAdapter`, `OpenAiLlmAdapter`) live in `server:infra/llm/`.
Injected as nullable (`getOrNull()`). If LLM is not configured — `null`, fallback to `MetadataResolver`.

### Proxy

`ProxyConfig` is used in:
- `yt-dlp` → `--proxy` argument
- LLM HTTP client → `Ktor Client` engine proxy config

### Save as Rule

When creating a job (`POST /api/v1/jobs`), you can pass `saveAsRule`
to automatically create a rule for this channel from the current metadata.

### features → tgminiapp

`features` contains all Compose UI components. `tgminiapp` is a thin shell that:
- Initializes DI (Koin)
- Connects `features` screens
- Provides Telegram WebApp JS interop

---

## ✅ Pre-Commit Checklist

- [ ] Code compiles on all targets (`./gradlew build`)
- [ ] Tests pass (`./gradlew allTests`)
- [ ] New code in `commonMain` does not use JVM-only classes
- [ ] Documentation is updated
- [ ] No hardcoded secrets
- [ ] Follows principles from ADR

---

## 🚫 What NOT to Do

- ❌ Do not add JVM-only dependencies to `commonMain` of KMP modules
- ❌ Do not add Ktor/DB dependencies to `domain`
- ❌ Do not use exceptions for business errors
- ❌ Do not hardcode paths and configuration
- ❌ Do not log sensitive data (botToken, initData)
- ❌ Do not create circular dependencies between modules
- ❌ Do not place UI components in `tgminiapp` — only in `features`

---

## 📎 Quick Reference

- **Gradle commands**:
  - `./gradlew build` — full build of all modules
  - `./gradlew check` — all tests (commonTest + jvmTest + jsTest)
  - `./gradlew :server:app:run` — run the server
  - `./gradlew :tgminiapp:jsBrowserDevelopmentRun` — run the UI

- **Docker**:
  - `docker compose up -d postgres` — database only
  - `docker compose up -d` — everything

- **Useful files**:
  - `docs/ADR/` — architecture decision records
  - `gradle/libs.versions.toml` — dependency versions
