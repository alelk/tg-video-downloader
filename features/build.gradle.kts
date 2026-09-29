plugins {
    id("tgvd.compose")
}

description = "Shared UI components, screens, viewmodels (Compose Multiplatform KMP)"

// ─── Generate BuildConfig with version from root project ───
val generateBuildConfig = tasks.register("generateBuildConfig") {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val versionString = project.version.toString()
    outputs.dir(outputDir)
    inputs.property("appVersion", versionString)
    doLast {
        val dir = outputDir.get().asFile.resolve("io/github/alelk/tgvd/features/common")
        dir.mkdirs()
        dir.resolve("BuildConfig.kt").writeText(
            """
            |package io.github.alelk.tgvd.features.common
            |
            |/** Auto-generated from app.version at build time. */
            |object BuildConfig {
            |    const val APP_VERSION: String = "$versionString"
            |}
            """.trimMargin(),
        )
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
            dependencies {
                implementation(projects.api.contract)
                implementation(projects.api.client)
                implementation(projects.domain)

                // Compose runtime/foundation/material3/ui/resources come from the tgvd.compose convention

                // Lifecycle
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.lifecycle.runtime.compose)

                // Navigation
                implementation(libs.bundles.voyager)

                // DI
                implementation(libs.koin.core)
                implementation(libs.koin.compose)

                // Coroutines
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotest.runner)
            }
        }
    }
}

// ShellSourceGuardTest and UiConventionsTest (jvmTest) scan source files: declare them as test inputs,
// otherwise an up-to-date/cached test result would hide a new violation.
tasks.named<Test>("jvmTest") {
    inputs
        .dir(layout.projectDirectory.dir("../tgminiapp/src"))
        .withPropertyName("shellSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs
        .dir(layout.projectDirectory.dir("src/commonMain/kotlin"))
        .withPropertyName("uiSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
