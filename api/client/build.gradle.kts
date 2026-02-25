plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

description = "API client: Ktor KMP HTTP client for API"

kotlin {
    jvmToolchain(21)

    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":api:contract"))

                api(libs.bundles.ktor.client)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.ktor.client.mock)
            }
        }

        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

