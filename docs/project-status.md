---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ PROJECT_CONTEXT.md, ../CLAUDE.md, plans/step-01-refactoring.md, plans/step-01/README.md ]
---

# Project status

> History of steps, newest first. Read before implementation work. A step gets a full entry
> (shipped / the mine / deliberately not built / open questions) when it closes; while it runs,
> each finished stage adds one line.

## Step 01 — Backward-compatible refactoring to the engineering-ai-skills baseline (`plans/step-01-refactoring.md`, in progress)

**Started 2026-09-29** from commit `2b2ebff` (+ plan in `7610c61`). Goal: order in build, layers,
transactions and job processing, a test safety net, and truthful agent docs — without changing
anything a user, client or deployed installation sees (except G10 and Fork 5,
[ADR-009](ADR/009-engineering-skills-baseline.md)). Stages: [`plans/step-01/README.md`](plans/step-01/README.md).

- 2026-09-29 — 01.1 done: ADR-009 (skills baseline, overrides, compatibility contract); `AGENTS.md`
  and `CLAUDE.md` rewritten; `docs/PROJECT_CONTEXT.md` and this file created; false facts fixed in
  `ARCHITECTURE.md` and `TESTING.md`.
- 2026-09-29 — 01.2 done: included build `convention-plugins/` (`tgvd.*`), repositories only in
  settings (`FAIL_ON_PROJECT_REPOS`), typesafe project accessors, no Kotest Gradle plugin/KSP (JS
  test runner off, `compileTestKotlinJs` stays), Testcontainers 2.0.3 throughout, `shadowJar` out
  of `build` (CI builds it explicitly); 278 JVM tests before and after.
- 2026-09-29 — 01.3 done: Detekt 1.23.8 + ktlint (Gradle plugin 13.1.0, `intellij_idea`, 120 cols) in
  `tgvd.kmp` / `tgvd.compose.js` / `tgvd.jvm`, main + test sources, run by `./gradlew build` in all 11
  modules; per-module baselines (Detekt 496, ktlint 3211 findings) only shrink.
- 2026-09-29 — 01.4 done: testable `Application.module(config, eagerDatabase, startBackgroundServices,
  overrides)`; Testcontainers safety net (`postgres:16-alpine`, Docker now required by `./gradlew build`):
  migrations, repository round-trips, 52 frozen JSONB fixtures, `api-surface.txt` (27 routes), 17 golden
  JSON files, 27 route tests; 187 new tests (465 JVM tests in total).
- 2026-09-29 — 01.5 done: fail-fast start (`validateConfig`, Flyway before Koin/routing, exit 1),
  `/health/live` + `/health/ready` (`SELECT 1`), lifecycle handlers guarded per application instance,
  `exec java` in the three server Dockerfiles, correlation id in log lines; **fix (Fork 5):**
  `TELEGRAM_ALLOWED_USER_IDS`/`TELEGRAM_ALLOWED_USERNAMES` now reach the config (installations that set
  them narrow access — release note); G10: malformed body/parameter → `400 VALIDATION_ERROR`; 499 JVM tests.
- 2026-09-29 — 01.6 done: job and preview routes are parse (`api:mapping`) → use-case → respond;
  `WorkspaceAccess`, `CreateJobUseCase` (validation + `saveAsRule` in one transaction),
  `List/Get/Cancel/RetryJobUseCase`, `PreviewVideoUseCase`; track selectors moved to `domain/track`
  behind `TrackSelectionSettingsProvider`; G10: broken `ruleId`/value classes in `POST …/jobs` → 400;
  fakes + mothers in `domain-test-fixtures`, first `api:mapping` tests.
- 2026-09-29 — 01.7 done: rule, channel, workspace and system routes are parse → use-case → respond;
  `server:transport` no longer depends on `server:infra` and injects no repository; domain port
  `SystemSettingsStore` (+ yt-dlp status/update use-cases); `Clock` injected without defaults
  (`single<Clock> { Clock.System }`); `UnconfiguredLlmPort` instead of `LlmPort?`; `NoopTransactionRunner`
  moved to `domain-test-fixtures`; fitness tests `TransportSourceGuardTest`, `DomainPurityTest`;
  G10: broken tags/regex/blank ids/non-positive user ids in rule, channel and member input → 400.
- 2026-09-29 — 01.8 done: only `TransactionRunner` opens transactions (`dbQuery` removed; repositories run
  in the current one; `JobProcessor` and `SystemSettingsHolder` use short runner transactions); nested runner
  joins the outer transaction and "a `Left` commits" pinned by tests; `catchingDb` (rollback, `23505` →
  `WorkspaceSlugConflict`/`JobAlreadyExists`, else `DatabaseFailed` → same `500 INTERNAL_ERROR`); repositories
  take time from `Clock` (inserts store the use-case's timestamps) and statuses via the mapping; `JobProcessor`
  smoke test on Testcontainers; 659 JVM tests.
- 2026-09-29 — 01.9 done: job status transitions are a pure table in `JobStatus`; every status write is a
  compare-and-set (`JobRepository.transition`, `updateStatus` removed), `claimNext` (`FOR UPDATE SKIP LOCKED`),
  `requeueInterrupted` at start (Fork 2); cancel stops the download and kills the yt-dlp/ffmpeg process tree;
  `stop()` requeues running jobs; the mine test (`JobLifecycleTest`) proven red on an unconditional `UPDATE`;
  714 JVM tests.
- 2026-09-29 — 01.10 done: the three multi-stage Dockerfiles build with the repository's `./gradlew`
  on `eclipse-temurin:21-jdk` (Mini App image: `domain/src` and `kotlin-js-store/yarn.lock` added — it
  did not build before); `.dockerignore` drops `.claude`, `data/`, `output/`, yt-dlp binaries; CI split
  into a read-only `ci` gate and a `release` job with write rights, `concurrency`, timeouts,
  `setup-gradle`; `docker-publish` with `packages: write` per job; compose `stop_grace_period: 20s`.

## Known issues

Deliberately not fixed in Step 01 unless the stage is named.

- `saveAsRule.includeCategory` is accepted in `POST …/jobs` but ignored: `api:mapping`
  (`SaveAsRuleDto.toDomain()`) drops it and the domain has no such parameter. Fixing it changes behaviour — out of
  Step 01.
- Joining a workspace by slug: `POST /workspaces` with a slug that is already taken adds the caller
  as `MEMBER` (`CreateWorkspaceUseCase`). The client relies on it to reconnect; kept as is, a known
  access risk.
- `VideoInfoCacheImpl.evictExpired` is never called — expired cache rows are not cleaned up.
- `TELEGRAM_DEV_MODE=true` is the default in `docker-compose.yaml` and `.env.example` (local-dev
  compose). Not changed; since 01.5 the server logs a `WARN` at start and `DEPLOYMENT.md` warns.
- `jobs.maxAttempts`, `jobs.retryDelayMs`, `logging.level`, `logging.format` are read from the
  config but not used anywhere (documented as such in `CONFIGURATION.md` §7.1 since 01.5).
