# ADR-001: Technology Stack

**Status**: Accepted  
**Date**: 2026-02-25  
**Authors**: Alex Elkin

---

## Context

We need to choose a technology stack for a video downloading service supporting various platforms
(YouTube, RuTube, VK Video, 1000+ sites via yt-dlp) managed through a Telegram Mini App.

Requirements:
- A single language for backend, shared logic, and frontend
- Kotlin Multiplatform for code sharing
- Type-safe API between client and server
- Simple deployment
- Good async support
- Ability to add other UI platforms (desktop, Android, web) in the future

---

## Decision

### Backend (JVM only)

| Component         | Choice                   | Alternatives          | Rationale                                    |
|-------------------|--------------------------|-----------------------|----------------------------------------------|
| **Language**      | Kotlin 2.3+ (KMP)        | Java, Scala           | KMP, sealed classes, coroutines              |
| **JVM**           | 21 LTS                   | 17                    | Performance improvements                     |
| **Framework**     | Ktor 3.x                 | Spring Boot           | Lightweight, Kotlin-first, coroutines-native |
| **DI**            | Koin 4.x                 | Kodein, Dagger        | Simple DSL, KMP-compatible                   |
| **Serialization** | kotlinx.serialization    | Jackson, Gson         | Compile-time, KMP, sealed classes support    |
| **Database**      | PostgreSQL 16            | MySQL, SQLite         | JSONB, reliability, production-ready         |
| **ORM**           | Exposed                  | Ktorm, jOOQ           | Kotlin DSL, type-safe, active development    |
| **Migrations**    | Flyway                   | Liquibase             | Simple, SQL files                            |
| **Config**        | Hoplite                  | Konf, Typesafe Config | Kotlin DSL, env support, profiles            |
| **Logging**       | kotlin-logging + Logback | Log4j2                | SLF4J compatibility, structured logging      |

### Shared (KMP: jvm + js)

| Component         | Choice                   | Alternatives          | Rationale                                         |
|-------------------|--------------------------|-----------------------|---------------------------------------------------|
| **Domain**        | Pure Kotlin (KMP)        | —                     | Pure Kotlin, no frameworks                        |
| **Either**        | Arrow                    | kotlin.Result         | KMP, rich API, monad comprehensions               |
| **Timestamps**    | kotlin.time.Instant      | java.time.Instant     | In stdlib since Kotlin 2.1.20+, KMP               |
| **UUID**          | kotlin.uuid.Uuid         | java.util.UUID        | In stdlib since Kotlin 2.0+, KMP                  |
| **Coroutines**    | kotlinx-coroutines       | —                     | Standard async in Kotlin                          |

### Frontend / UI

| Component       | Choice                | Alternatives | Rationale                                          |
|-----------------|-----------------------|--------------|----------------------------------------------------|
| **UI**          | Compose Multiplatform | React, Vue   | Single Kotlin stack, type-safe, reusable           |
| **features**    | Compose KMP (jvm, js) | —            | UI components shared between shell applications    |
| **tgminiapp**   | JS (Browser)          | Wasm         | Telegram WebApp API compatibility                  |
| **HTTP Client** | Ktor Client (KMP)     | Fetch API    | KMP, type-safe, shared DTOs                        |
| **DI (client)** | Koin Compose          | —            | KMP-compatible, Compose integration                |

### Infrastructure

| Component            | Choice              | Alternatives    | Rationale                              |
|----------------------|---------------------|-----------------|----------------------------------------|
| **Video download**   | yt-dlp (subprocess) | youtube-dl, API | Best platform support, active updates  |
| **Video processing** | ffmpeg              | —               | Industry standard                      |
| **Containerization** | Docker              | Podman          | Universal, widely supported            |

### Testing

| Component       | Choice                    | Where        | Rationale                              |
|-----------------|---------------------------|--------------|----------------------------------------|
| **Framework**   | Kotest 6 framework-engine | commonTest   | KMP-compatible (jvm, js, native)       |
| **Assertions**  | Kotest assertions         | commonTest   | KMP-compatible, rich matchers          |
| **JVM runner**  | Kotest runner-junit5      | jvmTest      | IDE integration, BDD style             |
| **Mocking**     | MockK                     | jvmTest only | Kotlin-first, coroutines support       |
| **Integration** | Testcontainers            | jvmTest only | Real DB, reliable integration tests    |

---

## Consequences

### Positive

- Single Kotlin language across all platforms (KMP)
- Type-safe API between client and server via shared DTOs
- UI components written once in `features`
- Sealed classes, coroutines — work identically in `commonMain`
- Adding a new UI platform = creating a thin shell module

### Negative

- Compose Multiplatform for Web is less mature than React
- Fewer ready-made UI components for the web target
- KMP Gradle setup is more complex than pure JVM
- MockK does not work in `commonTest` (jvmTest only)
- Kotest JS/Native engine has limitations (no annotation-based config)

### Risks

- **Compose Web stability**: Monitor releases, maintain a fallback plan
- **yt-dlp breaking changes**: Pin versions, test after updates
- **KMP bundle size**: JS bundle may be large — configure tree-shaking

---

## References

- [Ktor Documentation](https://ktor.io/docs/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
