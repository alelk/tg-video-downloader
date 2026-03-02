plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

description = "Telegram Mini App: Compose Multiplatform Web UI"

kotlin {
    jvmToolchain(21)

    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "tgminiapp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(project(":features"))
                implementation(project(":api:contract"))
                implementation(project(":api:client"))

                // Compose (needed for renderComposable entry point)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)

                // DI
                implementation(libs.koin.core)
                implementation(libs.koin.compose)

                // Telegram Mini App
                implementation(libs.tg.mini.app)

                // Ktor Client JS
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)
            }
        }
    }
}
