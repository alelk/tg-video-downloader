pluginManagement {
    // Shared build logic lives in an included build: the precompiled convention plugins
    // (tgvd.kmp / tgvd.jvm / tgvd.compose …) resolve by id like published plugins.
    includeBuild("convention-plugins")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Repositories are declared once, here; a module (or a plugin) adding its own fails the build.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // Node.js and Yarn distributions for the Kotlin/JS toolchain. The Kotlin plugin would add
        // these itself, which FAIL_ON_PROJECT_REPOS forbids; the tgvd.* conventions switch that off
        // (convention-plugins/.../JsToolingRepositories.kt) and they are declared here instead.
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions at $url"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions at $url"
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "tg-video-downloader"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
