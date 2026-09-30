---
status: stable
owner: Alex (alelk)
updated: 2026-09-30
related: [ PROJECT_CONTEXT.md, ../CLAUDE.md, plans/step-01-refactoring.md, plans/step-01/README.md ]
---

# Project status

> History of steps, newest first. Read before implementation work. A step gets a full entry
> (shipped / the mine / deliberately not built / open questions) when it closes; while it runs,
> each finished stage adds one line.

## Step 01 — Backward-compatible refactoring to the engineering-ai-skills baseline (`plans/step-01-refactoring.md`, done 2026-09-30)

**Started 2026-09-29** from commit `2b2ebff` (+ plan in `7610c61`); all 13 stages done, none deferred.
Goal: order in build, layers, transactions and job processing, a test safety net, and truthful agent
docs — without changing anything a user, client or deployed installation sees (except G10 and Fork 5,
[ADR-009](ADR/009-engineering-skills-baseline.md)). Stages and their executor notes:
[`plans/step-01/README.md`](plans/step-01/README.md). JVM tests: 278 → 753.

**Shipped.**
- Build ([01.2](plans/step-01/01.2-build-conventions.md), [01.3](plans/step-01/01.3-static-analysis.md)):
  included build `convention-plugins/` (`tgvd.*`), catalogue-only versions, repositories only in
  settings, Kotest on the JVM via JUnit 5 (JS test runner off, `compileTestKotlinJs` is the purity
  gate), `shadowJar` outside `build`; Detekt + ktlint with per-module baselines that only shrink.
  `./gradlew build` is the one gate (Docker required).
- Server start and lifecycle ([01.4](plans/step-01/01.4-safety-net.md), [01.5](plans/step-01/01.5-bootstrap-and-lifecycle.md)):
  testable `Application.module(config, eagerDatabase, startBackgroundServices, overrides)`; fail-fast
  config (`validateConfig`), Flyway before Koin/routing, exit 1 on failure; `/health/live`,
  `/health/ready`; `exec java` in the Dockerfiles; correlation id in logs.
- Domain and transport ([01.6](plans/step-01/01.6-thin-transport-jobs-preview.md), [01.7](plans/step-01/01.7-thin-transport-rest.md)):
  every route is parse (`api:mapping`, `Either`) → use-case → respond; membership and ownership are
  checked in use-cases (`WorkspaceAccess`); `server:transport` no longer depends on `server:infra`;
  track selectors and system settings behind domain ports; `Clock` injected; `UnconfiguredLlmPort`.
- Persistence ([01.8](plans/step-01/01.8-transactions.md)): only `TransactionRunner` opens
  transactions; repositories run in the current one; `catchingDb` (rollback, unique violations →
  domain conflicts, else `DatabaseFailed` → the same `500 INTERNAL_ERROR`); time from `Clock`.
- Job processing ([01.9](plans/step-01/01.9-job-processing.md)): `JobStatus` transition table;
  every status write is a compare-and-set (`JobRepository.transition`); atomic `claimNext`
  (`FOR UPDATE SKIP LOCKED`); interrupted jobs back to `PENDING` on start and on stop (Fork 2);
  user cancel kills the yt-dlp/ffmpeg process tree.
- Delivery ([01.10](plans/step-01/01.10-delivery.md)): Dockerfiles build with `./gradlew` (the Mini
  App image builds again); least-privilege CI with a read-only gate and a separate `release` job;
  compose `stop_grace_period: 20s`.
- Client and UI ([01.11](plans/step-01/01.11-client-and-shell.md), [01.12](plans/step-01/01.12-screen-models.md)):
  `TgVideoDownloaderClient` returns `Either<ApiError, T>`; thin `tgminiapp` shell; Preview and Settings
  on Voyager `ScreenModel`s with Entry/Content split; a ratchet for the remaining screens.
- Fitness tests: `ApiSurfaceTest`, `TransportSourceGuardTest`, `DomainPurityTest`,
  `ShellSourceGuardTest`, `UiConventionsTest`.
- Docs and ADRs ([01.1](plans/step-01/01.1-decisions-and-agent-entry.md), [01.13](plans/step-01/01.13-close-out.md)):
  ADR-009; `AGENTS.md`, `CLAUDE.md`, `PROJECT_CONTEXT.md`, this file; facts in `docs/*.md` re-checked
  against the code at close.
- Behaviour changes (all intended): G10 — malformed input → `400 VALIDATION_ERROR` instead of `500`;
  **Fork 5 (`fix:`)** — `TELEGRAM_ALLOWED_USER_IDS`/`TELEGRAM_ALLOWED_USERNAMES` reach the config
  (release note: installations that set them now narrow access); a concurrent duplicate job → `409`
  instead of `500`; a browser network failure is shown as an error text instead of escaping; Preview
  edits survive navigating away and back (01.12, see open questions).

**The mine.** "Resurrected" and lost jobs — a status write that bypasses the transaction or writes
unconditionally, so a cancelled job becomes `COMPLETED` or an interrupted one hangs in `DOWNLOADING`.
Proven absent by `server/app/.../job/JobLifecycleTest` (cancel mid-download via the route, `stop()`
mid-download, a `downloading` row before start), proven red by replacing the CAS with an unconditional
`UPDATE` (recipe in its KDoc). Secondary: a silent compatibility break — pinned by `ApiSurfaceTest`,
`GoldenJsonTest`, `JsonbFixturesTest`, `MigrationsTest` and the route tests of 01.4; the empty
allow-list from env (01.5) — `ConfigBindingTest`.

**Deliberately not built.** Everything in G13 (client ids and idempotent create, `version`
columns, cursor pagination, outbox, DTO renames, `@Resource` restructuring, BuildKit secrets,
SHA-pinned actions, design system, Navigation 3, LLM adapters) — ADR-009. From the plan's *Not in*:
new features, automatic retries, splitting `JobProcessor` into use-cases, SQL pagination, upsert
instead of select-then-write, and the known issues below (`includeCategory`, joining by slug,
`evictExpired`, `TELEGRAM_DEV_MODE=true` in compose, unused config keys).

**Open questions for the owner** (raised by stage executors, not decided):
1. Allow-list parsing (01.5): a non-numeric entry in `TELEGRAM_ALLOWED_USER_IDS` is silently dropped;
   if every entry is invalid the list becomes empty and access opens to every Telegram user (the start
   log still reports "1 user id"). Should `validateConfig` reject it (changes behaviour of old configs)?
2. Repository finder methods (01.8): only port methods that already returned `Either` go through
   `catchingDb`; finders, `delete`, `saveAll`, `claimNext`, cache methods throw on a DB error (→ the
   same `500`). Should they return `Either` too, as the `exposed-postgres` skill recommends (beyond the plan)?
3. Delivery (01.10):
   - the new CI has not run yet — push a branch / open a PR and check a green run;
   - the published server image (`server/app/Dockerfile.ci`) lacks `python3-pip` and the bgutil
     PO-token plugin that `server/app/Dockerfile` installs — which composition is intended?
   - semantic-release runs on Node 20 (EOL) and is installed with `npm install --global` without a
     lockfile (the skill suggests `release/package.json` + `npm ci`);
   - `nginx:1.27-alpine` and `bgutil…:latest` are not pinned by minor/digest.
4. UI (01.11, 01.12):
   - the manual click-through in real Telegram (preview, create job, jobs list, settings) has not been
     done — only headless before/after comparisons in a browser;
   - behaviour change: Preview edits (metadata, tracks, storage path) now survive switching tabs and
     visiting the channel editor (previously reset to the server answer). Keep it, or dispose the model
     on leaving (one line in `PreviewEntry`)?
   - saving settings from the UI sends `extractorOverrides = {}` and `UpdateSystemSettingsUseCase`
     replaces the map wholesale, so overrides set another way are wiped on every UI save (behaviour
     unchanged by the step). Fix?
5. The plan file was still `status: draft` when the step closed; 01.13 set it to `done` as its stage
   file instructs — revert if the plan is meant to stay unreviewed.

### Stage log

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
- 2026-09-29 — 01.8 done: only `TransactionRunner` opens transactions (the per-repository transaction helper removed; repositories run
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
- 2026-09-29 — 01.11 done: `TgVideoDownloaderClient` returns `Either<ApiError, T>` (`Http`/`Network`/`Decoding`,
  one conversion point `ApiCall.kt`, cancellation rethrown), the client's exception type removed; all 23 `catch`/`runCatching`
  sites in `features` fold the result with the same user-visible messages; `WorkspaceGate` and the root
  `TgvdApp` moved to `features/app`, the shell keeps `main`, Koin and Telegram/JS interop only
  (`ShellSourceGuardTest`); 727 JVM tests.
- 2026-09-29 — 01.12 done: Preview and Settings run on Voyager `ScreenModel`s (`koinScreenModel`, base
  `FeatureScreenModel`: `StateFlow` state with `Async` loading/busy/error, `Channel` effects, one `onEvent`,
  flags lowered on every outcome) and are split into Entry/Content/ScreenModel/UiState/Event/section files (all
  ≤ 216 lines); screen-model tests on a fake client; `UiConventionsTest` ratchet on direct client injection
  (8 known screens); pixel-identical UI in a headless before/after comparison; 753 JVM tests.
- 2026-09-30 — 01.13 done: step closed — this entry; `docs/*.md` re-checked against the code (API contract:
  retry route, DTO fields, error table; schema after V3/V4; env-variable binding (`__` nests); SECURITY.md
  rewritten to the real auth/allow-list; TESTING.md examples replaced by the real tests); consolidated known
  issues and open questions; plan `done`, `CLAUDE.md` → no current phase.

## Known issues

Deliberately not fixed in Step 01 (behaviour changes, out of its scope). The stage that found each one is
in brackets; details in its executor notes.

**Behaviour and access**

- `saveAsRule.includeCategory` is accepted in `POST …/jobs` but ignored: `api:mapping`
  (`SaveAsRuleDto.toDomain()`) drops it and the domain has no such parameter.
- Joining a workspace by slug: `POST /workspaces` with a slug that is already taken adds the caller
  as `MEMBER` (`CreateWorkspaceUseCase`). The client relies on it to reconnect; a known access risk.
- A non-numeric `TELEGRAM_ALLOWED_USER_IDS` entry is dropped silently (open question 1). [01.5]
- `TELEGRAM_DEV_MODE=true` is the default in `docker-compose.yaml` and `.env.example` (local-dev
  compose). Since 01.5 the server logs a `WARN` at start and `DEPLOYMENT.md` warns.
- Saving settings from the UI wipes `extractorOverrides` (open question 4). [01.12]
- System settings are global: every allowed user can change them. [01.13]
- Compose CORS: the inline config has no `cors` section, so `cors.hosts` falls back to
  `application.yaml` (`localhost:8081`), while the compose webapp is served on `localhost:3000` —
  cross-origin calls from it may be refused; not verified in a browser. [01.13]

**Server and data**

- `VideoInfoCacheImpl.evictExpired` is never called — expired cache rows are ignored but not deleted.
- Unused config keys: `jobs.maxAttempts`, `jobs.retryDelayMs`, `logging.level`, `logging.format`, and
  also `ytDlp.timeout`, `ffmpeg.timeout` (no process timeout at all), `storage.*` (base directories are
  not enforced), `server.baseUrl`, `llm.*` (`CONFIGURATION.md` §7.1). [01.5, 01.13]
- Saved system settings (`system_settings`) replace the configured `ytDlp`/`proxy` sections as a whole,
  including deployment values such as `ytDlp.path` and `allowUpdate`. [01.13]
- Entity invariants still throw → `500`: a blank rule name or empty `outputs`, a blank channel or
  workspace name; adding an existing member (PK violation) → `500`; a malformed `metadata.releaseDate`
  in `POST …/jobs` → `500`; a preview URL without a scheme that yt-dlp accepts → `500`. [01.6, 01.7]
- `idx_jobs_active_video` is unique per `video_id` across all workspaces: an active job in one
  workspace gives `409` in another. [01.4]
- `DownloadHistoryEntryDto.status` is the upper-case enum name, `JobDto.status` lower case (frozen
  wire). `CreateJobRequestDto.category` is not read by the server. [01.4, 01.6]
- Stored `jobs.error` keeps `code`/`details`/`retryable` but only `message` reaches the domain;
  `jobs.progress.message` is dropped; a completed job has `progress = null`. [01.4, 01.8]
- `POST_PROCESSING` is never written (conversion runs as `DOWNLOADING` / phase `CONVERT`). [01.13]
- A `DatabaseFailed` on a processor CAS write leaves the job `DOWNLOADING` until the next start;
  `job_outputs` rows are written before the final CAS, so a job cancelled at the last moment may keep
  them. [01.9]
- `YtDlpBootstrap` / `YtDlpServiceImpl` still wait with a blocking `waitFor()`. [01.9]
- `TelegramAuthValidator` reads the system clock instead of an injected `Clock` (G9 gap); `initData`
  has no unit test of its own (covered through the routes). [01.13]
- `DomainError.InvalidUrl` and `PathTraversalAttempt` are mapped but never produced;
  `FileNameValidator.sanitize` has no length limit. [01.13]
- A create response carries the use-case's nanosecond timestamp, the stored value has microseconds. [01.8]

**Delivery and tooling**

- See open question 3 (CI not yet run; `Dockerfile.ci` without bgutil; Node 20; unpinned images).
- The JSON appender in `logback.xml` is not referenced; logs are text only. [01.13]
- The header of `Dockerfile.tgvd-server` mentions `docker compose build tgvd-server` — there is no
  such compose service. [01.13]
- `tgminiapp` crashes at start without `telegram-web-app.js` (`window.Telegram` missing); deprecated
  `Modifier.menuAnchor()` / `centerAlignedTopAppBarColors` in `features`. [01.11, 01.12]
- The `workspaceSlug` of the API client is set through a cast to `TgVideoDownloaderClientImpl`. [01.11]
- File names in `domain-test-fixtures` clash with `domain` on the JVM classpath (`…Kt` facade
  classes), so the fixtures are not on `server:infra`'s test classpath. [01.7]
- On JS an invalid regex throws something other than `IllegalArgumentException` (three
  `RuleMatchTest` cases fail on JS; the JS test runner is off). [01.1, 01.2, 01.7]
- Detekt 1.23 uses a Gradle API removed in Gradle 10 — Detekt 2.x is needed before that upgrade. [01.3]
- `README.md` still advertises LLM metadata (Gemini/OpenAI) and automatic retries — neither exists. [01.1]
