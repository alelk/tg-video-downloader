pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tg-video-downloader"

// === Domain ===
include(":domain")

// === API ===
include(":api:contract")
include(":api:mapping")
include(":api:client")
include(":api:client:di")

// === Server ===
include(":server:infra")
include(":server:transport")
include(":server:di")
include(":server:app")

// === UI ===
include(":features")
include(":tgminiapp")
