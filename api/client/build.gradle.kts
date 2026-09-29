plugins {
    id("tgvd.kmp.serialization")
}

description = "API client: Ktor KMP HTTP client for API"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.api.contract)
                // Every call returns Either<ApiError, T>
                api(libs.arrow.core)

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
                implementation(libs.kotest.runner)
                implementation(libs.kotest.assertions.core)
            }
        }

        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}
