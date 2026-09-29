plugins {
    id("tgvd.kmp")
}

description = "Kotest Arb generators for domain models (test fixtures)"

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(projects.domain)
                api(libs.kotest.property)
            }
        }
    }
}
