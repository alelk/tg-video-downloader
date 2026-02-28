package io.github.alelk.tgvd.server.infra.config

data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
) {
    enum class ProxyType { HTTP, SOCKS5 }

    fun toUrl(): String? {
        if (!enabled) return null
        val auth = if (username != null && password != null) "$username:$password@" else ""
        val scheme = when (type) {
            ProxyType.HTTP -> "http"
            ProxyType.SOCKS5 -> "socks5"
        }
        return "$scheme://$auth$host:$port"
    }
}

