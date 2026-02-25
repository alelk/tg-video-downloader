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
    api(project(":api:contract"))

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

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
}

tasks.test {
    useJUnitPlatform()
}

