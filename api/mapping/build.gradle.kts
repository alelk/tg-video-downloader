plugins {
    id("tgvd.kmp")
}

description = "API mapping: domain <-> DTO conversion"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.domain)
                api(projects.api.contract)
                implementation(libs.arrow.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotest.framework.engine)
                implementation(libs.kotest.assertions.core)
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
