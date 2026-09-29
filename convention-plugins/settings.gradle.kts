rootProject.name = "convention-plugins"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    // Reuse the project's single version catalogue so plugin versions are never inlined here.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
