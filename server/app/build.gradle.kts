import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("tgvd.jvm.serialization")
}

// Ktor and Shadow are applied by id, without a version: their markers are on the convention-plugins
// classpath (see convention-plugins/build.gradle.kts), so the Kotlin Gradle plugin is loaded only
// once. A `plugins {}` block cannot resolve a version-less id from there, hence `apply(plugin = …)`.
apply(plugin = "io.ktor.plugin")
apply(plugin = "com.gradleup.shadow")

description = "Server application: entrypoint and configuration"

configure<JavaApplication> {
    mainClass.set("io.github.alelk.tgvd.server.ApplicationKt")
}

dependencies {
    implementation(projects.domain)
    implementation(projects.api.contract)
    implementation(projects.server.infra)
    implementation(projects.server.transport)
    implementation(projects.server.di)

    // Ktor
    implementation(libs.ktor.server.netty)

    // Telegram Bot API
    implementation(libs.tgbotapi)

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

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("tgvd-server.jar")

    // Merge META-INF/services — required for Ktor plugins, SLF4J providers, Flyway, etc.
    mergeServiceFiles {
        include("META-INF/services/**")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    manifest {
        attributes["Main-Class"] = "io.github.alelk.tgvd.server.ApplicationKt"
    }
}

// shadowJar is deliberately NOT part of `build` (the gate compiles and tests; packaging is an explicit
// step): CI and the Dockerfiles call `:server:app:shadowJar` themselves. Shadow 9 hooks it into
// `assemble` twice — directly, and through the shadow distribution archives (shadowDistZip/Tar →
// shadowJar) that the distribution plugin publishes to `archives`. Both hooks are removed here.
tasks.named("assemble") {
    setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == "shadowJar" })
}
configurations.named("archives") {
    artifacts.removeIf { it.buildDependencies.getDependencies(null).any { task -> task.name.startsWith("shadowDist") } }
}
