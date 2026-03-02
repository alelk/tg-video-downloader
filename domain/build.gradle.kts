plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotest)
    alias(libs.plugins.ksp)
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
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.property)
                implementation(project(":domain:domain-test-fixtures"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
