# ADR-005: Kotlin Multiplatform

**Status**: Accepted  
**Date**: 2026-02-25  
**Authors**: Alex Elkin

---

## Context

The project includes:
- Backend server (JVM)
- Telegram Mini App (JS, Browser)
- Potentially: desktop (macOS/Windows), Android, web without Telegram

Domain models, DTOs, mapping, and UI components can be shared across platforms instead of being duplicated.

Requirements:
- A single domain model for both the server and clients
- Type-safe API client for all platforms
- Reusable UI components (Compose Multiplatform)
- Ability to add a new platform with minimal effort

---

## Decision

Migrate `domain`, `api:*`, and `features` to **Kotlin Multiplatform (KMP)**, keeping `server:*` JVM-only.

### Module Classification

| Module            | Plugin           | Targets    | Rationale                                              |
|-------------------|------------------|------------|--------------------------------------------------------|
| `domain`          | `multiplatform`  | jvm, js    | Pure Kotlin, shared everywhere                         |
| `api:contract`    | `multiplatform`  | jvm, js    | DTOs with kotlinx.serialization (KMP)                  |
| `api:mapping`     | `multiplatform`  | jvm, js    | Needed on both server and in features                  |
| `api:client`      | `multiplatform`  | jvm, js    | Ktor Client (KMP-native)                               |
| `api:client:di`   | `multiplatform`  | jvm, js    | Platform-specific engine selection                     |
| `features`        | `multiplatform`  | jvm, js    | Compose Multiplatform UI                               |
| `tgminiapp`       | `multiplatform`  | js only    | Telegram shell, browser only                           |
| `server:*`        | `jvm`            | jvm only   | Exposed, Flyway, yt-dlp — JVM-only libraries           |

### KMP Compatibility of Key Dependencies

| Dependency            | KMP? | Notes                                          |
|-----------------------|------|------------------------------------------------|
| Arrow (Either)        | ✅    | Full KMP support                               |
| kotlinx.serialization | ✅    | Full KMP support                               |
| kotlinx-coroutines    | ✅    | Full KMP support                               |
| `kotlin.time.Instant` | ✅    | Kotlin 2.1.20+, in stdlib                      |
| `kotlin.uuid.Uuid`    | ✅    | Kotlin 2.0+, in stdlib                         |
| `kotlin.text.Regex`   | ✅    | In stdlib                                      |
| Ktor Client           | ✅    | CIO (JVM), Js (browser)                        |
| Koin                  | ✅    | koin-core — KMP, koin-compose — KMP            |
| Compose Multiplatform | ✅    | JVM (Desktop) + JS (Browser)                   |
| Exposed 1.0+          | ❌    | JVM only, but uses kotlin.time.Instant         |
| Ktor Server           | ❌    | JVM only — used only in server                 |
| Flyway                | ❌    | JVM only — used only in server                 |

### Architectural Decisions for KMP

1. **UUID**: `kotlin.uuid.Uuid` instead of `java.util.UUID` — in stdlib
2. **Timestamps**: `kotlin.time.Instant` instead of `java.time.Instant` — in stdlib since Kotlin 2.1.20+
3. **Duration**: `kotlin.time.Duration` instead of `java.time.Duration` — in stdlib
4. **Dates**: `value class LocalDate(val value: String)` — custom value class with ISO 8601 validation; no KMP-compatible equivalent in stdlib
5. **URL**: `value class Url(val value: String)` — custom value class; no KMP-compatible equivalent in stdlib
6. **Paths**: `value class FilePath(val value: String)` — custom value class. `java.nio.file.Path` — only in `server:infra` (JVM) for mapping
7. **Value classes**: Supported on JS since Kotlin 2.1+. Use `value class` in `commonMain`
8. **Logging**: `expect/actual` for logging or a KMP logging library

---

## Alternatives

### 1. Everything on JVM, UI on React

**Pros**: Simpler setup, larger React ecosystem.

**Cons**: Two languages (Kotlin + TypeScript), DTO duplication, no type-safety between client and server.

### 2. KMP only for domain and api:contract

**Pros**: Less KMP surface area, simpler.

**Cons**: UI components are not shared, `api:mapping` is duplicated.

### 3. Kotlin/Wasm instead of Kotlin/JS

**Pros**: Potentially better performance.

**Cons**: Less mature, limited compatibility with JS libraries (Telegram WebApp API).

---

## Consequences

### Positive

- Single domain model and DTOs across all platforms
- Type-safe API client for every platform
- UI components written once in `features`
- Adding a new platform = creating a thin shell module
- Type errors caught at compile time

### Negative

- Additional Gradle configuration complexity (KMP boilerplate)
- Not all libraries support KMP
- `commonTest` does not support MockK → manual fake implementations
- Compose Multiplatform for Web is less mature than React
- Longer initial build time

### Risks

- **Compose Web stability**: Monitor JetBrains releases, have a fallback plan
- **Value class JS support**: Stable in Kotlin 2.1+, but watch for edge cases
- **Bundle size**: JS bundle from KMP can be large → configure tree-shaking

---

## Testing in KMP

| Source set   | Framework                                      | What to test                        |
|--------------|------------------------------------------------|-------------------------------------|
| `commonTest` | Kotest framework-engine + assertions           | Domain logic, mapping, use-cases    |
| `jvmTest`    | Kotest runner-junit5 + MockK + Testcontainers  | Integration tests, DB               |
| `jsTest`     | Kotest framework-engine                        | JS-specific edge cases              |

> MockK does not support JS. For mocking in `commonTest` — create fake implementations of interfaces.
> Kotest Gradle plugin + KSP are required for JS/Native tests.

---

## References

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor Client KMP](https://ktor.io/docs/client-create-multiplatform-application.html)
- [Arrow KMP](https://arrow-kt.io/docs/quickstart/)
- [kotlin.uuid.Uuid](https://kotlinlang.org/api/core/kotlin/-uuid/)
