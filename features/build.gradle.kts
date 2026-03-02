plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

description = "Shared UI components, screens, viewmodels (Compose Multiplatform KMP)"

kotlin {
    jvmToolchain(21)

    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
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

