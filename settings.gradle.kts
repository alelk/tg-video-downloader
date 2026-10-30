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
        // io.github.alelk:tg-mini-app (when ../tg-mini-app is not checked out next to this repo).
        maven {
            name = "GitHubPackagesTgMiniApp"
            url = uri("https://maven.pkg.github.com/alelk/tg-mini-app")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_USER")
                    ?: "alelk"
                password = providers.gradleProperty("gpr.token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
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
        // Last, and only for our own group: a locally published tg-mini-app build.
        mavenLocal {
            content { includeGroup("io.github.alelk") }
        }
    }
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
    println("🌐 Local tg-mini-app not found – will use Maven dependency (GitHub Packages)")
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
