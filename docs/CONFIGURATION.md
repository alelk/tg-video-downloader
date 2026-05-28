# Configuration

> **Purpose**: All application configuration parameters.

---

## 1. Format

- **Library**: Hoplite
- **Format**: YAML
- **Files**: `application.yaml`, `application-{profile}.yaml`
- **Environment variables**: Supported via Hoplite

---

## 2. Full Schema

```yaml
# Server
server:
  port: 8080
  host: "0.0.0.0"
  baseUrl: "https://your-domain.com"

# Telegram
telegram:
  botToken: "123456:ABC-DEF..."        # REQUIRED, via env
  allowedUserIds:                       # by Telegram user ID (numeric)
    - "123456789"
  allowedUsernames:                     # by @username (more convenient — users know their own handle)
    - "my_username"
  devMode: false                        # NEVER true in production
  miniAppAutoReply:
    enabled: false                       # true => bot replies to messages containing links
    botUsername: "my_downloader_bot"   # without @
    appShortName: "tgvd"               # short name from BotFather (last segment of t.me/bot/appShortName)
    buttonText: "Open Mini App"
    replyText: "Got your link. Open Mini App to continue."
    urlPatterns: []                      # list of regex patterns to filter URLs; empty => any URL
                                         # example: ["youtube\\.com", "youtu\\.be", "rutube\\.ru", "instagram\\.com"]
    pollingTimeoutSeconds: 60

# Database
db:
  url: "jdbc:postgresql://localhost:5432/tgvd"
  user: "tgvd"
  password: "secret"                    # via env
  poolSize: 10
  minIdle: 2

# Storage
storage:
  baseDirectories:
    - "/media/Music Videos"
    - "/media/TV"
    - "/media/Videos"
  tempDirectory: "/tmp/tgvd"

# yt-dlp
ytDlp:
  path: "yt-dlp"                        # or absolute path (e.g. "./yt-dlp")
  timeout: "30m"
  retries: 3
  fragmentRetries: 10
  allowUpdate: true                    # allow update via UI
  updateChannel: "stable"              # stable | nightly
  autoDownload: true                   # automatically download yt-dlp on startup if binary not found
  legacyServerConnect: false           # --legacy-server-connect: workaround for SSL EOF errors (e.g. RuTube)
  noCheckCertificate: false            # --no-check-certificate: disable TLS validation (use with caution!)

# ffmpeg
ffmpeg:
  path: "ffmpeg"                        # path to ffmpeg, or just "ffmpeg" if in PATH
  timeout: "60m"
  # ffprobe is resolved from the same directory: path.replace("ffmpeg", "ffprobe")
  # Used to determine actual source resolution before conversion.

# Jobs
jobs:
  maxConcurrentDownloads: 2
  maxAttempts: 3
  pollIntervalMs: 5000
  retryDelayMs: 30000

# Logging
logging:
  level: "INFO"
  format: "JSON"                        # JSON | TEXT

# LLM (Optional) — for smart metadata extraction
llm:
  provider: "GEMINI"                    # GEMINI | OPENAI | NONE
  apiKey: "AIza..."                     # REQUIRED if provider != NONE, via env
  model: "gemini-2.0-flash"

# Proxy (Optional) — for yt-dlp and LLM
proxy:
  enabled: false
  type: "HTTP"                          # HTTP | SOCKS5
  host: "127.0.0.1"
  port: 8080
  username: null                        # optional, via env
  password: null                        # optional, via env
```

---

## 3. Data Classes

```kotlin
data class AppConfig(
    val server: ServerConfig,
    val telegram: TelegramConfig,
    val db: DbConfig,
    val storage: StorageConfig,
    val ytDlp: YtDlpConfig,
    val ffmpeg: FfmpegConfig,
    val jobs: JobsConfig,
    val logging: LoggingConfig,
    val llm: LlmConfig = LlmConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
)

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val baseUrl: String,
)

data class TelegramConfig(
    val botToken: String,
    val allowedUserIds: List<String> = emptyList(),
    val allowedUsernames: List<String> = emptyList(),
    val devMode: Boolean = false,
    val miniAppAutoReply: TelegramMiniAppAutoReplyConfig = TelegramMiniAppAutoReplyConfig(),
)

data class TelegramMiniAppAutoReplyConfig(
    val enabled: Boolean = false,
    val botUsername: String? = null,
    /** Mini App short name — last segment of https://t.me/{botUsername}/{appShortName} */
    val appShortName: String? = null,
    val buttonText: String = "Open Mini App",
    val replyText: String = "Got your link. Open Mini App to continue.",
    /** List of regex patterns to filter URLs. Empty = respond to any URL */
    val urlPatterns: List<String> = emptyList(),
    val pollingTimeoutSeconds: Int = 60,
)

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int = 10,
    val minIdle: Int = 2,
)

data class StorageConfig(
    val baseDirectories: List<String>,
    val tempDirectory: String = "/tmp/tgvd",
)

data class YtDlpConfig(
    val path: String = "yt-dlp",
    val timeout: Duration = 30.minutes,
    val retries: Int = 3,
    val fragmentRetries: Int = 10,
    val allowUpdate: Boolean = true,
    val updateChannel: String = "stable",
    /** --legacy-server-connect: workaround for SSL EOF errors (e.g. RuTube) */
    val legacyServerConnect: Boolean = false,
    /** --no-check-certificate: disable TLS validation (use with caution!) */
    val noCheckCertificate: Boolean = false,
)

data class FfmpegConfig(
    val path: String = "ffmpeg",
    val timeout: Duration = 60.minutes,
)
// Note: ffprobe is resolved automatically from the same directory (path.replace("ffmpeg","ffprobe")).
// Encoding settings (codec, CRF, preset, hardware acceleration) are defined per-output in rules
// via VideoEncodeSettings, not globally.

data class JobsConfig(
    val maxConcurrentDownloads: Int = 2,
    val maxAttempts: Int = 3,
    val pollIntervalMs: Long = 5000,
    val retryDelayMs: Long = 30000,
)

data class LoggingConfig(
    val level: String = "INFO",
    val format: String = "JSON",
)

data class LlmConfig(
    val provider: LlmProvider = LlmProvider.NONE,
    val apiKey: String? = null,
    val model: String? = null,
) {
    enum class LlmProvider { GEMINI, OPENAI, NONE }
}

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
```

---

## 4. Loading Configuration

```kotlin
fun loadConfig(): AppConfig {
    return ConfigLoaderBuilder.default()
        .addResourceSource("/application.yaml")
        .addResourceOrFileSource("/application-${getProfile()}.yaml", optional = true)
        .addEnvironmentSource()
        .build()
        .loadConfigOrThrow<AppConfig>()
}

private fun getProfile(): String {
    return System.getenv("APP_PROFILE") ?: "local"
}
```

---

## 5. Environment Variables

Hoplite automatically maps environment variables:

| Env Variable                                    | Config Path                                       |
|-------------------------------------------------|---------------------------------------------------|
| `SERVER_PORT`                                   | `server.port`                                     |
| `TELEGRAM_BOT_TOKEN`                            | `telegram.botToken`                               |
| `TELEGRAM_ALLOWED_USER_IDS`                     | `telegram.allowedUserIds` (comma-separated)       |
| `TELEGRAM_ALLOWED_USERNAMES`                    | `telegram.allowedUsernames` (comma-separated)     |
| `DB_URL`                                        | `db.url`                                          |
| `DB_USER`                                       | `db.user`                                         |
| `DB_PASSWORD`                                   | `db.password`                                     |
| `LLM_API_KEY`                                   | `llm.apiKey`                                      |
| `PROXY_PASSWORD`                                | `proxy.password`                                  |
| `TELEGRAM_BOT_MINI_APP_ENABLED`                 | `telegram.miniAppAutoReply.enabled`               |
| `TELEGRAM_BOT_USERNAME`                         | `telegram.miniAppAutoReply.botUsername`           |
| `TELEGRAM_BOT_MINI_APP_SHORT_NAME`              | `telegram.miniAppAutoReply.appShortName`          |
| `TELEGRAM_BOT_MINI_APP_BUTTON_TEXT`             | `telegram.miniAppAutoReply.buttonText`            |
| `TELEGRAM_BOT_MINI_APP_REPLY_TEXT`              | `telegram.miniAppAutoReply.replyText`             |
| `TELEGRAM_BOT_MINI_APP_POLLING_TIMEOUT_SECONDS` | `telegram.miniAppAutoReply.pollingTimeoutSeconds` |
| `YTDLP_LEGACY_SERVER_CONNECT`                   | `ytDlp.legacyServerConnect`                       |
| `YTDLP_NO_CHECK_CERTIFICATE`                    | `ytDlp.noCheckCertificate`                        |

---

## 6. Profiles

### 6.1 local

`application-local.yaml`:

```yaml
telegram:
  devMode: true

db:
  url: "jdbc:postgresql://localhost:5432/tgvd"
  user: "tgvd"
  password: "tgvd"

storage:
  baseDirectories:
    - "/Users/you/Downloads/videos"
  tempDirectory: "/tmp/tgvd-local"

logging:
  level: "DEBUG"
  format: "TEXT"
```

### 6.2 production

`application-production.yaml`:

```yaml
telegram:
  devMode: false
  botToken: "${TELEGRAM_BOT_TOKEN}"

db:
  url: "${DB_URL}"
  user: "${DB_USER}"
  password: "${DB_PASSWORD}"

storage:
  baseDirectories:
    - "/media/Music Videos"
    - "/media/TV"
  tempDirectory: "/data/tgvd-temp"

logging:
  level: "INFO"
  format: "JSON"
```

---

## 7. Configuration Validation

```kotlin
fun AppConfig.validate() {
    require(telegram.botToken.isNotBlank()) { "telegram.botToken is required" }
    require(telegram.allowedUserIds.isNotEmpty()) { "telegram.allowedUserIds cannot be empty" }
    require(storage.baseDirectories.isNotEmpty()) { "storage.baseDirectories cannot be empty" }
    
    if (!telegram.devMode) {
        require(!telegram.botToken.startsWith("test")) { 
            "Invalid botToken in production mode" 
        }
    }
    
    // Verify that base directories exist and are accessible
    storage.baseDirectories.forEach { dir ->
        val path = Path.of(dir)
        require(Files.exists(path) && Files.isDirectory(path)) {
            "storage.baseDirectories: $dir does not exist or is not a directory"
        }
    }
    
    // LLM: if provider is set, apiKey is required
    if (llm.provider != LlmConfig.LlmProvider.NONE) {
        require(!llm.apiKey.isNullOrBlank()) { "llm.apiKey is required when provider is ${llm.provider}" }
    }
    
    // Proxy: if enabled, host and port are required
    if (proxy.enabled) {
        require(proxy.host.isNotBlank()) { "proxy.host is required when proxy is enabled" }
        require(proxy.port in 1..65535) { "proxy.port must be 1-65535" }
    }
}
```

---

## 8. Application Wiring

```kotlin
fun main() {
    val config = loadConfig()
    config.validate()
    
    embeddedServer(Netty, port = config.server.port, host = config.server.host) {
        install(Koin) {
            modules(
                module {
                    single { config }
                    single { config.telegram }
                    single { config.db }
                    // ...
                }
            )
        }
        
        configureFlyway()
        configureSerialization()
        configureRouting()
    }.start(wait = true)
}
```

---

## 9. Docker Environment

```dockerfile
ENV SERVER_PORT=8080
ENV TELEGRAM_BOT_TOKEN=""
ENV DB_URL="jdbc:postgresql://postgres:5432/tgvd"
ENV DB_USER="tgvd"
ENV DB_PASSWORD=""
ENV LLM_API_KEY=""
ENV APP_PROFILE="production"
```

```yaml
# docker-compose.yml
services:
  app:
    environment:
      - SERVER_PORT=8080
      - TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
      - DB_URL=jdbc:postgresql://postgres:5432/tgvd
      - DB_USER=tgvd
      - DB_PASSWORD=${DB_PASSWORD}
      - LLM_API_KEY=${LLM_API_KEY}
      - PROXY_PASSWORD=${PROXY_PASSWORD}
      - APP_PROFILE=production
```
