import org.jetbrains.compose.ComposeExtension

// The Telegram Mini App shell (`tgminiapp`): a js(IR)-only Compose application. It is not a
// `tgvd.kmp` module — a shell has no jvm target. `binaries.executable()` and the webpack
// `outputFileName` stay in the module: they describe that one application, not a module kind.
// Kotlin, serialization and both Compose plugins are applied here, never via `alias(...)`.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Node.js/Yarn for the js target come from the repositories declared in settings.gradle.kts.
resolveJsToolingFromSettingsRepositories()

val composeDeps = extensions.getByType<ComposeExtension>().dependencies

kotlin {
    // Pinned like every other module, so no build step depends on the JDK that launched Gradle.
    jvmToolchain(JVM_TOOLCHAIN_VERSION)

    // js(IR), not wasmJs: older Telegram WebViews have no WasmGC (ADR-009, G11).
    js(IR) {
        browser {
            // Same as tgvd.kmp: no JS test runner in the gate; the shell has no tests anyway.
            testTask {
                enabled = false
            }
        }
    }

    sourceSets {
        // Compose entry point (ComposeViewport) and the shell's own composables.
        jsMain.dependencies {
            implementation(composeDeps.runtime)
            implementation(composeDeps.foundation)
            implementation(composeDeps.material3)
            implementation(composeDeps.ui)
        }
    }
}
