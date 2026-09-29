import org.gradle.api.tasks.testing.Test

// Shared configuration for JVM-only modules (server:*): Kotlin/JVM, pinned toolchain, JUnit 5.

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Pinned so the build does not depend on whichever JDK launched Gradle: every module compiles
// against the same bytecode level, locally, in CI and in the Docker builder.
kotlin {
    jvmToolchain(JVM_TOOLCHAIN_VERSION)
}

tasks.withType<Test>().configureEach {
    // Kotest runs on the JUnit Platform (`kotest-runner-junit5`).
    useJUnitPlatform()
    jvmArgs(DOCKER_API_VERSION_JVM_ARG)
}
