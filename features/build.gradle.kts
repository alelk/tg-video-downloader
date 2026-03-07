plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

description = "Shared UI components, screens, viewmodels (Compose Multiplatform KMP)"

// ─── Generate BuildConfig with version from root project ───
val generateBuildConfig by tasks.registering {
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
            """.trimMargin()
        )
    }
}

kotlin {
    jvmToolchain(21)

    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildConfig.map { layout.buildDirectory.dir("generated/buildconfig") })
            dependencies {
                implementation(project(":api:contract"))
                implementation(project(":api:client"))

                // Compose
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)

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
    }
}

