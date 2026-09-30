---
status: stable
owner: Alex (alelk)
updated: 2026-09-30
related: [ DEPLOYMENT.md, SECURITY.md, ../server/app/src/main/resources/application.yaml ]
---

# Configuration

> **Purpose**: All application configuration parameters.

---

## 1. Format

- **Library**: Hoplite
- **Format**: YAML
- **Files**: `application.yaml` (classpath, committed defaults), optional `application-{profile}.yaml`
  (classpath; none is committed), optional external file `APP_CONFIG` (compose inline config)
- **Environment variables**: Supported via Hoplite

---

## 2. Full Schema

Example values (the committed defaults are in `server/app/src/main/resources/application.yaml`; the
code defaults are the data classes in `server/infra/.../config/`).

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
  timeout: "30m"                       # read, NOT used (no process timeout is applied)
  retries: 3                           # code default 5
  fragmentRetries: 10                  # code default 30
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
  cookiesFromBrowser: "${YTDLP_COOKIES_FROM_BROWSER:-}"
  cookiesFile: "${YTDLP_COOKIES_FILE:-}"
  # … more yt-dlp options (formats, rate limits, player client, extractorOverrides, …) — see YtDlpConfig.kt

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
  timeout: "60m"                        # read, NOT used (no process timeout is applied)
  renderDevice: null                    # optional hardware render device for encoding
  # ffprobe is resolved from the same directory: path.replace("ffmpeg", "ffprobe")
  # Used to determine actual source resolution before conversion.

# Post-processing defaults
postProcess:
  taggingTool: "FFMPEG"                 # FFMPEG | ATOMICPARSLEY | MP4BOX
  embedThumbnail: true
  embedMetadata: true
  normalizeAudio: false

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

# LLM (Optional) — read, NOT used: no LLM adapter exists, UnconfiguredLlmPort is always bound
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
  username: null                        # optional
  password: null                        # optional
# Note: once settings are saved through `PUT /system/settings`, the stored `ytDlp` and `proxy`
# sections (table `system_settings`) replace these two sections entirely at start (§4).

# CORS for the Mini App origin
cors:
  enabled: true
  anyHost: false
  allowCredentials: false
  hosts: ["localhost:8081"]
  headers: ["Content-Type", "X-Telegram-Init-Data", "X-Workspace-Id"]
  exposeHeaders: ["X-Correlation-Id"]
  methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
  allowNonSimpleContentTypes: true
```

---

## 3. Data Classes

`server/infra/src/main/kotlin/io/github/alelk/tgvd/server/infra/config/` — one file per section
(`AppConfig`, `ServerConfig`, `TelegramConfig`, `DbConfig`, `StorageConfig`, `YtDlpConfig`,
`FfmpegConfig`, `PostProcessConfig`, `JobsConfig`, `LoggingConfig`, `LlmConfig`, `ProxyConfig`,
`CorsConfig`). The KDoc of each field is the reference; the files are not repeated here.

```kotlin
data class AppConfig(
    val server: ServerConfig,              // port 8080, host 0.0.0.0, baseUrl "http://localhost:8080"
    val telegram: TelegramConfig,          // botToken is required (may be empty only with devMode)
    val db: DbConfig,                      // url, user, password required; poolSize 10, minIdle 2
    val storage: StorageConfig,            // baseDirectories required
    val ytDlp: YtDlpConfig = YtDlpConfig(),
    val ffmpeg: FfmpegConfig = FfmpegConfig(),
    val postProcess: PostProcessConfig = PostProcessConfig(),
    val jobs: JobsConfig = JobsConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val llm: LlmConfig = LlmConfig(),
    val proxy: ProxyConfig = ProxyConfig(),
    val cors: CorsConfig = CorsConfig(),
)
```

Encoding settings (codec, CRF, preset, hardware acceleration) are defined per output in rules via
`VideoEncodeSettings`, not globally. ffprobe is resolved from the ffmpeg path
(`path.replace("ffmpeg", "ffprobe")`).

---

## 4. Loading Configuration

`server/app/.../config/ConfigLoader.kt`. Sources, first wins:

1. environment variables (Hoplite 2.9 env source with `useUnderscoresAsSeparator`: a **double**
   underscore nests, a single underscore joins words in camelCase — `SERVER__PORT` → `server.port`,
   `DB__PASSWORD` → `db.password`, `TELEGRAM__BOT_TOKEN` → `telegram.botToken`; a name with single
   underscores only, such as `SERVER_PORT`, becomes `serverPort` — no such key, ignored);
2. the external file `APP_CONFIG` (default `/app/config/application.yaml`, optional — in Docker it is
   the compose `configs.server-config`);
3. `application-<APP_PROFILE>.yaml` on the classpath (default profile `local`, optional);
4. `application.yaml` on the classpath — committed defaults.

`${VAR:-default}` placeholders inside the YAML are resolved from the process environment. This is how
the single-underscore variables (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_IDS`, `YTDLP_COOKIES_FILE`, …)
reach their keys: the env source alone would map `TELEGRAM_BOT_TOKEN` to `telegramBotToken`, which is
no key. A placeholder in the external compose config wins over the classpath `application.yaml`.

After loading, `SystemSettingsHolder` reads the `ytdlp` and `proxy` rows of `system_settings`; when a
row exists (settings were once saved through `PUT /system/settings`), it **replaces** the configured
`ytDlp` / `proxy` section as a whole (including `path`, `timeout`, `retries`, `allowUpdate`).

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

Variables that reach the config through a `${…}` placeholder (in `application.yaml`, the compose
inline config, or both):

| Env Variable                                    | Config Path                                       | Where the placeholder is |
|-------------------------------------------------|---------------------------------------------------|--------------------------|
| `TELEGRAM_BOT_TOKEN`                            | `telegram.botToken`                               | both                     |
| `TELEGRAM_ALLOWED_USER_IDS`                     | `telegram.allowedUserIds` (comma-separated)       | both                     |
| `TELEGRAM_ALLOWED_USERNAMES`                    | `telegram.allowedUsernames` (comma-separated)     | both                     |
| `TELEGRAM_DEV_MODE`                             | `telegram.devMode`                                | compose only             |
| `TELEGRAM_BOT_MINI_APP_ENABLED`                 | `telegram.miniAppAutoReply.enabled`               | both                     |
| `TELEGRAM_BOT_USERNAME`                         | `telegram.miniAppAutoReply.botUsername`           | both                     |
| `TELEGRAM_BOT_MINI_APP_SHORT_NAME`              | `telegram.miniAppAutoReply.appShortName`          | both                     |
| `TELEGRAM_BOT_MINI_APP_BUTTON_TEXT`             | `telegram.miniAppAutoReply.buttonText`            | both                     |
| `TELEGRAM_BOT_MINI_APP_REPLY_TEXT`              | `telegram.miniAppAutoReply.replyText`             | both                     |
| `TELEGRAM_BOT_MINI_APP_POLLING_TIMEOUT_SECONDS` | `telegram.miniAppAutoReply.pollingTimeoutSeconds` | `application.yaml` only (compose hardcodes `60`) |
| `YTDLP_COOKIES_FROM_BROWSER`                    | `ytDlp.cookiesFromBrowser`                        | `application.yaml`       |
| `YTDLP_COOKIES_FILE`                            | `ytDlp.cookiesFile`                               | `application.yaml`       |

Read by the loader itself: `APP_CONFIG` (external file, default `/app/config/application.yaml`) and
`APP_PROFILE` (classpath profile file, default `local`). `BGUTIL_HTTP_ENDPOINT` is not a config key:
the bgutil yt-dlp plugin in the server image reads it from the process environment.

Any other key can be set with the double-underscore form (§4), e.g. `DB__URL`, `DB__USER`,
`DB__PASSWORD`, `SERVER__PORT`, `PROXY__PASSWORD`. Names such as `DB_URL`, `DB_PASSWORD`,
`SERVER_PORT`, `LLM_API_KEY` or `PROXY_PASSWORD` do **not** reach the config.

---

## 6. Profiles

No profile file is committed. `application-<APP_PROFILE>.yaml` (default profile `local`) is an
optional classpath resource; a developer may add a git-ignored `application-local.yaml` under
`server/app/src/main/resources/` to override the defaults for `./gradlew :server:app:run` — for
example the database of `docker compose up -d db` (host port `5433`, password `tgvd`) and
`telegram.devMode: true`. In Docker the external file `APP_CONFIG` (the compose inline config) plays
this role.

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
  number of ids/usernames (never the values). A non-numeric entry in `allowedUserIds` is counted here
  but silently dropped by the auth plugin — if all entries are such, access is open to everyone (open
  question in `project-status.md`).

### 7.1 Keys that are read but not used

Accepted for compatibility (G1: every key keeps its meaning and default), with no effect today:

- `jobs.maxAttempts`, `jobs.retryDelayMs` — there are no automatic retries (a failed job is retried
  by the user).
- `logging.level`, `logging.format` — logging is configured by `logback.xml` (text lines; a JSON
  appender is defined there but not referenced).
- `ytDlp.timeout`, `ffmpeg.timeout` — no timeout is applied to the external processes.
- `storage.baseDirectories`, `storage.tempDirectory`, `server.baseUrl` — bound, but nothing reads them
  (output paths come from rule path templates).
- `llm.*` — no LLM adapter exists; `UnconfiguredLlmPort` is always bound.

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

The Dockerfiles set only `JAVA_OPTS` (and runtime paths); configuration comes from the compose file.
`docker-compose.yaml`, service `server`: `APP_CONFIG=/app/config/application.yaml` points at the inline
config `configs.server-config` (database `db:5432`, `devMode` from `TELEGRAM_DEV_MODE`, default `true`),
and the environment passes `TELEGRAM_BOT_TOKEN` (default `dev-token`), `TELEGRAM_ALLOWED_USER_IDS`,
`TELEGRAM_ALLOWED_USERNAMES`, `TELEGRAM_DEV_MODE`, `YTDLP_COOKIES_FILE`, `BGUTIL_HTTP_ENDPOINT` and the
`TELEGRAM_BOT_MINI_APP_*` variables from `.env`. See [`DEPLOYMENT.md`](DEPLOYMENT.md).
