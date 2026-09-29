---
status: accepted
owner: Alex (alelk)
updated: 2026-09-29
related: [ ../plans/step-01-refactoring.md, ../plans/step-01/README.md, ../PROJECT_CONTEXT.md, ../../AGENTS.md ]
---

# ADR-009: engineering-ai-skills as the engineering baseline, with home-scale exceptions

**Status**: Accepted
**Date**: 2026-09-29
**Authors**: Alex Elkin

---

## Context

### Skills

Code conventions for this repository come from the `engineering-ai-skills` catalogue
(<https://github.com/alelk/engineering-ai-skills>). A local copy lives in `.claude/skills/`; it is
git-ignored through `.git/info/exclude` and is never edited in this repository — the catalogue is
the source of truth, and project-specific deviations are recorded **only in this ADR**.

The catalogue is written for multi-tenant SaaS products. This project is a **home** service: one
installation, one server process, 1–5 users (a family or a small group), deployed with
`docker compose` next to a single PostgreSQL 16. Many skill rules pay off here; some add cost
without benefit.

**Applied skills (15)** — installed in `.claude/skills/`:

| Skill                        | Used for                                                                  |
|------------------------------|---------------------------------------------------------------------------|
| `kotlin-project-layout`      | Gradle layout, version catalogue, convention plugins, Detekt + ktlint     |
| `kmp-architecture`           | KMP targets, `commonMain` purity, Kotest on the JVM                       |
| `kotlin-clean-architecture`  | module boundaries, ports & adapters, `Unconfigured*` adapters             |
| `kotlin-domain-modeling`     | use-cases, `Either<DomainError, T>`, `TransactionRunner`, injected `Clock` |
| `kotlin-testing-strategy`    | test pyramid, fakes, Testcontainers, tests proven red                     |
| `architecture-fitness-tests` | rules as tests inside `./gradlew build`, `KNOWN_*` ratchets               |
| `ktor-backend`               | testable `module()`, config, `StatusPages`, lifecycle, health             |
| `ktor-api-contract`          | DTO rules, one JSON policy, never-throwing mapping, `Either` client       |
| `exposed-postgres`           | Exposed 1.x, repositories without transactions, `catchingDb`, schema tests |
| `outbox-and-background-jobs` | claim → work outside a transaction → settle, for the job queue           |
| `telegram-miniapp`           | `initData` validation, thin `tgminiapp` shell                             |
| `compose-multiplatform-ui`   | `features` layering, screen state holder, effects                         |
| `gradle-docker-build`        | Dockerfiles, `exec` entrypoint, JVM container flags                       |
| `gradle-release-system`      | Conventional Commits, semantic-release, CI runs `./gradlew build`         |
| `doc-first-agentic-workflow` | agent entry files, ADRs, step plans and stages, project status            |

**Not applied (6)** — deliberately not installed:

| Skill                       | Why not                                                                                     |
|-----------------------------|---------------------------------------------------------------------------------------------|
| `postgres-multitenancy-rls` | One installation, one DB role; workspace isolation is enforced in use-cases (G3).          |
| `ktor-auth-sessions`        | Telegram `initData` is validated on every request; there is no other client (Fork 4, G4).  |
| `compose-navigation3`       | Voyager stays; migrating navigation for a home UI does not pay off (Fork 3, G12).           |
| `compose-web-shell`         | The only shell is the Telegram Mini App (`tgminiapp`, `js(IR)`); no standalone web shell.  |
| `compose-design-system`     | `TgvdTheme`/`MaterialTheme` stay; no design-token layer (G12).                              |
| `llm-integration`           | No LLM adapter is implemented; `LlmPort` gets an `Unconfigured` adapter only (G8).          |

The decisions below were closed in the Step 01 plan
([`docs/plans/step-01-refactoring.md`](../plans/step-01-refactoring.md), Decisions G1–G15 and
Forks 1–5). G-numbers refer to that table.

---

## Decision

The skills in `.claude/skills/` are the default rules for all code. Where a skill rule and this
table disagree, **this table wins**. Anything not listed here follows the skill as written.

| Ref        | Skill rule                                                                                             | Verdict       | Project rule                                                                                                                                                                                                                  |
|------------|--------------------------------------------------------------------------------------------------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| G2, Fork 1 | Migrations run as a separate provisioning step under a schema-owner role (`exposed-postgres`, `gradle-docker-build`: one-shot `migrate` service) | **Override**  | Flyway runs **inside the server process**, eagerly, **before routing is installed**; a failed migration terminates the process with a non-zero exit code. `baselineOnMigrate` is left as is. No separate migrate job/service. |
| G2         | Baseline migrations may be edited in place before the first deploy (`exposed-postgres`)                 | **Not taken** | The service is deployed. `V1…V8` are byte-for-byte frozen; schema changes only as new `V9+`.                                                                                                                                 |
| G3         | Row-level security, separate runtime/schema-owner/jobs roles (`postgres-multitenancy-rls`, `exposed-postgres`, `outbox-and-background-jobs`) | **Not taken** | One DB role, no RLS, no policies. Isolation by workspace is checked in use-cases (membership + "resource belongs to the workspace").                                                                                          |
| G3         | Tenant id comes from the token, never from the URL (`ktor-api-contract`, `ktor-backend`)               | **Override**  | The workspace id stays in the path (`/api/v1/workspaces/{workspaceId}/…`, G1). The use-case verifies that the caller is a member of that workspace.                                                                           |
| G4, Fork 4 | Exchange `initData` for a JWT session (`telegram-miniapp`, `ktor-auth-sessions`)                        | **Not taken** | `initData` is validated (HMAC + `auth_date`) on **every** request; `devMode` stays as it is. No JWT, no sessions, no refresh.                                                                                                |
| G4, Fork 5 | Fail-fast on unsafe configuration (`ktor-backend`)                                                      | **Apply, adapted** | `TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES` reach the config; an empty value is an empty list. Both lists empty → access for every Telegram user, as today, but a loud `WARN` at startup; `devMode=true` → `WARN` too. The server does not refuse to start. |
| G6         | Status machine as a pure table; compare-and-set writes; claim with `FOR UPDATE SKIP LOCKED` (`kotlin-domain-modeling`, `outbox-and-background-jobs`) | **Apply**     | `JobStatus` transitions are a pure table in `domain`; every status write is CAS `from → to`; `PENDING → DOWNLOADING` claim is atomic; on start `DOWNLOADING/POST_PROCESSING → PENDING`; on stop active jobs → `PENDING` (Fork 2: without incrementing `attempt`); user cancel kills the process. |
| G6         | Durable retry ladder for failed work (`outbox-and-background-jobs`)                                     | **Not taken** | No automatic retries. `jobs.maxAttempts` / `jobs.retryDelayMs` are accepted and documented as unused. Retry stays a user action (`RetryJobUseCase`).                                                                         |
| G6         | Multi-instance safety of pollers and singleton jobs (`outbox-and-background-jobs`)                      | **Override**  | **Exactly one server instance** is a deployment condition. `SKIP LOCKED` protects concurrent claims inside that instance; running two instances is unsupported.                                                              |
| G5         | Only `TransactionRunner` opens transactions; repositories never do; no long I/O inside a transaction (`exposed-postgres`, `kotlin-domain-modeling`) | **Apply**     | `dbQuery` is removed; repositories run in the current transaction; `catchingDb` maps `SQLException`. yt-dlp, ffmpeg, LLM and HTTP calls never run inside a transaction.                                                      |
| G7         | Verb-named single-method use-cases, command in the same file (`kotlin-domain-modeling`)                | **Override**  | Class names **keep the `…UseCase` suffix** (no mass renames). A **new** command lives in the file of its use-case; existing `*Request` types stay where they are. Routes = parse → use-case → respond; membership and ownership checks live in use-cases; reads go through query use-cases. |
| G7, G13    | Client-generated UUIDv7 ids and idempotent create; `version` column + CAS on every mutable aggregate (`kotlin-domain-modeling`, `ktor-api-contract`, `exposed-postgres`) | **Not taken** | Server-generated ids stay; no `version` columns. CAS is used **only** for the job status.                                                                                                                                    |
| G8         | Optional external services through `Unconfigured*` adapters (`kotlin-clean-architecture`, `ktor-backend`) | **Apply**     | `UnconfiguredLlmPort` replaces `LlmPort?`; behaviour is identical (fallback to `MetadataResolver`). No LLM adapters in Step 01.                                                                                               |
| G9         | Injected `kotlin.time.Clock` (`kotlin-clean-architecture`, `kotlin-domain-modeling`)                    | **Apply**     | `Clock` is injected everywhere without constructor defaults, including repositories; DI binds `Clock.System`.                                                                                                                |
| G11        | Web target `wasmJs`; `compileKotlinWasmJs` as the purity gate (`kmp-architecture`)                      | **Override**  | The web target stays **`js(IR)`** (old Telegram WebViews); no `wasmJs`. The JS test runner is disabled; `compileTestKotlinJs` stays the `commonMain`/`commonTest` purity gate inside `./gradlew build`.                      |
| G11        | Convention plugins, version catalogue, Kotest on JVM via JUnit 5, Detekt + ktlint with baselines, `shadowJar` outside `build` (`kotlin-project-layout`, `kmp-architecture`, `ktor-backend`) | **Apply**     | Included build `convention-plugins/`, plugin id prefix `tgvd.`. `./gradlew build` is the one gate.                                                                                                                           |
| G12, Fork 3 | androidx `ViewModel` + Navigation 3; design-system tokens, `AppTheme.*` only (`compose-multiplatform-ui`, `compose-navigation3`, `compose-design-system`) | **Override**  | Voyager and `TgvdTheme`/`MaterialTheme` stay. The state holder is a Voyager **`ScreenModel`** with the same pattern (`StateFlow` state + `Channel` effects + one `onEvent`) and Entry/Content split — for Preview and Settings; the other screens are held by a `KNOWN_*` ratchet. |
| G12        | API client returns `Either<ApiError, T>` (`ktor-api-contract`, `compose-multiplatform-ui`)              | **Apply**     | `TgVideoDownloaderClient` returns `Either<ApiError, T>`; no `ApiException` in `features`.                                                                                                                                     |
| G13        | Cursor pagination (`PageDto`), outbox/events, DTO renames and snake_case `@SerialName`, `@Resource` URL restructuring (`ktor-api-contract`, `outbox-and-background-jobs`) | **Not taken** | Lists stay bare arrays; no outbox, no events; DTO names and `@SerialName` values are frozen by G1; existing URLs stay.                                                                                               |
| G13        | BuildKit secrets, SHA-pinned GitHub Actions (`gradle-docker-build`, `gradle-release-system`)            | **Not taken** | Docker build args and GitHub Actions references stay as they are.                                                                                                                                                                          |
| G14        | Numbered docs tree `00-meta … 70-development`, four-digit ADR numbers, frontmatter on every doc (`doc-first-agentic-workflow`) | **Override**  | `docs/` keeps its names (no `00-…70-`); ADRs continue `ADR-NNN` (`ADR-009`); plans live in `docs/plans/`; `project-status.md` and `PROJECT_CONTEXT.md` live in `docs/`. Frontmatter is added only to documents that are touched. Each stage fixes the documents whose facts it changed. |
| G15        | Skills installed from a shared registry, git-ignored, never edited in the project (`doc-first-agentic-workflow`) | **Apply**     | Local copy in `.claude/skills/` (excluded via `.git/info/exclude`); exceptions only here.                                                                                                                                    |

---

## Compatibility contract

Step 01 and every later change governed by this ADR are **backward compatible**. An old client, an
old database, an old config and an old compose file keep working with a new server.

- **HTTP.** The set of routes (method + path), JSON field names, types, discriminators and
  defaults, error `code` values and HTTP statuses do not change. Only additions are allowed.
- **Database.** Migrations `V1…V8` are byte-for-byte unchanged; schema changes only as new `V9+`.
  Stored values (`status`, `category`, JSONB documents) written by earlier versions stay readable.
- **Configuration.** Every key and environment variable keeps its meaning and default. New keys are
  optional, with a default equal to the current behaviour.
- **Deployment.** Image names, Dockerfile paths, ports, volumes, `/health` and release assets stay
  the same.

The **only** intentional exceptions:

1. **G10 — malformed input.** Broken JSON, a malformed UUID in a path or body, an empty value for a
   value class → `400 VALIDATION_ERROR` instead of `500 INTERNAL_ERROR`. Answers that are already
   `400` (e.g. `POST /workspaces` with a bad slug and a `{"error": …}` body) do not change. DB
   errors stay `500 INTERNAL_ERROR`.
2. **Fork 5 — allow-list from `.env`.** `TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES`
   now reach the config (they were documented in `.env.example`, `CONFIGURATION.md` and
   `DEPLOYMENT.md` but silently ignored). Where they are set, access narrows to the listed users.
   Shipped as a `fix:` commit and called out in the release notes.

---

## Alternatives

- **Adopt every skill as written** (RLS, JWT sessions, Navigation 3, wasm, cursor pages, outbox,
  client ids + `version`) — rejected: large wire/schema/UI churn and moving parts that a
  1–5-user single-instance service never uses; it would also break G1.
- **Fork the skills into the repository and edit them** — rejected: two sources of truth; the
  catalogue drifts from the copy. Exceptions belong in one ADR.
- **Separate migrate job (skill default)** — rejected for one installation with one role: an extra
  container and ordering in compose, with no security gain.

---

## Consequences

- **One installation = one DB role and one server process.** Nothing in the schema or the job
  processor coordinates across instances beyond `SKIP LOCKED` claims; running two server replicas
  is unsupported (deployment docs must say so).
- **The Telegram bot uses long polling and is a singleton**: a second process with the same bot
  token would fight over `getUpdates`. This is one more reason for the single-instance condition.
- Workspace isolation depends on use-case checks, not on the database — tests must cover "a
  resource of another workspace → 404" through the route.
- Wire, schema, config and deployment compatibility are checked by the safety net of Step 01
  (stage 01.4): API surface snapshot (`api-surface.txt`), golden JSON responses, frozen JSONB
  fixtures, migration tests. A red test there means: fix the code, not the expectation (except G10
  and new routes).
- Rules that can be checked mechanically become fitness tests inside `./gradlew build`; their
  `KNOWN_*` lists only shrink.
- Upgrading the skills catalogue may introduce new rules; any new conflict is resolved by a new ADR
  that supersedes this one, not by editing it.
