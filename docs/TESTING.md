---
status: stable
owner: Alex (alelk)
updated: 2026-09-30
related: [ PROJECT_CONTEXT.md, ../AGENTS.md ]
---

# Testing

> **Purpose**: Testing strategy, KMP tests, and examples.

---

## 1. Testing Stack

### By Source Set

| Source set                         | Libraries                                              | Purpose                                              |
|------------------------------------|--------------------------------------------------------|------------------------------------------------------|
| KMP `commonTest`                   | Kotest framework-engine, assertions (+ property in `domain`, coroutines-test in `features`/`api:client`) | Domain, mapping, contract, screen models (KMP-compatible) |
| KMP `jvmTest`                      | Kotest runner-junit5                                   | runs `commonTest` on the JVM; source-scanning fitness tests, golden JSON, client tests (`ktor-client-mock`) |
| server `src/test` (`server:infra`, `server:app`) | `bundles.testing` (Kotest, MockK, coroutines-test), Testcontainers | repositories, migrations, processor, routes on PostgreSQL |
| server `src/test` (`server:app`, `server:transport`) | Ktor test host                              | routes through `module()` without a real server      |

MockK is used in one place only (`server/infra/.../process/YtDlpRunnerTest.kt`); everything else uses
fakes.

> **Kotest 6 runs on the JVM only**, through JUnit 5 (`kotest-runner-junit5`). The Kotest Gradle
> plugin and KSP are **not** used (their JS compiler plugin lags the Kotlin compiler). `commonTest`
> is still compiled for JS — `compileTestKotlinJs` is part of `./gradlew build` and keeps shared
> tests free of JVM-only APIs — but the JS test runner is disabled (ADR-009, G11).
> Consequence: a test in `commonTest` must also *compile* for JS, but it only *runs* on the JVM.
> **MockK** does not support JS. In `commonTest`, use **fake implementations** of interfaces for mocking.

### Dependencies

JUnit Platform for every `Test` task is configured by the `tgvd.kmp` / `tgvd.jvm` convention
plugins (`convention-plugins/`, see [ARCHITECTURE.md §4.2](ARCHITECTURE.md#42-convention-plugins));
modules declare only dependencies.

```kotlin
// KMP modules (domain, api:contract, features)
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner)         // kotest-runner-junit5
        }
    }
}

// server:app (server:infra is the same without the test host, plus its testFixtures;
// server:transport has testing + test host; server:di has testing + koin-test and no tests yet)
dependencies {
    testImplementation(libs.bundles.testing)           // Kotest runner/assertions/property, MockK, coroutines-test
    testImplementation(libs.bundles.testcontainers)    // Testcontainers 2.x: core, postgresql, junit-jupiter
    testImplementation(testFixtures(projects.server.infra))   // PostgresTestContainer
    testImplementation(libs.ktor.server.test.host)
}
```

---

## 2. Test Structure

### KMP Modules (domain, api:contract, api:mapping, api:client, features)

```
domain/src/commonTest/kotlin/io/github/alelk/tgvd/domain/
├── common/
│   ├── TagTest.kt
│   └── ValueClassValidationTest.kt
├── rule/
│   ├── RuleMatchTest.kt
│   ├── RuleMatchingServiceTest.kt
│   └── RuleUseCasesTest.kt         # list / get / create / update / delete, another workspace's rule = not found
├── channel/
│   ├── ChannelTest.kt
│   └── ChannelUseCasesTest.kt      # filters, tags, CRUD, another workspace's channel = not found
├── system/
│   ├── YtDlpVersionTest.kt
│   └── SystemUseCasesTest.kt       # settings normalisation and kept secrets, yt-dlp status/update
├── metadata/
│   ├── ResolvedMetadataTest.kt
│   ├── MetadataResolverTest.kt
│   ├── MetadataTemplateMergerTest.kt   # mergeTemplates()
│   └── test/ResolvedMetadataTestHelpers.kt
├── storage/
│   ├── PathTemplateEngineTest.kt
│   ├── OutputFormatTest.kt
│   └── TrackPreferencesTest.kt
├── job/
│   ├── CreateJobUseCaseTest.kt     # every validation branch: same field and message as before 01.6
│   ├── JobStatusTest.kt            # the transition table, every pair
│   ├── JobUseCasesTest.kt          # list / get / cancel / retry, another workspace's job = not found
│   └── SaveAsRuleBuilderTest.kt
├── preview/
│   ├── PreviewUseCaseTest.kt
│   ├── PreviewVideoUseCaseTest.kt
│   └── DefaultMediaSelectionTest.kt
├── track/
│   ├── AudioTrackSelectorTest.kt
│   └── SubtitleSelectorTest.kt
└── workspace/
    ├── WorkspaceAccessTest.kt
    └── WorkspaceUseCasesTest.kt    # list, create/join by slug, members: list (member), add/remove (OWNER)

domain/src/jvmTest/kotlin/io/github/alelk/tgvd/domain/architecture/
└── DomainPurityTest.kt             # fitness: no Ktor/Exposed/serialization/Koin, no Clock.System in commonMain

domain/domain-test-fixtures/src/commonMain/kotlin/io/github/alelk/tgvd/domain/
├── common/, job/, metadata/, rule/, storage/, video/, workspace/
│                                   # Kotest Arb generators for domain models (e.g. Arb.ruleMatch(maxDepth = 2))
├── fakes/                          # FakeWorkspaceRepository, FakeJobRepository, FakeRuleRepository,
│                                   # FakeChannelRepository (keep the port contract: another workspace
│                                   # = not found), FakeLlmPort, TestClock
├── fixtures/                       # object mothers (aWorkspace, aCreateJobRequest, aJob, aRule, aChannel…), Either asserts
├── track/                          # aTrackSelectionSettings (server defaults)
└── tx/NoopTransactionRunner.kt     # runs the block inline (test-only, not in domain since 01.7)

api/contract/src/commonTest/kotlin/io/github/alelk/tgvd/api/contract/
├── common/ApiErrorDtoTest.kt
├── job/CreateJobRequestDtoTest.kt
├── metadata/MetadataTemplateDtoTest.kt, ResolvedMetadataDtoTest.kt
├── preview/PreviewResponseDtoTest.kt
├── rule/RuleMatchDtoTest.kt
└── storage/OutputFormatDtoTest.kt, StorageDtoTest.kt

api/mapping/src/commonTest/kotlin/io/github/alelk/tgvd/api/mapping/
├── common/ParseTest.kt             # parseId / parseValue: bad input → ValidationError, never an exception
├── job/CreateJobRequestMappingTest.kt
├── preview/VideoPreviewMappingTest.kt
├── rule/RuleMappingTest.kt         # invalid regex / tag / blank path template → ValidationError
├── channel/ChannelMappingTest.kt   # query filter precedence, blank ids, malformed tags
├── workspace/WorkspaceMappingTest.kt
└── system/SystemSettingsMappingTest.kt  # secrets never sent, proxy type parsing, update response

api/client/src/jvmTest/kotlin/io/github/alelk/tgvd/api/client/
└── TgVideoDownloaderClientImplTest.kt  # ktor-client-mock: 2xx decode, ApiErrorDto → ApiError.Http, non-DTO error
                                        # body, IOException / kotlin.Error → Network, broken JSON → Decoding,
                                        # 204, initData per request, cancellation is rethrown

features/src/commonTest/kotlin/io/github/alelk/tgvd/features/
├── fakes/FakeTgVideoDownloaderClient.kt  # settable results + recorded calls; `gate` holds calls in flight
├── fixtures/PreviewFixtures.kt     # aPreviewResponse, anAudioFormat, aChannelDto, aJobDto…
├── download/model/PreviewEditorValuesTest.kt, PreviewMediaOptionsTest.kt   # pure
├── download/screen/PreviewScreenModelTest.kt   # kotlinx-coroutines-test: channel check, debounced re-preview,
│                                   # refetch, job creation → JobCreated, failures lower the flags
├── settings/model/SettingsFormTest.kt          # DTO ↔ form round trip, active cookies source, defaults
└── settings/screen/SettingsScreenModelTest.kt  # load → form, load failure, save → Saved, yt-dlp update

features/src/jvmTest/kotlin/io/github/alelk/tgvd/features/architecture/
├── ShellSourceGuardTest.kt         # fitness: no @Composable and no Compose foundation/material imports in tgminiapp/src
└── UiConventionsTest.kt            # fitness: no koinInject<TgVideoDownloaderClient>() outside the KNOWN_* ratchet
```

Screen-model tests set `Dispatchers.setMain(StandardTestDispatcher())` (Voyager's `screenModelScope` runs on
`Dispatchers.Main.immediate`) and drive time with `runTest`/`advanceUntilIdle`.

> Tests mirror the package-by-feature structure of the domain.
> `jvmTest/` is reserved for JVM-specific edge cases and source-scanning fitness tests (they need `java.io.File`); `jsTest/` would compile but not run (no JS test runner).

### JVM Modules (server:*)

```
server/infra/src/testFixtures/kotlin/.../server/infra/testing/
└── PostgresTestContainer.kt        # one postgres:16-alpine per test JVM; a fresh database per spec

server/infra/src/test/kotlin/.../server/infra/
├── db/MigrationsTest.kt            # empty DB → DatabaseFactory start path → Flyway validate(), ≥ V1…V8
├── db/ExposedTransactionRunnerTest.kt    # nested runner joins the outer transaction; "a Left commits"
├── db/CatchingDbTest.kt            # rollback, SQLSTATE 23505 → domain conflicts, slug race
├── db/fixtures/                    # Mothers.kt, EitherAssertions.kt
├── db/repository/*ImplTest.kt      # round-trips of every repository under ExposedTransactionRunner
├── db/repository/JobStatusWritesTest.kt  # status CAS, claimNext (two parallel claims → one wins), requeue
├── service/JobProcessorSmokeTest.kt      # a pending job through the processor to COMPLETED
├── db/jsonb/JsonbFixturesTest.kt   # frozen JSONB fixtures (src/test/resources/jsonb-fixtures/;
│                                   # expectations in JsonbFixtureCases.kt, fixtures/JsonbFixtureFiles.kt)
├── service/SystemSettingsHolderTest.kt   # also as SystemSettingsStore: deployment-only values kept
├── llm/UnconfiguredLlmPortTest.kt  # G8: the unconfigured port previews exactly like the former null port
├── process/YtDlpRunnerTest.kt      # argument building (the one MockK user)
└── process/ProcessCancellationTest.kt    # stub binary: cancel kills the process tree

server/app/src/test/kotlin/.../server/
├── ApiSurfaceTest.kt               # live routing tree == src/test/resources/api-surface.txt
├── StartupTest.kt                  # failed migration / invalid config → module() throws before routing
├── config/ConfigValidationTest.kt, AllowListNormalizationTest.kt
├── config/ConfigBindingTest.kt     # the real loadConfig in a child JVM (ConfigBindingProbe.kt) — empty env → []
├── route/*RoutesTest.kt            # module() + Testcontainers + fakes, devMode (X-Telegram-Init-Data: dev):
│                                   # Workspace, Preview, Job, Rule, Channel, System, Health, AllowList,
│                                   # MalformedInput (G10), YtDlpUpdateDisabled
├── route/RouteTestSupport.kt, RouteFixtures.kt   # RouteTestApp harness, signedInitData, DEV_USER_ID = 1
├── job/JobLifecycleTest.kt         # THE MINE (01.9): cancel via route / stop() / restart mid-download
├── fakes/Fakes.kt                  # VideoInfoExtractor, VideoDownloader, YtDlpService
├── fakes/ControlledVideoDownloader.kt    # a download the test drives step by step; records its cancellation
└── telegram/MiniAppDeepLinkTest.kt

server/transport/src/test/kotlin/.../server/transport/architecture/
└── TransportSourceGuardTest.kt     # fitness: no repository and no server.infra in transport sources

api/contract/src/jvmTest/kotlin/.../api/contract/golden/
└── GoldenJsonTest.kt               # golden wire JSON (src/jvmTest/resources/golden/), both directions
```

> **Docker is required** for `./gradlew build` (Testcontainers). The safety net of Step 01 (stage 01.4):
> `api-surface.txt`, golden JSON and JSONB fixtures pin what clients and deployed databases already
> hold — if one of them fails, fix the code, not the snapshot. Only additions are allowed (a new route
> line, a new fixture file); JSONB fixtures and golden files are never regenerated.
> Route tests build the server with `module(config, startBackgroundServices = false, overrides = fakes)`;
> a second Telegram user is authenticated with a correctly signed `initData` (`signedInitData`).

---

## 3. Unit Tests (domain, `commonTest`)

- **Pure logic** — `RuleMatchTest` (`matches(MatchContext(video))`, `matchSpecificity()`),
  `PathTemplateEngineTest` (`render(template, video, metadata, format)` → `FilePath`),
  `JobStatusTest` (every transition pair), selectors in `track/`, value classes in `common/`.
- **Use-cases on fakes** — `*UseCasesTest`, `CreateJobUseCaseTest`, `PreviewUseCaseTest`: fakes from
  `domain-test-fixtures/fakes` honour the port contracts (a resource of another workspace is not
  found, `FakeJobRepository.transition` is a CAS), `NoopTransactionRunner` runs the block inline,
  `TestClock` pins time, object mothers come from `fixtures/`.
- **Property-based** — `checkAll` with the Arb generators of `domain-test-fixtures`
  (`RuleMatchTest`, `JobStatusTest`, `ValueClassValidationTest`, `OutputFormatTest`,
  `TrackPreferencesTest`).
- Telegram `initData` validation has no unit test of its own; it is covered through the routes
  (`WorkspaceRoutesTest`: missing header, foreign signature → 401; `AllowListRoutesTest`: signed user
  in / out of the allow-list; `signedInitData` in `RouteTestSupport.kt` signs like Telegram).

## 4. Mapping and Contract Tests

- `api:mapping` (`commonTest`): DTO → domain returns `Either` and never throws — bad ids, blank value
  classes, invalid regex/tags → `ValidationError` (`ParseTest`, `RuleMappingTest`, `ChannelMappingTest`…).
- `api:contract` (`commonTest`): serialization of each sealed DTO and its discriminator.
- `api:contract` (`jvmTest`): `GoldenJsonTest` — every golden file in `src/jvmTest/resources/golden/`
  decodes to its case and the case encodes to the same JSON (the wire is frozen, G1).

## 5. Integration Tests

### 5.1 Repository Tests

`server/infra/src/test/.../db/repository/*ImplTest.kt` on `PostgresTestContainer`
(`postgres:16-alpine`, one container per test JVM, a fresh database per spec, migrated through
`DatabaseFactory`). Every repository call runs inside `ExposedTransactionRunner` (repositories never
open transactions); repositories are built with an injected `Clock` (`RuleRepositoryImpl(clock)`, …).

### 5.2 Route Tests

`server/app/src/test/.../route/*RoutesTest.kt`: `RouteTestApp` builds the real application with
`module(config, startBackgroundServices = false, overrides = fakes)` on its own database, `devMode =
true` (`X-Telegram-Init-Data: dev` = user id `1`), paths use the workspace slug
(`/api/v1/workspaces/{workspaceSlug}/…`). One `TestApplication` per spec; never two concurrently
(Koin is global).

## 6. End-to-End Tests

There is no separate e2e suite and no `e2e` tag. The closest is `server/app/.../job/JobLifecycleTest`
(real `module()`, real `JobProcessor`, PostgreSQL, a `ControlledVideoDownloader`): cancel through the
route mid-download, `stop()` mid-download and restart, a `downloading` row before start — each ends in
the right status (`cancelled` / `completed`, wire values are lowercase).

## 7. Fitness Tests

Run inside `./gradlew build`; `KNOWN_*` lists only shrink, a red test means fix the code:
`ApiSurfaceTest` (server:app), `TransportSourceGuardTest` (server:transport), `DomainPurityTest`
(domain `jvmTest`), `ShellSourceGuardTest` and `UiConventionsTest` (features `jvmTest`).

## 8. Test Configuration

```kotlin
// server/transport/build.gradle.kts (actual) — versions come from gradle/libs.versions.toml
dependencies {
    testImplementation(libs.bundles.testing)          // Kotest runner/assertions/property, MockK, coroutines-test
    testImplementation(libs.ktor.server.test.host)
}
// useJUnitPlatform() comes from the tgvd.jvm convention plugin
```

> `server:infra` and `server:app` add `libs.bundles.testcontainers`. There is no `e2e` tag filter,
> no `e2eTest` task and no Kotest `ProjectConfig`; all tests run in `./gradlew build` with Kotest
> defaults (specs run sequentially).

## 9. Coverage

No numeric coverage target (the `kotlin-testing-strategy` skill). The aim instead: a use-case has a
happy path and a test per refusal, a repository a round-trip, a route its status codes, and a step's
mine a test proven red on a broken implementation.
