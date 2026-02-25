plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

description = "Server transport: Ktor routes, auth middleware, HTTP layer"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":domain"))
    api(project(":api:contract"))
    api(project(":api:mapping"))
    api(project(":server:infra"))

    // Ktor Server
    api(libs.bundles.ktor.server)
    api(libs.ktor.server.resources)

    // Logging
    api(libs.kotlin.logging)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

