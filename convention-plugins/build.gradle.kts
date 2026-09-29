plugins {
    `kotlin-dsl`
}

// Precompiled convention plugins apply these Gradle plugins by id, so the plugins must be on this
// build's classpath. Versions still come from the catalogue — `toDep()` turns a plugin marker into
// its artifact. Kotlin, serialization and both Compose plugins load from this ONE classpath; a
// module must never apply them again with `alias(...)` (the Kotlin Gradle plugin would load twice).
dependencies {
    implementation(libs.plugins.kotlinMultiplatform.toDep())
    implementation(libs.plugins.kotlinJvm.toDep())
    implementation(libs.plugins.kotlinSerialization.toDep())
    implementation(libs.plugins.composeMultiplatform.toDep())
    implementation(libs.plugins.composeCompiler.toDep())
    // Ktor and Shadow are applied only by server:app, but both pull in the Kotlin Gradle plugin:
    // resolved there with a version (`alias(...)`) they get a classloader of their own and the
    // Kotlin plugin is loaded twice. On this classpath they are applied by id, without a version.
    implementation(libs.plugins.ktor.toDep())
    implementation(libs.plugins.shadow.toDep())
    // Static analysis, applied by tgvd.kmp, tgvd.compose.js and tgvd.jvm (see StaticAnalysis.kt).
    implementation(libs.plugins.detekt.toDep())
    implementation(libs.plugins.ktlint.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}
