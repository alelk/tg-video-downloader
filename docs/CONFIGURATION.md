---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ DEPLOYMENT.md, SECURITY.md, ../server/app/src/main/resources/application.yaml ]
---

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
  botToken: "123456:ABC-DEF..."        # REQUIRED unless devMode (TELEGRAM_BOT_TOKEN) — see §7
  allowedUserIds:                       # by Telegram user ID (numeric); TELEGRAM_ALLOWED_USER_IDS
    - "123456789"                       # a comma-separated string works too: "123456789, 987654321"
  allowedUsernames:                     # by @username (more convenient — users know their own handle);
    - "my_username"                     # TELEGRAM_ALLOWED_USERNAMES; '@' and case are ignored
                                        # both lists empty => ANY Telegram user (a WARN at start)
  devMode: false                        # NEVER true in production (a WARN at start when true)
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
  preferredAudioLanguages: []          # optional extra tracks; empty = source-original audio only
  maxAdditionalAudioTracks: 2          # does not include the original track; 0 = original only
  originalAudioLanguage: null           # pin the original-track language, e.g. "ru", to override yt-dlp's default-audio detection
  writeSubs: true                      # download publisher-provided subtitles
  writeAutoSubs: true                  # also download generated captions
  preferredSubtitleLanguages: ["ru", "en"] # download only these languages
  sleepSubtitles: 3                    # --sleep-subtitles: pause before each subtitle download when both writeSubs and writeAutoSubs are on (avoids YouTube 429)

# The merge container (--merge-output-format) always follows the extension of the chosen
# Output format for that rule/output — there is no separate global or per-rule override.
#
# A preview's explicit subtitle selection takes priority over these defaults and its rule.
# An empty selection disables subtitle downloads for that job. A rule's downloadSubtitles
# is tri-state: null = inherit writeSubs/writeAutoSubs above, true/false = force on/off for
# that rule regardless of the global default, when no explicit preview selection is supplied.
#
# Audio and subtitle settings are layered: global (above) < rule downloadPolicy
# (audioLanguages, downloadSubtitles, subtitleLanguages) < channel trackPreferences.
# Unset rule/channel values inherit. Missing languages are skipped, and a subtitle
# track that fails to download (e.g. HTTP 429) is only a warning (--ignore-errors).

# ffmpeg
ffmpeg:
  path: "ffmpeg"                        # path to ffmpeg, or just "ffmpeg" if in PATH
  timeout: "60m"
  # ffprobe is resolved from the same directory: path.replace("ffmpeg", "ffprobe")
  # Used to determine actual source resolution before conversion.

# Jobs
jobs:
  maxConcurrentDownloads: 2            # jobs processed at once (claimed atomically, one by one)
  maxAttempts: 3                        # read, NOT used (no automatic retries)
  pollIntervalMs: 5000                  # claim new jobs / notice cancelled ones at most this late
  retryDelayMs: 30000                   # read, NOT used (no automatic retries)

# Logging — read, NOT used: logging is configured by logback.xml (text lines with the correlation id)
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

`server/app/.../config/ConfigLoader.kt`. Sources, first wins:

1. environment variables (Hoplite env source: `A_B` → `a.b`, e.g. `SERVER_PORT` → `server.port`);
2. the external file `APP_CONFIG` (default `/app/config/application.yaml`, optional — in Docker it is
   the compose `configs.server-config`);
3. `application-<APP_PROFILE>.yaml` on the classpath (default profile `local`, optional);
4. `application.yaml` on the classpath — committed defaults.

`${VAR:-default}` placeholders inside the YAML are resolved from the process environment. This is how
`TELEGRAM_*` variables reach camelCase keys (`telegram.botToken`, `telegram.allowedUserIds`): the env
source alone would map `TELEGRAM_BOT_TOKEN` to `telegram.bot.token`, which is no key.

```kotlin
fun loadConfig(env: Map<String, String> = System.getenv()): AppConfig =
    ConfigLoaderBuilder.default()
        .addPropertySource(EnvironmentVariablesPropertySource(true, true, { env }))
        .addFileSource(env["APP_CONFIG"] ?: "/app/config/application.yaml", optional = true)
        .addResourceSource("/application-${env["APP_PROFILE"] ?: "local"}.yaml", optional = true)
        .addResourceSource("/application.yaml")
        .build()
        .loadConfigOrThrow<AppConfig>()
        .let { it.copy(telegram = it.telegram.withNormalizedAllowLists()) }
```

**Allow-lists from the environment.** `allowedUserIds: "${TELEGRAM_ALLOWED_USER_IDS:-}"` (the same for
usernames) in `application.yaml` and in the compose inline config. The value is one comma-separated
string; after binding every entry is trimmed and blank entries are dropped, so an unset or empty
variable is an **empty** list (never `[""]`, which would lock everybody out with `403`).

---

## 5. Environment Variables

Environment variables the server reads (see §4 for how each one reaches its key):

| Env Variable                                    | Config Path                                       |
|-------------------------------------------------|---------------------------------------------------|
| `SERVER_PORT`                                   | `server.port`                                     |
| `TELEGRAM_BOT_TOKEN`                            | `telegram.botToken` (via `${…}` in the YAML)      |
| `TELEGRAM_ALLOWED_USER_IDS`                     | `telegram.allowedUserIds` (comma-separated, `${…}`) |
| `TELEGRAM_ALLOWED_USERNAMES`                    | `telegram.allowedUsernames` (comma-separated, `${…}`) |
| `TELEGRAM_DEV_MODE`                             | `telegram.devMode` (compose inline config only)   |
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

## 7. Configuration Validation (fail-fast)

`server/app/.../config/ConfigValidation.kt`: `validateConfig(config)` returns every problem;
`requireValidConfig` throws `InvalidConfigException` with **all** of them in one message. It runs in
`main()` right after `loadConfig()` and again at the top of `Application.module()`.

| Rule | Message names |
|------|---------------|
| `telegram.devMode = false` and `telegram.botToken` is empty or a development placeholder (`test-token`, `dev-token`) | `TELEGRAM_BOT_TOKEN` |

The default in `application.yaml` is `botToken: "${TELEGRAM_BOT_TOKEN:-}"` — without the variable a
non-dev server refuses to start. The compose inline config keeps `dev-token` with `devMode` defaulting
to `true` (local-dev compose).

Logged at start (not errors):

- `telegram.devMode = true` → `WARN`: `X-Telegram-Init-Data: dev` is accepted without a signature.
- both allow-lists empty → `WARN`: access is open to any Telegram user; otherwise `INFO` with the
  number of ids/usernames (never the values).

### 7.1 Keys that are read but not used

Accepted for compatibility (G1: every key keeps its meaning and default), with no effect today:

- `jobs.maxAttempts`, `jobs.retryDelayMs` — there are no automatic retries (a failed job is retried
  by the user).
- `logging.level`, `logging.format` — logging is configured by `logback.xml`.

---

## 8. Application Wiring

`main()`: `loadConfig()` → `requireValidConfig()` → `embeddedServer { module(config) }`. Any exception
before the server listens is logged and the process exits with code `1`.

`Application.module(config, eagerDatabase, startBackgroundServices, overrides)`:

1. `requireValidConfig(config)`, start-up `WARN`/`INFO` about Telegram access;
2. `eagerDatabase = true`: Hikari pool → Flyway `migrate` → Exposed `Database` — **before** Koin and
   routing; a failed migration throws and no route is installed (the pool is closed);
3. Koin with the opened database; plugins ContentNegotiation → DefaultHeaders → CallId → CallLogging →
   CORS → StatusPages → Resources; routing (`/health*` public, the API behind `TelegramAuthPlugin`);
4. `startBackgroundServices = true`: yt-dlp bootstrap + `JobProcessor`, the auto-reply bot — subscribed
   to this application's lifecycle only; the pool is closed on `ApplicationStopped`.

Job processing and the lifecycle (ARCHITECTURE §5.3):

- start: jobs left `downloading`/`post-processing` by the previous process go back to `pending` (same
  attempt), then polling every `jobs.pollIntervalMs`; a job cancelled through the API is stopped (its
  yt-dlp/ffmpeg killed) on its next progress write or at the latest on the next poll;
- stop (`ApplicationStopping`): running jobs are cancelled and returned to `pending` — up to 10 s for
  them to wind down (15 s bound for the whole hook). A harder stop (`SIGKILL`, container stop timeout)
  loses nothing: the next start requeues them;
- **exactly one server instance per database** — a second instance's start would requeue the jobs
  the first one is downloading.

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
