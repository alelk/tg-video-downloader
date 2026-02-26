plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

description = "API mapping: domain <-> DTO conversion"

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
                api(project(":api:contract"))
                implementation(libs.arrow.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
