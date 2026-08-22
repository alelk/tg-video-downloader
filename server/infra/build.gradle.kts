plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

description = "Server infrastructure: repositories, DB, external processes (yt-dlp, ffmpeg)"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":domain"))

    // Database
    api(libs.bundles.exposed)
    api(libs.postgresql)
    api(libs.hikari)

    // Migrations
    api(libs.flyway.core)
    api(libs.flyway.database.postgresql)

    // Serialization for JSONB
    api(libs.kotlinx.serialization.json)

    // Logging
    api(libs.kotlin.logging)

    // Outbound HTTP (e.g. GitHub releases API for yt-dlp update check)
    api(libs.ktor.client.core)
    api(libs.ktor.client.cio)
    api(libs.ktor.client.content.negotiation)
    api(libs.ktor.serialization.kotlinx.json)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}

