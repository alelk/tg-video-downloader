package io.github.alelk.tgvd.server.infra.config

data class LlmConfig(
    val provider: LlmProvider = LlmProvider.NONE,
    val apiKey: String? = null,
    val model: String? = null,
) {
    enum class LlmProvider { GEMINI, OPENAI, NONE }
}

