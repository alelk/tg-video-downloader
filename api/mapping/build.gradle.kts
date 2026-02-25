plugins {
    alias(libs.plugins.kotlinJvm)
}

description = "API mapping: domain <-> DTO conversion"

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":domain"))
    api(project(":api:contract"))

    // Testing
    testImplementation(libs.bundles.testing)
}

tasks.test {
    useJUnitPlatform()
}

