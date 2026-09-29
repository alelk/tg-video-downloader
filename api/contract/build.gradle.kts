plugins {
    id("tgvd.kmp.serialization")
}

description = "API contract: DTOs for HTTP API (shared between client and server)"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
                api(libs.ktor.resources)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.assertions.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner)
            }
        }
    }
}
