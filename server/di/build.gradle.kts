plugins {
    alias(libs.plugins.kotlinJvm)
}

description = "Server DI: Koin modules and dependency wiring"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":domain"))
    api(project(":api:mapping"))
    api(project(":server:infra"))
    api(project(":server:transport"))

    // Koin
    api(libs.koin.core)
    api(libs.koin.ktor)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.koin.test)
}

tasks.test {
    useJUnitPlatform()
}

