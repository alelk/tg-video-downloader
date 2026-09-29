---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ ../AGENTS.md, ../CLAUDE.md, project-status.md, ADR/009-engineering-skills-baseline.md, plans/step-01-refactoring.md ]
---

# Project Context (for agents and people)

> The one working context. Everything here is confirmed by the repository (as of Step 01, stage
> 01.1). Module map and commands: [`AGENTS.md`](../AGENTS.md). Code rules: `.claude/skills/` +
> exceptions in [ADR-009](ADR/009-engineering-skills-baseline.md) — not here.

## 1. What this is

**TG Video Downloader** — a self-hosted service that downloads videos from YouTube, RuTube, VK
Video and the other sites `yt-dlp` supports, names and files them by rules (music videos, series
episodes, other), optionally converts them with `ffmpeg`, and is operated from a **Telegram Mini
App**.

- **Users:** home use — the owner, family or a small group, **1–5 people**. Access is by Telegram
  account (allow-list of user ids / usernames). Users share **workspaces** (rules, channel
  directory, jobs are per workspace; members have a `WorkspaceRole`).
- **Deployment:** **one instance** via `docker compose`: `db` (PostgreSQL 16, `postgres:16-alpine`),
  `server` (Ktor, port 8080), `webapp` (Mini App static bundle, port 3000), `bgutil` (yt-dlp PO
  token provider). Details: [`DEPLOYMENT.md`](DEPLOYMENT.md).
- **Constraints:**
  - **Backward compatibility** is a contract: an old client, DB, config and compose file work with
    a new server — [ADR-009 → Compatibility contract](ADR/009-engineering-skills-baseline.md#compatibility-contract).
  - **Simplicity over SaaS machinery:** one DB role, no RLS, no JWT sessions, no outbox, no
    automatic retries — the full list of deliberate non-adoptions is in ADR-009.
  - **Single instance:** the job processor and the long-polling Telegram bot assume exactly one
    server process.

## 2. Stack

Kotlin Multiplatform (JVM 21 toolchain; web target `js(IR)`), Ktor 3 server and client, Koin,
Hoplite config, Exposed 1.x + HikariCP + Flyway on PostgreSQL, kotlinx.serialization, Arrow
`Either`, Compose Multiplatform + Voyager navigation, `tgbotapi` (Telegram bot), kotlin-logging +
Logback, Kotest + MockK (JVM only) + Testcontainers. External tools: `yt-dlp`, `ffmpeg`.
Versions: **only** `gradle/libs.versions.toml`; product version: **only** `app.version`.

## 3. Modules and layer rules

Module map: [`AGENTS.md → Modules`](../AGENTS.md#modules) (from `settings.gradle.kts`).

Intended dependency direction (inner layers know nothing of outer ones):

```
tgminiapp → features → api:client → api:contract
                    ↘ domain ← api:mapping → api:contract
server:app → server:di → server:transport → domain, api:contract, api:mapping
                       → server:infra     → domain          (never api:*)
```

Every route is parse (`api:mapping`, `Either`) → use-case (membership and ownership checked inside
its transaction) → respond (since 01.6 for jobs and preview, 01.7 for the rest). `server:transport`
does not depend on `server:infra` and injects no repository — pinned by `TransportSourceGuardTest`;
`domain/commonMain` stays free of Ktor/Exposed/serialization/Koin and `Clock.System` —
`DomainPurityTest`.

## 4. Commands

See [`AGENTS.md → Commands`](../AGENTS.md#commands). `./gradlew build` is the only gate. Migrations
run automatically when the server starts (Flyway inside the process); there is no separate migrate
command.

## 5. Invariants

- **Errors:** business errors are `Either<DomainError, T>` (`domain/common/DomainError`);
  transport maps them to HTTP + `ApiErrorDto`.
- **Transactions:** use-cases wrap work in `TransactionRunner.inRwTransaction {}` /
  `inRoTransaction {}`; no yt-dlp/ffmpeg/LLM/HTTP inside a transaction. Only the runner opens a
  transaction (repositories run in the current one); a `Left` returned from the block commits, so
  checks come before writes; database errors go through `catchingDb` (`DATABASE.md` §7).
- **Isolation:** every resource belongs to a workspace; the API path carries the workspace id
  (`/api/v1/workspaces/{workspaceId}/…`); membership must be checked before access.
- **Ids and values:** value classes (`JobId`, `RuleId`, `WorkspaceId`, `ChannelDirectoryEntryId`,
  `Tag`, `Url`, `FilePath`, `LocalDate`…); `commonMain` never uses `java.*`.
- **Persistence:** JSONB columns use `*Pm` models in `server:infra`, separate from DTOs;
  migrations `V1…V8` are frozen, new ones are `V9+`.
- **Jobs:** `JobStatus` = `PENDING → DOWNLOADING → POST_PROCESSING → COMPLETED | FAILED | CANCELLED`;
  `JobProcessor` claims `PENDING` jobs atomically; every status write is a compare-and-set; stop and
  restart return interrupted jobs to `PENDING` (ARCHITECTURE §5.3).
- **Auth:** Telegram `initData` (HMAC + `auth_date`) validated on every request
  (header `X-Telegram-Init-Data`); `devMode` accepts `dev` as init data.

## 6. External services

| Port (domain)                         | Adapter (server:infra / server:app) | Purpose                                  |
|---------------------------------------|-------------------------------------|------------------------------------------|
| `VideoInfoExtractor`, `VideoDownloader` | `YtDlpRunner`                 | metadata extraction, downloads           |
| `YtDlpService` (`domain/system`)      | `YtDlpServiceImpl`                  | yt-dlp version and self-update           |
| — (used by the job processor)         | `FfmpegRunner`                      | conversion, audio extraction, tag/cover embedding |
| `VideoInfoCache`                      | `VideoInfoCacheImpl` (PostgreSQL)   | yt-dlp result cache for preview          |
| `SystemSettingsStore` (`domain/system`) | `SystemSettingsHolder` (PostgreSQL `system_settings`) | runtime yt-dlp and proxy settings (`/system/settings`) |
| `LlmPort`                             | `UnconfiguredLlmPort` (no real adapter yet) | metadata suggestions; refused → `MetadataResolver` fallback |
| —                                     | `TelegramMiniAppAutoReplyBot` (long polling, `server:app`) | replies with a Mini App button |

## 7. Key domain flows and change recipes

- **Preview:** `POST …/preview` → `PreviewVideoUseCase` (membership) → `PreviewUseCase`:
  `VideoInfoCache` (yt-dlp on miss) → rule matching (with channel directory,
  [ADR-008](ADR/008-channel-directory.md)) → metadata (rule template < channel overrides < user
  overrides) → storage plan, default tracks (`domain/track` selectors), download history.
  [ADR-007](ADR/007-interactive-preview-refinement.md).
- **Job:** `POST …/jobs` → `CreateJobUseCase` (membership, validation, optional `saveAsRule` in the
  same transaction) → `PENDING` → `JobProcessor` downloads, moves, converts, embeds →
  `COMPLETED`/`FAILED`. List / get / cancel / retry via their workspace-scoped use-cases.
- **New category:** `Category` → `ResolvedMetadata` → `MetadataTemplate` → DTOs → mapping →
  `MetadataResolver` → UI.
- **New sealed variant:** domain → `api:contract` (`@SerialName`) → `api:mapping` → `*Pm` if
  persisted → tests → `features`.
- **New field on a persisted entity:** new `V9+` migration → Exposed table → `*Pm` → domain
  mapping; new DTO fields are optional with a default.

## 8. Terminology pitfalls

- `Channel` is a **channel-directory entry** (`ChannelDirectoryEntryId`); `ChannelId` is the
  platform's channel id from yt-dlp. An entry is keyed by `channelId + extractor` per workspace.
- `MetadataTemplate` (template on a rule/channel) vs `ResolvedMetadata` (result for one video) vs
  `UserOverrides` (manual edits in preview).
- Domain `*Request` classes (`CreateJobRequest`, `CreateRuleRequest`…) are domain commands; wire
  types are `*Dto` / `*RequestDto` in `api:contract`.
- Job statuses are `PENDING/DOWNLOADING/POST_PROCESSING/COMPLETED/FAILED/CANCELLED` — older docs
  that say `QUEUED/RUNNING/DONE` are wrong.

## 9. Testing

Kotest `FunSpec`. Domain, contract and `features` tests live in `commonTest` (JVM runner via
JUnit 5); fakes, not MockK, in `commonTest`. Server tests in `src/test` (JVM): repositories, migrations
and routes run on Testcontainers (`postgres:16-alpine`, Docker required); `api-surface.txt`, golden JSON
and frozen JSONB fixtures pin the wire and the stored data (fix the code, never the snapshot). Strategy: [`TESTING.md`](TESTING.md) and the
`kotlin-testing-strategy` skill.

## 10. Common traps

- `.claude/skills/` is a git-ignored copy: never edit it; exceptions go to ADR-009.
- Hoplite's env source nests on `_` (`TELEGRAM_BOT_TOKEN` → `telegram.bot.token`, no such key): `TELEGRAM_*`
  variables reach camelCase keys only through `${VAR:-}` placeholders in `application.yaml` and the
  compose inline config. A new variable needs such a placeholder in both places.
- `docker-compose.yaml` and `.env.example` default `TELEGRAM_DEV_MODE=true` (local-dev compose).
- Known issues and their status: [`project-status.md`](project-status.md#known-issues).

## 11. Documentation map

| Topic                         | Document                                              |
|-------------------------------|-------------------------------------------------------|
| Current phase, history, known issues | [`project-status.md`](project-status.md)       |
| Step plans and stages         | [`plans/`](plans/)                                    |
| Architecture, data flows      | [`ARCHITECTURE.md`](ARCHITECTURE.md)                  |
| Domain model                  | [`DOMAIN.md`](DOMAIN.md)                              |
| HTTP API                      | [`API_CONTRACT.md`](API_CONTRACT.md)                  |
| Database                      | [`DATABASE.md`](DATABASE.md)                          |
| Configuration                 | [`CONFIGURATION.md`](CONFIGURATION.md)                |
| Security                      | [`SECURITY.md`](SECURITY.md)                          |
| Testing                       | [`TESTING.md`](TESTING.md)                            |
| Deployment, maintenance       | [`DEPLOYMENT.md`](DEPLOYMENT.md), [`MAINTENANCE.md`](MAINTENANCE.md) |
| Decisions                     | [`ADR/`](ADR/) (ADR-001 … ADR-009)                    |
| yt-dlp arguments              | [`ai/yt-dlp-cheatsheet.md`](ai/yt-dlp-cheatsheet.md)  |
