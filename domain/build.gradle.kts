plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

description = "Domain models, use-cases, and business logic (pure Kotlin, no frameworks)"

kotlin {
    jvmToolchain(21)

    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                // Arrow for Either
                api(libs.arrow.core)
                // Kotlinx
                api(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
