# ADR-003: Module Architecture

**Status**: Accepted  
**Date**: 2026-02-25  
**Authors**: Alex Elkin

---

## Context

The project includes:
- Backend (Ktor, JVM)
- Telegram Mini App (Compose Multiplatform, JS)
- Shared domain models and DTOs (KMP)
- Potential future targets: desktop, Android, web applications

We need to define the Gradle module structure to achieve:
- Clear separation of concerns
- Code reuse via KMP
- Independent development and testing
- Dependency control

---

## Decision

### Module Structure

```
tg-video-downloader/
├── domain/              # Pure business logic (KMP: jvm, js)
├── api/
│   ├── contract/        # API DTOs (KMP: jvm, js)
│   ├── mapping/         # Domain <-> DTO mapping (KMP: jvm, js)
│   ├── client/          # HTTP client (KMP: jvm, js)
│   └── client/di/       # Client DI wiring (KMP: jvm, js)
├── features/            # UI components, Compose Multiplatform (KMP: jvm, js)
├── tgminiapp/           # Telegram Mini App shell (JS only)
├── server/
│   ├── infra/           # Repositories, external processes, LLM (JVM only)
│   ├── transport/       # Ktor routes, middleware (JVM only)
│   ├── di/              # Koin modules (JVM only)
│   └── app/             # Entrypoint (JVM only)
└── docs/                # Documentation
```

### Dependency Graph

```
                    tgminiapp (JS)
                        │
                        ▼
                    features (KMP)
                        │
            ┌───────────┼────────────┐
            │           │            │
            ▼           ▼            ▼
      api:client     domain    api:mapping
            │                       │
      api:client:di          api:contract
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
  server:transport  server:infra  server:di
          │             │             │
          └─────────────┼─────────────┘
                        │
                   server:app
```

### Dependency Rules

| Module             | May depend on                                          | Must NOT depend on             |
|--------------------|--------------------------------------------------------|--------------------------------|
| `domain`           | Kotlin stdlib, Arrow, kotlinx-coroutines               | Everything else                |
| `api:contract`     | Kotlin stdlib, kotlinx.serialization                   | domain, server:*, features     |
| `api:mapping`      | domain, api:contract, Arrow                            | server:*, api:client, features |
| `api:client`       | api:contract, Ktor Client                              | domain, server:*, features     |
| `api:client:di`    | api:client, Koin, Ktor engine                          | domain, server:*, features     |
| `features`         | domain, api:client, api:mapping, Compose, Koin         | server:*, tgminiapp            |
| `tgminiapp`        | features, api:client:di                                | server:*, domain directly      |
| `server:infra`     | domain, api:contract                                   | transport, di, app, features   |
| `server:transport` | domain, api:contract, api:mapping, Ktor Server         | di, app, features              |
| `server:di`        | domain, server:infra, server:transport, Koin           | app, features                  |
| `server:app`       | All server modules                                     | features, tgminiapp            |

---

## Rationale

### Why a separate `domain` module (KMP)?

- **Framework independence**: pure Kotlin, testable without Ktor/DB
- **Reuse**: the same models on server and client
- **KMP**: sealed classes, enums — work identically on JVM and JS

### Why `api:contract` is separate from `domain`?

- **API stability**: the contract is versioned independently
- **Different invariants**: DTOs may have nullable where domain requires non-null
- **Different annotations**: `@Serializable`, `@SerialName`

### Why is `api:mapping` KMP?

- Mapping is needed on the server (`server:transport`) and on the client (`features`)
- Allows `features` to work with domain models instead of raw DTOs

### Why is `api:client:di` a separate module?

- Platform-specific engine selection (CIO for JVM, Js for browser)
- `api:client` remains clean — unaware of the specific engine
- Koin wiring is isolated

### Why is `features` a separate KMP module?

- All Compose UI components are reused across shell applications
- `tgminiapp` is a thin wrapper with Telegram-specific glue code
- Adding a new platform = new shell module depending on `features`

### Why split `server:*` into multiple modules?

| Module             | Responsibility                                |
|--------------------|-----------------------------------------------|
| `server:infra`     | "Dirty" work: DB, processes, FS, LLM          |
| `server:transport` | HTTP: routes, middleware, validation           |
| `server:di`        | Dependency wiring                             |
| `server:app`       | Entry point, configuration                    |

---

## Gradle Configuration

### settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

// === Domain (KMP) ===
include(":domain")

// === API (KMP) ===
include(":api:contract")
include(":api:mapping")
include(":api:client")
include(":api:client:di")

// === Server (JVM only) ===
include(":server:infra")
include(":server:transport")
include(":server:di")
include(":server:app")

// === UI (KMP) ===
include(":features")
include(":tgminiapp")
```

### domain/build.gradle.kts Example

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
```

### features/build.gradle.kts Example

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(projects.domain)
            implementation(projects.api.client)
            implementation(projects.api.mapping)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
```

### server:infra/build.gradle.kts Example

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.domain)
    implementation(projects.api.contract)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
}
```

---

## Consequences

### Positive

- Clear boundaries of responsibility
- Dependency control at the Gradle level
- Code reuse across platforms (KMP)
- UI components written once (`features`)
- Easy to test in isolation

### Negative

- Initial overhead for KMP setup
- More `build.gradle.kts` files
- Not all testing tools support KMP (MockK → jvmTest only)

### Evolution

As the project grows, you can:
- Extract `core/` for shared utilities
- Split `features` into feature modules (`features:preview`, `features:jobs`)
- Add new targets (iOS, macOS, Android) to KMP modules

---

## References

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Gradle Multi-project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [KMP Project Structure](https://kotlinlang.org/docs/multiplatform-discover-project.html)
