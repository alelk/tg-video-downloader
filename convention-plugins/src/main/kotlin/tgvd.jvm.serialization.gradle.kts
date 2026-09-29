// JVM-only module that also needs kotlinx.serialization (*Pm JSONB models, DTOs). Kotlin-family
// plugins must all load from one classpath, so serialization is applied here — never via
// `alias(...)` in the module, which would load the Kotlin Gradle plugin twice.

plugins {
    id("tgvd.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}
