// KMP module that also needs kotlinx.serialization (DTOs, HTTP client). Kotlin-family plugins must
// all load from one classpath, so serialization is applied here — never via `alias(...)` in the
// module, which would load the Kotlin Gradle plugin a second time.

plugins {
    id("tgvd.kmp")
    id("org.jetbrains.kotlin.plugin.serialization")
}
