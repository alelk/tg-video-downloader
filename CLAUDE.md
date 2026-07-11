# CLAUDE.md — Claude Code Quick Reference

> Companion to [AGENTS.md](./AGENTS.md). Read AGENTS.md first for full architecture context.
> This file covers Claude Code-specific shortcuts, gotchas, and task recipes.

---

## Project in One Line

Self-hosted video downloader (yt-dlp backend) with a Telegram Mini App UI.
Stack: Kotlin 2.3 + Ktor 3 + Compose Multiplatform + PostgreSQL.

---

## Quick Commands

```bash
./gradlew build                          # full build (all targets)
./gradlew check                          # all tests (commonTest + jvmTest + jsTest)
./gradlew :server:app:run                # run server
./gradlew :tgminiapp:jsBrowserDevelopmentRun  # run UI (dev mode)
docker compose up -d postgres            # DB only
docker compose up -d                     # everything
```

---

## Module Map (Where to Find Things)

| What                          | Where                                           |
|-------------------------------|-------------------------------------------------|
| Domain models, use-cases      | `domain/src/commonMain/`                        |
| HTTP DTOs                     | `api/contract/src/commonMain/`                  |
| Domain ↔ DTO mapping          | `api/mapping/src/commonMain/`                   |
| Ktor HTTP client              | `api/client/src/commonMain/`                    |
| Compose UI screens/components | `features/src/commonMain/`                      |
| DB repositories, yt-dlp       | `server/infra/src/main/`                        |
| Ktor routes, auth middleware  | `server/transport/src/main/`                    |
| DI wiring (server)            | `server/di/src/main/`                           |
| Entrypoint                    | `server/app/src/main/`                          |
| Telegram Mini App shell       | `tgminiapp/src/jsMain/`                         |
| yt-dlp arg reference          | `docs/ai/yt-dlp-cheatsheet.md`                  |
| Dependency versions           | `gradle/libs.versions.toml`                     |
| DB migrations                 | `server/infra/src/main/resources/db/migration/` |

---

## Hard Rules (Compiler Won't Catch These)

1. **No JVM types in `commonMain`** — use `kotlin.uuid.Uuid`, `kotlin.time.Instant`, `kotlin.time.Duration`; custom
   `LocalDate`, `Url`, `FilePath` value classes from `domain/common/`
2. **No exceptions for business errors** — return `Either<DomainError, T>` from use-cases and mapping
3. **Transactions** — wrap writes in `txRunner.inRwTransaction {}`, reads in `inRoTransaction {}`; never put long I/O (
   LLM, yt-dlp) inside a transaction block
4. **No Ktor/DB in `domain`** — `domain` depends only on stdlib, Arrow, coroutines
5. **No UI in `tgminiapp`** — all screens/components live in `features`; `tgminiapp` is just shell + DI

---

## Common Task Recipes

### New endpoint

1. DTO → `api/contract` (`commonMain`)
2. Route → `server/transport`
3. Use-case → `domain` (if business logic; inject `TransactionRunner`)
4. Tests → `commonTest` (domain) + `jvmTest` (route)
5. Update `docs/API_CONTRACT.md`

### New domain type (sealed hierarchy)

Order matters — inner layers first:

1. `domain/commonMain` — sealed class/interface
2. `api/contract/commonMain` — sealed DTO with `@SerialName`
3. `api/mapping/commonMain` — bidirectional mapping
4. `commonTest` — tests
5. `features` — UI

### New category

When adding a new `Category` value, cascade through:
`Category enum` → `ResolvedMetadata` (sealed) → `MetadataTemplate` (sealed) → DTOs → mapping → `MetadataResolver` → UI

### Add a field to `Job` or `Rule`

DB migration in `server/infra/.../db/migration/` (Flyway, sequential numbering).
Update Exposed table object → persistence model (`*Pm`) → domain mapping.
`server:infra` does NOT depend on `api:contract` — DB models are separate from API DTOs.

---

## Key Types Cheatsheet

```
DomainError          — sealed interface, all business errors (domain/common/)
TransactionRunner    — interface; NoopTransactionRunner for tests (domain/tx/)
VideoInfo            — yt-dlp extraction result (domain/video/)
ResolvedMetadata     — sealed: MusicVideo | SeriesEpisode | Other (domain/metadata/)
MetadataTemplate     — sealed: same variants, used in Rule (domain/metadata/)
StoragePlan          — original + additional output targets (domain/storage/)
OutputFormat         — sealed: OriginalVideo | ConvertedVideo | Audio | Thumbnail (domain/storage/)
RuleMatch            — sealed match criteria: ChannelId | ChannelName | HasTag | TitleRegex | ... (domain/rule/)
UserOverrides        — sealed user edits in preview: MusicVideo | SeriesEpisode | Other (domain/preview/)
PreviewUseCase       — orchestrates: yt-dlp cache → rule match → LLM → overrides (domain/preview/)
```

---

## Testing Conventions

- **KMP domain/mapping tests**: `commonTest`, Kotest `FunSpec`, no MockK — use fake implementations
- **JVM server tests**: `jvmTest`, MockK allowed, Testcontainers for PostgreSQL
- **Route tests**: Ktor `testApplication`, use `X-Telegram-Init-Data: dev` with `devMode = true`
- **Transaction in tests**: `NoopTransactionRunner()` — executes block inline, no DB required
- Run tests: `./gradlew check` (excludes `e2e` tag by default)

---

## Docs Quick-Reference

| Topic                     | File                           |
|---------------------------|--------------------------------|
| Architecture + data flows | `docs/ARCHITECTURE.md`         |
| All domain models         | `docs/DOMAIN.md`               |
| Full API spec             | `docs/API_CONTRACT.md`         |
| DB schema + migrations    | `docs/DATABASE.md`             |
| Config schema (YAML/env)  | `docs/CONFIGURATION.md`        |
| Auth + security           | `docs/SECURITY.md`             |
| Test examples             | `docs/TESTING.md`              |
| Deployment                | `docs/DEPLOYMENT.md`           |
| Architecture decisions    | `docs/ADR/`                    |
| yt-dlp arg reference      | `docs/ai/yt-dlp-cheatsheet.md` |
