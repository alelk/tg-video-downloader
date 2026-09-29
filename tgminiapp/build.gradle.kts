plugins {
    id("tgvd.compose.js")
}

description = "Telegram Mini App: Compose Multiplatform Web UI"

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "tgminiapp.js"
            }
            binaries.executable()
        }
    }

    sourceSets {
        jsMain {
            dependencies {
                implementation(projects.features)
                implementation(projects.api.contract)
                implementation(projects.api.client)

                // Compose runtime/foundation/material3/ui come from the tgvd.compose.js convention

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
