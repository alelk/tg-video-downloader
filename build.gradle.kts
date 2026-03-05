plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.ksp) apply false
}

group = "io.github.alelk.tgvd"

// ─── app.version as single source of truth ───
val appVersionFile = rootProject.file("app.version")
val appVersion: String = if (appVersionFile.exists()) {
    appVersionFile.readText().trim().ifBlank { "0.0.1-SNAPSHOT" }
} else {
    "0.0.1-SNAPSHOT"
}
version = appVersion

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/alelk/tg-mini-app")
            credentials {
                username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USER") ?: "alelk"
                password = findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}