// Root build script: shared project coordinates only. No plugins and no build logic here — that
// lives in the tgvd.* convention plugins (convention-plugins/), applied per module; repositories
// are declared in settings.gradle.kts.

// The product version has one source of truth: app.version (rewritten by semantic-release).
val appVersion: String = rootProject.file("app.version")
    .takeIf { it.exists() }
    ?.readText()?.trim()
    ?.ifBlank { null }
    ?: "0.0.1-SNAPSHOT"

allprojects {
    group = "io.github.alelk.tgvd"
    version = appVersion
}
