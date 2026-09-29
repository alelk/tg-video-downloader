plugins {
    id("tgvd.kmp")
}

description = "Domain models, use-cases, and business logic (pure Kotlin, no frameworks)"

kotlin {
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
                implementation(projects.domain.domainTestFixtures)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotest.runner)
            }
        }
    }
}
