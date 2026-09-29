package io.github.alelk.tgvd.domain.system

/**
 * A required dependency the server cannot serve requests without (the database). `GET /health/ready`
 * answers `200` only when every probe says ready.
 *
 * A probe never throws: a failure is `false` ("not ready"), not an error. Optional services (LLM)
 * are not readiness dependencies — their absence is a supported configuration.
 */
fun interface ReadinessProbe {
    suspend fun isReady(): Boolean
}
