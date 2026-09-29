import org.gradle.api.tasks.testing.Test

// Shared configuration for every Kotlin Multiplatform library module (domain, api:*, features):
// jvm + js(IR) browser targets, pinned toolchain, Kotest on the JVM through JUnit 5.
//
// Web target: js(IR), not wasmJs — the Mini App must run in older Telegram WebViews without WasmGC
// (ADR-009, G11). One web target only.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// The Kotest multiplatform Gradle plugin (and the KSP it needs) is deliberately NOT applied: its
// js compiler plugin lags the Kotlin compiler and breaks the build on upgrade. Kotest runs on the
// JVM via JUnit 5 (`kotest-runner-junit5` in the module's jvmTest). The js target only has to
// COMPILE commonMain and commonTest — `compileTestKotlinJs` is the platform-purity gate.

kotlin {
    // Pinned so the bytecode does not depend on whichever JDK launched Gradle.
    jvmToolchain(JVM_TOOLCHAIN_VERSION)

    jvm()

    js(IR) {
        browser {
            // No JS test runner is part of the gate (G11): it needs a headless browser, and Kotest
            // on JS needs the compiler plugin we do not apply. Disable only the runner —
            // compileTestKotlinJs stays in `build` and keeps commonTest free of JVM-only APIs.
            // Do NOT use failOnNoDiscoveredTests = false instead: that hides a missing runner.
            testTask {
                enabled = false
            }
        }
    }
}

// Node.js/Yarn for the js target come from the repositories declared in settings.gradle.kts.
resolveJsToolingFromSettingsRepositories()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(DOCKER_API_VERSION_JVM_ARG)
}
