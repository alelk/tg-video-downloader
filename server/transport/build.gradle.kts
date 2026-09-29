plugins {
    id("tgvd.jvm.serialization")
}

description = "Server transport: Ktor routes, auth middleware, HTTP layer"

dependencies {
    api(projects.domain)
    api(projects.api.contract)
    api(projects.api.mapping)

    // Ktor Server
    api(libs.bundles.ktor.server)
    api(libs.ktor.server.resources)

    api(libs.koin.ktor)

    // Logging
    api(libs.kotlin.logging)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktor.server.test.host)
}
