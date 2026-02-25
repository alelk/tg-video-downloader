plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.shadow)
}

description = "Server application: entrypoint and configuration"

application {
    mainClass.set("io.github.alelk.tgvd.server.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":api:contract"))
    implementation(project(":api:mapping"))
    implementation(project(":server:infra"))
    implementation(project(":server:transport"))
    implementation(project(":server:di"))

    // Ktor
    implementation(libs.ktor.server.netty)

    // Configuration
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.bundles.testcontainers)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("tgvd-server")
    archiveClassifier.set("")
    archiveVersion.set("")
}

