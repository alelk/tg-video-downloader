# AGENTS.md

Working guide for AI agents (Claude Code, Codex, Junie…). Full context:
**[`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md)**. Current phase: [`CLAUDE.md`](CLAUDE.md) →
"Current phase" — read it before each task.

## What this is

TG Video Downloader — a self-hosted **home** service (one instance, 1–5 users) that downloads
videos with `yt-dlp` (+ `ffmpeg` post-processing) and is operated through a Telegram Mini App.
Kotlin Multiplatform: Ktor server + PostgreSQL, Compose Multiplatform UI compiled to JS.
Versions only in `gradle/libs.versions.toml`; product version only in `app.version`.

## Modules

| Path                     | What                                                            | Targets |
|--------------------------|-----------------------------------------------------------------|---------|
| `domain/`                | entities, ports, use-cases, `DomainError`, `TransactionRunner` | jvm, js |
| `domain/domain-test-fixtures/` | test support: fakes, object mothers, `TestClock`, `NoopTransactionRunner`, `Arb`s | jvm, js |
| `api/contract/`          | HTTP DTOs (kotlinx.serialization)                               | jvm, js |
| `api/mapping/`           | domain ↔ DTO mapping                                            | jvm, js |
| `api/client/`            | Ktor HTTP client `TgVideoDownloaderClient`                      | jvm, js |
| `features/`              | all Compose screens/components, Koin `FeaturesModule`           | jvm, js |
| `tgminiapp/`             | Telegram Mini App shell: entrypoint, DI, Telegram interop       | js      |
| `server/infra/`          | Exposed repositories, Flyway migrations, yt-dlp/ffmpeg, `JobProcessor`, config | jvm |
| `server/transport/`      | Ktor routes, `initData` auth, error mapping                     | jvm     |
| `server/di/`             | server Koin modules                                             | jvm     |
| `server/app/`            | `Application.kt` entrypoint, Telegram bot, `application.yaml`   | jvm     |

Build logic lives in the included build `convention-plugins/` (`tgvd.kmp`, `tgvd.kmp.serialization`,
`tgvd.compose`, `tgvd.compose.js`, `tgvd.jvm`, `tgvd.jvm.serialization`): a module applies one
convention and lists its dependencies; repositories are only in `settings.gradle.kts`; project
dependencies use `projects.x` accessors — [`ARCHITECTURE.md` §4](docs/ARCHITECTURE.md#4-gradle-modules).

Web target is `js(IR)` (no wasm). LLM adapters don't exist yet: `LlmPort` is bound to
`UnconfiguredLlmPort` (infra), which refuses every suggestion — previews use the fallback resolver.

## Commands

```bash
docker compose up -d postgres              # local PostgreSQL 16 on localhost:5433
./gradlew build                            # THE gate: compiles all targets, all tests, Detekt, ktlint
./gradlew :server:app:run                  # run the server
./gradlew :tgminiapp:jsBrowserDevelopmentRun   # run the Mini App UI (dev)
```

`./gradlew build` is the only gate — a change is done when it is green. There is no other
aggregate test task to run.

## Rules

- **Compatibility is a contract** ([ADR-009](docs/ADR/009-engineering-skills-baseline.md#compatibility-contract)):
  routes, JSON fields/types/discriminators/defaults, error codes and statuses, config keys and env
  variables, image names, ports and volumes change only by **addition**.
- Migrations `V1…V8` in `server/infra/src/main/resources/db/migration/` are never edited; schema
  changes go into a new `V9+`.
- Dependencies point inward; `domain` knows only stdlib, Arrow, coroutines; `server:infra` never
  depends on `api:*` (`*Pm` models are separate from DTOs) — skill `kotlin-clean-architecture`.
- No `java.*` in `commonMain`: `kotlin.uuid.Uuid`, `kotlin.time.Instant/Duration`, project value
  classes — skill `kmp-architecture`.
- Business errors are `Either<DomainError, T>`, never exceptions; DTO → domain mapping never
  throws — skills `kotlin-domain-modeling`, `ktor-api-contract`.
- Writes in `txRunner.inRwTransaction {}`, reads in `inRoTransaction {}`; never yt-dlp, ffmpeg,
  LLM or HTTP inside a transaction block — skill `exposed-postgres`.
- No UI in `tgminiapp` — screens and components live in `features` — skill `telegram-miniapp`.
- Detekt/ktlint findings are fixed, not baselined: per-module `detekt-baseline.xml` /
  `ktlint-baseline.xml` only shrink; no repo-wide `ktlintFormat`; a `@Suppress` carries a one-line
  reason ([`ARCHITECTURE.md` §4.2](docs/ARCHITECTURE.md#42-convention-plugins)).
- Never log `initData`, the bot token or other secrets.
- Bug → failing test first. Never weaken, skip or delete a test to get green. A red fitness test
  (API surface, conventions, schema) means: fix the code; `KNOWN_*` lists only shrink.
- Commits only when the owner asks; Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`,
  `build:`, `docs:`), never `!` or `BREAKING CHANGE` (semantic-release would bump major).

## Never

- Inline a dependency version or the product version.
- Implement from a `draft` document or decide beyond the current plan — ask the owner.
- Fix things outside the task at hand — record them instead.
- Invent deploy commands or config keys.

## Skills

Code conventions ("how to write it") are the skills in `.claude/skills/`. They are a **local,
git-ignored copy** (excluded via `.git/info/exclude`) of the
[`engineering-ai-skills`](https://github.com/alelk/engineering-ai-skills) catalogue — **never edit
them here**; change the catalogue instead. Where this project deviates from a skill, the deviation
is recorded in [ADR-009](docs/ADR/009-engineering-skills-baseline.md) and ADR-009 wins.

Installing the copy on a new machine: Option C ("Project-level install") of the catalogue's
[`INSTALL.md`](https://github.com/alelk/engineering-ai-skills/blob/main/docs/ai/skills/INSTALL.md),
for the 15 skills listed in ADR-009.
