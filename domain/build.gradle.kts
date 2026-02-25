plugins {
    alias(libs.plugins.kotlinJvm)
}

description = "Domain models, use-cases, and business logic (pure Kotlin, no frameworks)"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Arrow for Either
    api(libs.arrow.core)

    // Kotlinx
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // Testing
    testImplementation(libs.bundles.testing)
}

tasks.test {
    useJUnitPlatform()
}

