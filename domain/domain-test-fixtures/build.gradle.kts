plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

description = "Kotest Arb generators for domain models (test fixtures)"

kotlin {
    jvmToolchain(21)

    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":domain"))
                api(libs.kotest.property)
            }
        }
    }
}

