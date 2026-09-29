---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ PROJECT_CONTEXT.md, ../AGENTS.md ]
---

# Testing

> **Purpose**: Testing strategy, KMP tests, and examples.

---

## 1. Testing Stack

### By Source Set

| Source set   | Libraries                                  | Purpose                                              |
|--------------|--------------------------------------------|------------------------------------------------------|
| `commonTest` | Kotest framework-engine, Kotest assertions | Domain, mapping, use-cases (KMP-compatible)          |
| `jvmTest`    | Kotest runner-junit5, MockK                | Integration tests, mocking                           |
| `jvmTest`    | Testcontainers                             | PostgreSQL in tests                                  |
| `jvmTest`    | Ktor Test                                  | HTTP tests without a real server                     |

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

// JVM modules (server:*)
dependencies {
    testImplementation(libs.bundles.testing)           // Kotest runner/assertions/property, MockK, coroutines-test
    testImplementation(libs.bundles.testcontainers)    // Testcontainers 2.x: core, postgresql, junit-jupiter
    testImplementation(libs.ktor.server.test.host)
}
```

---

## 2. Test Structure

### KMP Modules (domain, api:mapping, api:client)

```
domain/src/commonTest/kotlin/io/github/alelk/tgvd/domain/
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
│   └── MetadataResolverTest.kt
├── storage/
│   └── PathTemplateEngineTest.kt
├── job/
│   ├── CreateJobUseCaseTest.kt     # every validation branch: same field and message as before 01.6
│   └── JobUseCasesTest.kt          # list / get / cancel / retry, another workspace's job = not found
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
├── fakes/                          # FakeWorkspaceRepository, FakeJobRepository, FakeRuleRepository,
│                                   # FakeChannelRepository (keep the port contract: another workspace
│                                   # = not found), FakeLlmPort, TestClock
├── fixtures/                       # object mothers (aWorkspace, aCreateJobRequest, aJob, aRule, aChannel…), Either asserts
├── track/                          # aTrackSelectionSettings (server defaults)
└── tx/NoopTransactionRunner.kt     # runs the block inline (test-only, not in domain since 01.7)

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

features/src/jvmTest/kotlin/io/github/alelk/tgvd/features/architecture/
└── ShellSourceGuardTest.kt         # fitness: no @Composable and no Compose foundation/material imports in tgminiapp/src
```

> Tests mirror the package-by-feature structure of the domain.
> `jvmTest/` is reserved for JVM-specific edge cases and source-scanning fitness tests (they need `java.io.File`); `jsTest/` would compile but not run (no JS test runner).

### JVM Modules (server:*)

```
server/infra/src/testFixtures/kotlin/.../server/infra/testing/
└── PostgresTestContainer.kt        # one postgres:16-alpine per test JVM; a fresh database per spec

server/infra/src/test/kotlin/.../server/infra/
├── db/MigrationsTest.kt            # empty DB → DatabaseFactory start path → Flyway validate(), ≥ V1…V8
├── db/repository/*ImplTest.kt      # round-trips of every repository under ExposedTransactionRunner
├── db/repository/JobStatusWritesTest.kt  # status CAS, claimNext (two parallel claims → one wins), requeue
├── service/JobProcessorSmokeTest.kt      # a pending job through the processor to COMPLETED
├── db/jsonb/JsonbFixturesTest.kt   # frozen JSONB fixtures (src/test/resources/jsonb-fixtures/)
├── service/SystemSettingsHolderTest.kt   # also as SystemSettingsStore: deployment-only values kept
├── llm/UnconfiguredLlmPortTest.kt  # G8: the unconfigured port previews exactly like the former null port
└── process/…                       # YtDlpRunner; ProcessCancellationTest (stub binary: cancel kills the tree)

server/app/src/test/kotlin/.../server/
├── ApiSurfaceTest.kt               # live routing tree == src/test/resources/api-surface.txt
├── route/*RoutesTest.kt            # module() + Testcontainers + fakes, devMode (X-Telegram-Init-Data: dev)
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

## 3. Unit Tests

> Located in `commonTest` for KMP modules. Uses Kotest `FunSpec` + Kotest assertions.
> For mocking in `commonTest` — use **fake implementations** (not MockK!).

### 3.1 RuleMatch Tests

```kotlin
class RuleMatchTest : FunSpec({

    context("ChannelId matching") {
        test("matches exact channel id") {
            val match = RuleMatch.ChannelId("UC123")
            val video = videoInfo(channelId = "UC123")
            
            match.matches(video) shouldBe true
        }
        
        test("does not match different channel id") {
            val match = RuleMatch.ChannelId("UC123")
            val video = videoInfo(channelId = "UC456")
            
            match.matches(video) shouldBe false
        }
    }
    
    context("AllOf (AND) matching") {
        test("matches when all conditions match") {
            val match = RuleMatch.AllOf(listOf(
                RuleMatch.ChannelName("Rick Astley"),
                RuleMatch.TitleRegex(".*Never.*"),
            ))
            val video = videoInfo(
                channelName = "Rick Astley",
                title = "Never Gonna Give You Up"
            )
            
            match.matches(video) shouldBe true
        }
        
        test("does not match when one condition fails") {
            val match = RuleMatch.AllOf(listOf(
                RuleMatch.ChannelName("Rick Astley"),
                RuleMatch.TitleRegex(".*Together.*"),
            ))
            val video = videoInfo(
                channelName = "Rick Astley",
                title = "Never Gonna Give You Up"
            )
            
            match.matches(video) shouldBe false
        }
        
        test("throws on empty list") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.AllOf(emptyList())
            }
        }
    }
    
    context("AnyOf (OR) matching") {
        test("matches when at least one condition matches") {
            val match = RuleMatch.AnyOf(listOf(
                RuleMatch.ChannelId("UC123"),
                RuleMatch.ChannelId("UC456"),
            ))
            val video = videoInfo(channelId = "UC456")
            
            match.matches(video) shouldBe true
        }
    }
    
    context("TitleRegex matching") {
        test("matches regex in title") {
            val match = RuleMatch.TitleRegex("S\\d{2}E\\d{2}")
            val video = videoInfo(title = "My Show S01E05 - Episode Title")
            
            match.matches(video) shouldBe true
        }
        
        test("throws on invalid regex") {
            shouldThrow<IllegalArgumentException> {
                RuleMatch.TitleRegex("[invalid")
            }
        }
    }
    
    context("specificity") {
        test("ChannelId has highest specificity") {
            RuleMatch.ChannelId("UC123").specificity() shouldBe 100
            RuleMatch.ChannelName("Name").specificity() shouldBe 80
            RuleMatch.UrlRegex(".*").specificity() shouldBe 60
            RuleMatch.TitleRegex(".*").specificity() shouldBe 40
        }
    }
})

// Test helpers
private fun videoInfo(
    videoId: String = "abc123",
    title: String = "Test Video",
    channelId: String = "UC123",
    channelName: String = "Test Channel",
    webpageUrl: String = "https://youtube.com/watch?v=abc123",
) = VideoInfo(
    videoId = VideoId(videoId),
    extractor = Extractor.YOUTUBE,
    title = title,
    channelId = ChannelId(channelId),
    channelName = channelName,
    uploadDate = null,
    duration = 180.seconds,
    webpageUrl = Url(webpageUrl),
)
```

### 3.2 ResolvedMetadata Tests

```kotlin
class ResolvedMetadataTest : FunSpec({

    context("MusicVideo validation") {
        test("valid music video") {
            val metadata = ResolvedMetadata.MusicVideo(
                artist = "Artist",
                title = "Title",
                releaseDate = LocalDate("2024-01-15"),
                tags = listOf("rock", "live"),
            )
            
            metadata.artist shouldBe "Artist"
            metadata.category shouldBe Category.MUSIC_VIDEO
            metadata.year shouldBe 2024
        }
        
        test("throws on blank artist") {
            shouldThrow<IllegalArgumentException> {
                ResolvedMetadata.MusicVideo(artist = "  ", title = "Title")
            }
        }
        
        test("throws on invalid releaseDate") {
            shouldThrow<IllegalArgumentException> {
                LocalDate("not-a-date")
            }
        }
    }
    
    context("tags normalization") {
        test("normalizes tags") {
            val tags = listOf("  Rock  ", "rock", "Pop", "  ", "pop")
            val normalized = tags.normalizeTags()
            
            normalized shouldContainExactly listOf("Rock", "Pop")
        }
    }
})
```

### 3.3 TelegramAuthValidator Tests

```kotlin
class TelegramAuthValidatorTest : FunSpec({

    val botToken = "123456:ABC-DEF"
    
    context("valid initData") {
        test("validates correct signature") {
            val validator = TelegramAuthValidator(botToken)
            val initData = buildValidInitData(botToken)
            
            val result = validator.validate(initData)
            
            result.shouldBeRight()
            result.getOrNull()!!.id.value shouldBe 12345L
        }
    }
    
    context("invalid initData") {
        test("rejects missing hash") {
            val validator = TelegramAuthValidator(botToken)
            val initData = "user=%7B%22id%22%3A12345%7D&auth_date=1234567890"
            
            val result = validator.validate(initData)
            
            result.shouldBeLeft()
            result.leftOrNull() shouldBe TelegramAuthValidator.AuthError.MissingHash
        }
        
        test("rejects invalid hash") {
            val validator = TelegramAuthValidator(botToken)
            val initData = "user=%7B%22id%22%3A12345%7D&auth_date=1234567890&hash=invalid"
            
            val result = validator.validate(initData)
            
            result.shouldBeLeft()
            result.leftOrNull() shouldBe TelegramAuthValidator.AuthError.InvalidHash
        }
        
        test("rejects expired auth_date") {
            val validator = TelegramAuthValidator(
                botToken,
                maxAgeSeconds = 3600,
                clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
            )
            val oldAuthDate = Instant.now().epochSecond - 7200  // 2 hours ago
            val initData = buildValidInitData(botToken, authDate = oldAuthDate)
            
            val result = validator.validate(initData)
            
            result.shouldBeLeft()
            result.leftOrNull() shouldBe TelegramAuthValidator.AuthError.Expired
        }
    }
    
    context("dev mode") {
        test("accepts 'dev' in dev mode") {
            val validator = TelegramAuthValidator(botToken, devMode = true)
            
            val result = validator.validate("dev")
            
            result.shouldBeRight()
            result.getOrNull()!!.id.value shouldBe 0L
        }
        
        test("rejects 'dev' when not in dev mode") {
            val validator = TelegramAuthValidator(botToken, devMode = false)
            
            val result = validator.validate("dev")
            
            result.shouldBeLeft()
        }
    }
})

// Helper to build valid initData for testing
private fun buildValidInitData(
    botToken: String,
    userId: Long = 12345L,
    authDate: Long = Instant.now().epochSecond,
): String {
    val user = """{"id":$userId,"first_name":"Test"}"""
    val params = mapOf(
        "user" to user,
        "auth_date" to authDate.toString(),
    )
    
    val dataCheckString = params.entries
        .sortedBy { it.key }
        .joinToString("\n") { "${it.key}=${it.value}" }
    
    val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
    val hash = hmacSha256(secretKey, dataCheckString.toByteArray()).toHexString()
    
    return params.entries.joinToString("&") { (k, v) ->
        "$k=${URLEncoder.encode(v, Charsets.UTF_8)}"
    } + "&hash=$hash"
}
```

### 3.4 PathTemplateEngine Tests

```kotlin
class PathTemplateEngineTest : FunSpec({

    val baseDirectories = listOf("/media")
    val engine = PathTemplateEngine(baseDirectories)
    
    context("template rendering") {
        test("renders simple template") {
            val template = "/media/Music/{artist}/{title}.mp4"
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "artist" to "Rick Astley",
                "title" to "Never Gonna Give You Up",
            ))
            
            val result = engine.render(template, context)
            
            result.shouldBeRight()
            result.getOrNull() shouldBe 
                FilePath("/media/Music/Rick Astley/Never Gonna Give You Up.mp4")
        }
        
        test("sanitizes forbidden characters") {
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "title" to "Video: Part 1/2 <HD>",
            ))
            
            val result = engine.render("/media/{title}.mp4", context)
            
            result.shouldBeRight()
            result.getOrNull() shouldBe FilePath("/media/Video_ Part 1_2 _HD_.mp4")
        }
        
        test("handles missing variable") {
            val template = "/media/{artist}/{title}.mp4"
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "title" to "Title",
                // artist missing
            ))
            
            val result = engine.render(template, context)
            
            result.shouldBeRight()
            result.getOrNull().toString() shouldBe "/media//Title.mp4"
        }
    }
    
    context("path traversal protection") {
        test("rejects path outside base directories") {
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "title" to "../../../etc/passwd",
            ))
            
            val result = engine.render("/media/{title}", context)
            
            result.shouldBeLeft()
            result.leftOrNull() shouldBeInstanceOf DomainError.PathTraversalAttempt::class
        }
        
        test("allows normalized paths within base") {
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "title" to "folder/../actual/title",
            ))
            
            val result = engine.render("/media/{title}.mp4", context)
            
            result.shouldBeRight()
        }
    }
    
    context("filename length") {
        test("truncates long filenames") {
            val longTitle = "A".repeat(300)
            val context = PathTemplateEngine.TemplateContext(mapOf(
                "title" to longTitle,
            ))
            
            val result = engine.render("/media/{title}.mp4", context)
            
            result.shouldBeRight()
            val filename = result.getOrNull()!!.fileName.toString()
            filename.length shouldBeLessThanOrEqual 184  // 180 + ".mp4"
        }
    }
})
```

### 3.5 RuleMatchingService Tests

```kotlin
class RuleMatchingServiceTest : FunSpec({

    val service = RuleMatchingService()
    
    context("findMatchingRule") {
        test("returns rule with highest priority") {
            val rules = listOf(
                rule(id = "1", priority = 10, match = RuleMatch.ChannelName("Test")),
                rule(id = "2", priority = 20, match = RuleMatch.ChannelName("Test")),
                rule(id = "3", priority = 5, match = RuleMatch.ChannelName("Test")),
            )
            val video = videoInfo(channelName = "Test")
            
            val result = service.findMatchingRule(video, rules)
            
            result?.id?.value.toString() shouldBe "2"
        }
        
        test("uses specificity as tiebreaker") {
            val rules = listOf(
                rule(id = "1", priority = 10, match = RuleMatch.TitleRegex(".*")),
                rule(id = "2", priority = 10, match = RuleMatch.ChannelId("UC123")),
            )
            val video = videoInfo(channelId = "UC123", title = "Test")
            
            val result = service.findMatchingRule(video, rules)
            
            result?.id?.value.toString() shouldBe "2"  // ChannelId is more specific
        }
        
        test("returns null when no rules match") {
            val rules = listOf(
                rule(match = RuleMatch.ChannelId("UC999")),
            )
            val video = videoInfo(channelId = "UC123")
            
            val result = service.findMatchingRule(video, rules)
            
            result shouldBe null
        }
        
        test("ignores disabled rules") {
            val rules = listOf(
                rule(enabled = false, priority = 100, match = RuleMatch.ChannelName("Test")),
                rule(enabled = true, priority = 10, match = RuleMatch.ChannelName("Test")),
            )
            val video = videoInfo(channelName = "Test")
            
            val result = service.findMatchingRule(video, rules)
            
            result?.priority shouldBe 10
        }
    }
})

private fun rule(
    id: String = UUID.randomUUID().toString(),
    enabled: Boolean = true,
    priority: Int = 0,
    match: RuleMatch,
    category: Category = Category.OTHER,
) = Rule(
    id = RuleId(UUID.fromString(id.padStart(36, '0').take(36))),
    enabled = enabled,
    priority = priority,
    match = match,
    category = category,
    metadataTemplate = MetadataTemplate(),
    downloadPolicy = DownloadPolicy(),
    storagePolicy = StoragePolicy(originalTemplate = "/media/{title}.mp4"),
    postProcessPolicy = PostProcessPolicy(),
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)
```

---

## 4. Mapping Tests

```kotlin
class RuleMatchMappingTest : FunSpec({

    context("toDto") {
        test("maps ChannelId correctly") {
            val domain = RuleMatch.ChannelId("UC123")
            val dto = domain.toDto()
            
            dto shouldBe RuleMatchDto.ChannelId("UC123")
        }
        
        test("maps nested AllOf correctly") {
            val domain = RuleMatch.AllOf(listOf(
                RuleMatch.ChannelName("Test"),
                RuleMatch.TitleRegex(".*"),
            ))
            val dto = domain.toDto()
            
            dto shouldBe RuleMatchDto.AllOf(listOf(
                RuleMatchDto.ChannelName("Test", ignoreCase = true),
                RuleMatchDto.TitleRegex(".*"),
            ))
        }
    }
    
    context("toDomain") {
        test("maps valid ChannelId") {
            val dto = RuleMatchDto.ChannelId("UC123")
            val result = dto.toDomain()
            
            result.shouldBeRight()
            result.getOrNull() shouldBe RuleMatch.ChannelId("UC123")
        }
        
        test("rejects empty ChannelId") {
            val dto = RuleMatchDto.ChannelId("")
            val result = dto.toDomain()
            
            result.shouldBeLeft()
            result.leftOrNull()?.field shouldBe "value"
        }
        
        test("rejects empty AllOf") {
            val dto = RuleMatchDto.AllOf(emptyList())
            val result = dto.toDomain()
            
            result.shouldBeLeft()
        }
    }
    
    context("JSON serialization") {
        test("serializes with type discriminator") {
            val dto = RuleMatchDto.ChannelId("UC123")
            val json = Json.encodeToString(RuleMatchDto.serializer(), dto)
            
            json shouldContain """"type":"channel-id""""
            json shouldContain """"value":"UC123""""
        }
        
        test("deserializes nested structure") {
            val json = """
                {
                    "type": "all-of",
                    "matches": [
                        { "type": "channel-id", "value": "UC123" },
                        { "type": "title-regex", "pattern": ".*Test.*" }
                    ]
                }
            """.trimIndent()
            
            val dto = Json.decodeFromString<RuleMatchDto>(json)
            
            dto shouldBeInstanceOf RuleMatchDto.AllOf::class
            (dto as RuleMatchDto.AllOf).matches.size shouldBe 2
        }
    }
})
```

---

## 5. Integration Tests

> Not implemented yet (see §2) — the code below is the intended shape.

### 5.1 Repository Tests

```kotlin
class RuleRepositoryTest : FunSpec({

    val postgres = PostgreSQLContainer("postgres:16")
        .withDatabaseName("tgvd_test")
        .withUsername("test")
        .withPassword("test")
    
    lateinit var database: Database
    lateinit var repository: RuleRepository
    
    beforeSpec {
        postgres.start()
        database = Database.connect(
            url = postgres.jdbcUrl,
            user = postgres.username,
            password = postgres.password,
        )
        
        // Run migrations
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        
        repository = RuleRepositoryImpl(database)
    }
    
    afterSpec {
        postgres.stop()
    }
    
    beforeEach {
        // Clean table
        transaction(database) {
            exec("TRUNCATE rules CASCADE")
        }
    }
    
    test("save and findById") {
        val rule = rule(match = RuleMatch.ChannelId("UC123"))
        
        repository.save(rule)
        val found = repository.findById(rule.id)
        
        found shouldNotBe null
        found!!.match shouldBe rule.match
    }
    
    test("findAll with enabled filter") {
        repository.save(rule(enabled = true))
        repository.save(rule(enabled = false))
        repository.save(rule(enabled = true))
        
        val enabled = repository.findAll(enabled = true)
        val all = repository.findAll()
        
        enabled.size shouldBe 2
        all.size shouldBe 3
    }
    
    test("findAll orders by priority descending") {
        repository.save(rule(priority = 10))
        repository.save(rule(priority = 30))
        repository.save(rule(priority = 20))
        
        val rules = repository.findAll()
        
        rules.map { it.priority } shouldBe listOf(30, 20, 10)
    }
    
    test("delete removes rule") {
        val rule = rule()
        repository.save(rule)
        
        repository.delete(rule.id)
        
        repository.findById(rule.id) shouldBe null
    }
})
```

### 5.2 Route Tests

```kotlin
class PreviewRoutesTest : FunSpec({

    test("POST /api/v1/workspaces/{id}/preview returns preview") {
        testApplication {
            application {
                configureTestApp()
            }
            
            val response = client.post("/api/v1/workspaces/$testWorkspaceId/preview") {
                header("X-Telegram-Init-Data", "dev")
                contentType(ContentType.Application.Json)
                setBody("""{"url": "https://youtube.com/watch?v=dQw4w9WgXcQ"}""")
            }
            
            response.status shouldBe HttpStatusCode.OK
            
            val body = response.bodyAsText()
            body shouldContain """"videoId":"dQw4w9WgXcQ""""
            body shouldContain """"category":"""
        }
    }
    
    test("POST /api/v1/workspaces/{id}/preview returns 400 for invalid URL") {
        testApplication {
            application {
                configureTestApp()
            }
            
            val response = client.post("/api/v1/workspaces/$testWorkspaceId/preview") {
                header("X-Telegram-Init-Data", "dev")
                contentType(ContentType.Application.Json)
                setBody("""{"url": "not-a-url"}""")
            }
            
            response.status shouldBe HttpStatusCode.BadRequest
            
            val body = response.bodyAsText()
            body shouldContain """"code":"INVALID_URL""""
        }
    }
    
    test("returns 401 without initData") {
        testApplication {
            application {
                configureTestApp()
            }
            
            val response = client.post("/api/v1/workspaces/$testWorkspaceId/preview") {
                contentType(ContentType.Application.Json)
                setBody("""{"url": "https://youtube.com/watch?v=abc"}""")
            }
            
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

fun Application.configureTestApp() {
    install(Koin) {
        modules(testModule)
    }
    install(ContentNegotiation) {
        json()
    }
    configureRouting()
}

val testModule = module {
    single { TelegramAuthValidator("test-token", devMode = true) }
    single<VideoInfoExtractor> { MockVideoInfoExtractor() }
    // ... other fakes/mocks
}
```

---

## 6. E2E Tests (Smoke)

```kotlin
class DownloadFlowTest : FunSpec({

    // This test requires a real yt-dlp installation and is run manually
    tags(Tag("e2e"))
    
    test("full download flow").config(enabled = false) {
        // 1. Create preview
        val previewResponse = client.post("/api/v1/workspaces/$testWorkspaceId/preview") {
            // ...
        }
        
        // 2. Create job
        val jobResponse = client.post("/api/v1/workspaces/$testWorkspaceId/jobs") {
            // ...
        }
        
        // 3. Poll until done
        eventually(5.minutes) {
            val job = client.get("/api/v1/workspaces/$testWorkspaceId/jobs/${jobId}")
            job.status shouldBe "done"
        }
        
        // 4. Verify file exists
        Files.exists(Path.of(expectedPath)) shouldBe true
    }
})
```

---

## 7. Property-Based Tests

```kotlin
class RuleMatchPropertyTest : FunSpec({

    context("serialization roundtrip") {
        test("any RuleMatch survives serialization") {
            checkAll(Arb.ruleMatch()) { match ->
                val dto = match.toDto()
                val json = Json.encodeToString(dto)
                val deserialized = Json.decodeFromString<RuleMatchDto>(json)
                val domain = deserialized.toDomain().getOrNull()
                
                // Structural equality
                domain shouldBe match
            }
        }
    }
})

fun Arb.Companion.ruleMatch(depth: Int = 3): Arb<RuleMatch> = arbitrary {
    if (depth <= 0) {
        // Leaf nodes only
        listOf(
            RuleMatch.ChannelId(Arb.string(5..20).bind()),
            RuleMatch.ChannelName(Arb.string(3..50).bind()),
            RuleMatch.TitleRegex(".*${Arb.string(1..10).bind()}.*"),
        ).random()
    } else {
        listOf(
            RuleMatch.ChannelId(Arb.string(5..20).bind()),
            RuleMatch.ChannelName(Arb.string(3..50).bind()),
            RuleMatch.TitleRegex(".*"),
            RuleMatch.AllOf(Arb.list(ruleMatch(depth - 1), 1..3).bind()),
            RuleMatch.AnyOf(Arb.list(ruleMatch(depth - 1), 1..3).bind()),
        ).random()
    }
}
```

---

## 8. Test Configuration

### 8.1 build.gradle.kts

```kotlin
// server/transport/build.gradle.kts (actual) — versions come from gradle/libs.versions.toml
dependencies {
    testImplementation(libs.bundles.testing)          // Kotest runner/assertions/property, MockK, coroutines-test
    testImplementation(libs.ktor.server.test.host)
}
// useJUnitPlatform() comes from the tgvd.jvm convention plugin
```

> `server:infra` and `server:app` add `libs.bundles.testcontainers`. There is no `e2e` tag filter
> and no `e2eTest` task in the build; all tests run in `./gradlew build`.

### 8.2 Kotest Config

```kotlin
// src/test/kotlin/ProjectConfig.kt
object ProjectConfig : AbstractProjectConfig() {
    override val parallelism = 4
    
    override fun extensions() = listOf(
        // Add extensions
    )
}
```

---

## 9. Coverage Targets

- **Domain**: 90%+
- **Mapping**: 90%+
- **Security**: 95%+
- **Routes**: 80%+
- **Repositories**: 70%+ (integration tests)

