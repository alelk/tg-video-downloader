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

val localTgMiniAppDir = File(rootDir.parent, "tg-mini-app")
if (localTgMiniAppDir.exists() && localTgMiniAppDir.isDirectory) {
    println("🔗 Local tg-mini-app found – using composite build")
    includeBuild("../tg-mini-app") {
        dependencySubstitution {
            substitute(module("io.github.alelk:tg-mini-app"))
        }
    }
} else {
    println("🌐 Local pws-core not found – will use Maven dependency (GitHub Packages)")
}

rootProject.name = "tg-video-downloader"

// === Domain ===
include(":domain")
include(":domain:domain-test-fixtures")

// === API ===
include(":api:contract")
include(":api:mapping")
include(":api:client")

// === Server ===
include(":server:infra")
include(":server:transport")
include(":server:di")
include(":server:app")

// === UI ===
include(":features")
include(":tgminiapp")

