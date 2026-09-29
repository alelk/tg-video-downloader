plugins {
    id("tgvd.jvm")
}

description = "Server DI: Koin modules and dependency wiring"

dependencies {
    api(projects.domain)
    api(projects.server.infra)
    api(projects.server.transport)

    // Koin
    api(libs.koin.core)
    api(libs.koin.ktor)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.koin.test)
}
