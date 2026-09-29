/**
 * Build-wide constants shared by the `tgvd.*` convention plugins.
 *
 * The JDK the project compiles against is a build-infrastructure fact, not a dependency version,
 * so it lives here rather than in `libs.versions.toml` — but it is still declared exactly once.
 * Gradle selects (or provisions, via the foojay resolver in `settings.gradle.kts`) a matching
 * toolchain, so the bytecode level does not depend on whichever JDK launched Gradle.
 */
const val JVM_TOOLCHAIN_VERSION = 21

/**
 * Docker Engine 29+ rejects docker-java's historical default API version (1.32); 1.40 is supported
 * by both the engine and Testcontainers' client. Passed to every JVM test task.
 */
const val DOCKER_API_VERSION_JVM_ARG = "-Dapi.version=1.40"
