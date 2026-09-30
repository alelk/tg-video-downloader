---
status: stable
owner: Alex (alelk)
updated: 2026-09-30
related: [ CONFIGURATION.md, DEPLOYMENT.md, API_CONTRACT.md, ADR/006-workspaces.md, ADR/009-engineering-skills-baseline.md ]
---

# Security

> **Purpose**: Authorization via Telegram initData, allow-list, workspace membership, and the
> protections that exist in the code today (and the ones that do not).

---

## 1. Telegram initData

### 1.1 What It Is

`initData` — a string passed by Telegram to the Mini App. Contains:
- User data (user)
- Timestamp (auth_date)
- HMAC signature (hash)

The client sends it on **every** request in the header `X-Telegram-Init-Data` (read from
`Telegram.WebApp.initData` at request time). There are no sessions or tokens (ADR-009, Fork 4).

### 1.2 Format

```
query_id=AAHdF6IQAAAAAN0XohDhrOrc
&user=%7B%22id%22%3A123456789%2C%22first_name%22%3A%22John%22%7D
&auth_date=1234567890
&hash=c501b71e775f74ce10e377dea85a7ea24ecd640b223ea86dfe453e0eaed2e2b2
```

URL-encoded parameters separated by `&`.

### 1.3 Validation Algorithm

`server/transport/.../auth/TelegramAuthValidator.kt` — `TelegramAuthValidator(botToken, devMode,
maxAgeSeconds = 86400)`, `validate(initData): Either<AuthError, TelegramUser>`:

1. `devMode` and `initData == "dev"` → the dev user (`TelegramUserId(1)`, username `dev`), no check.
2. Parse `key=value` pairs (pairs without `=` are skipped, values URL-decoded); take out `hash`
   (missing → `MissingHash`).
3. `auth_date` must parse (`InvalidAuthDate`) and be at most `maxAgeSeconds` (24 h) old (`Expired`);
   the age is computed from the system clock (`System.currentTimeMillis()`, not an injected `Clock`).
4. Data-check string: the remaining pairs sorted by key, `key=value` joined with `\n`.
   `secret = HMAC-SHA256(key = "WebAppData", data = botToken)`,
   `expected = hex(HMAC-SHA256(key = secret, data = dataCheckString))`.
5. Compare with `MessageDigest.isEqual` (constant time) → `InvalidHash` on mismatch.
6. Decode `user` JSON (`MissingUser`, `InvalidUser`) into `TelegramUser(id, firstName, lastName, username)`.

A `Left` is answered `401 UNAUTHORIZED` with the message `Invalid initData: <AuthError>`.

---

## 2. Ktor Auth Plugin

### 2.1 TelegramAuthPlugin

`server/transport/.../auth/TelegramAuthPlugin.kt`, a route-scoped plugin with
`TelegramAuthConfig(validator, allowedUserIds: Set<Long>, allowedUsernames: Set<String>)`:

- no `X-Telegram-Init-Data` → `401 UNAUTHORIZED` "Missing X-Telegram-Init-Data header";
- invalid initData → `401 UNAUTHORIZED`;
- valid, but not allowed → `403 FORBIDDEN` "User not allowed". Allowed means: **both lists empty**,
  or the user id is in `allowedUserIds`, or the lower-cased username is in `allowedUsernames`
  (lower-cased, leading `@` stripped);
- otherwise the user is stored in the call attributes (`RoutingCall.telegramUser`).

Error bodies are `ApiErrorDto` with the call's correlation id (`call.callId`, header
`X-Correlation-Id`). The plugin logs nothing about the outcome.

### 2.2 Usage in Routing

`server/app/.../Application.kt`, `configureRouting(telegramConfig)`: the health routes (`/health`,
`/health/live`, `/health/ready`) are mounted **before** and outside the plugin (public); everything
else is under `route("/") { install(TelegramAuthPlugin) { … } }`:

```kotlin
install(TelegramAuthPlugin) {
    validator = authValidator
    allowedUserIds = telegramConfig.allowedUserIds.mapNotNull { it.toLongOrNull() }.toSet()
    allowedUsernames = telegramConfig.allowedUsernames.toSet()
}
workspaceRoutes(); previewRoutes(); jobRoutes(); ruleRoutes(); channelRoutes(); systemRoutes()
```

---

## 3. Two-Level Authorization

### 3.1 Level 1: Global Allow-list

Determines who can use the service at all (`TELEGRAM_ALLOWED_USER_IDS`, `TELEGRAM_ALLOWED_USERNAMES`,
comma-separated; CONFIGURATION.md §4–§5):

```yaml
telegram:
  allowedUserIds: "123456789, 987654321"
  allowedUsernames: "my_username"
```

- **Both lists empty = any Telegram user with valid initData gets in** (the server logs a `WARN` at
  start). This is the compatible default (ADR-009, G4).
- Valid initData, but the user is in neither list → `403 FORBIDDEN`.
- A non-numeric entry in `allowedUserIds` is dropped silently; if every entry is such, the list is
  effectively empty and access is open (open question in `project-status.md`).

### 3.2 Level 2: Workspace Membership

Determines which resources a user can access.

All domain resources (jobs, rules, channels, preview) are scoped to a workspace via the path:
`/api/v1/workspaces/{workspaceSlug}/...`

The use-case verifies (inside its transaction, `WorkspaceAccess.requireMember`) that the current user
is a member of the workspace — not a member → `403 FORBIDDEN`; unknown slug → `404 NOT_FOUND`; a
resource id of another workspace → `404 NOT_FOUND`.

Roles:
- **OWNER** — can manage members (add/remove)
- **MEMBER** — full access to all workspace resources

Known risk: `POST /workspaces` with a slug that already exists adds the caller as `MEMBER` (the client
relies on it to reconnect) — anyone who passes Level 1 and knows a slug can join that workspace.

System settings (`/api/v1/system/*`) are not workspace-scoped: every allowed user can read and change
them (yt-dlp update can be disabled with `ytDlp.allowUpdate: false`).

See also: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 4. Dev Mode

### 4.1 Configuration

```yaml
telegram:
  devMode: true  # LOCAL DEVELOPMENT ONLY!
```

`docker-compose.yaml` and `.env.example` default `TELEGRAM_DEV_MODE=true` (local-dev compose,
DEPLOYMENT.md §2.2).

### 4.2 Behavior

When `devMode = true`:
- `initData = "dev"` is accepted without validation;
- the dev user (id `1`, username `dev`) is returned; the allow-list still applies to it;
- the validator and the server start log a `WARN`.

With `devMode = false` the server refuses to start without a real bot token (empty, `test-token` or
`dev-token` → exit code 1, CONFIGURATION.md §7).

---

## 5. Path Security

### 5.1 Path traversal

- User-supplied storage paths (`POST …/jobs` storage plan, rule output templates) go through
  `validateStoragePaths()` (`domain/storage/`): any `..` or a forbidden character in a segment →
  `400 VALIDATION_ERROR`.
- File-name components rendered from video metadata go through `PathTemplateEngine`, which calls
  `FileNameValidator.sanitize` (§5.3).
- `DomainError.PathTraversalAttempt` exists and is mapped to `400`, but nothing produces it today.

### 5.2 Allowed directories

`storage.baseDirectories` is bound from the config but **not enforced**: nothing checks that an output
path lies inside those directories. In Docker the process can only write to its volumes
(`/data/media`, `/data/temp`) and `/app/bin`.

### 5.3 File-name sanitisation

`domain/common/FileNameValidator.kt`:

- `sanitize(value)` replaces `/ \ : * ? " < > |` with `_` and trims (no length limit, no control
  character removal);
- `validate(field, value)` / `isSafe(value)` reject blank values, those characters, `..` and a
  leading dot.

---

## 6. External Process Security

- yt-dlp and ffmpeg are started with an **argument list** (`ProcessBuilder(listOf(...))`), never a
  shell string (`YtDlpRunner`, `FfmpegRunner`, `YtDlpBootstrap`).
- Cancelling a job kills the whole process tree (`process/CancellableProcess.kt`: `SIGTERM`, up to
  5 s, then `SIGKILL`).
- **No timeout** is applied to the processes (`ytDlp.timeout` / `ffmpeg.timeout` are read but unused)
  and there is no output-size limit; concurrency is bounded by `jobs.maxConcurrentDownloads`.

---

## 7. Security Logging

Rules:

- never log the full `initData`, the bot token or the hash; the start log reports only the **number**
  of allow-listed ids/usernames;
- every log line carries the correlation id (`%X{correlationId}` in `logback.xml`), which is also in
  the `X-Correlation-Id` response header and in every error body;
- `CallLogging` logs requests; the auth plugin logs neither success nor failure.

---

## 8. Security Headers

Set for every response in `Application.kt` (`configureHttp`):

```kotlin
install(DefaultHeaders) {
    header("X-Content-Type-Options", "nosniff")
    header("X-Frame-Options", "DENY")
    header("X-XSS-Protection", "1; mode=block")
}
```

CORS: an allow-list of hosts from `cors.hosts` (`anyHost: false` by default), headers
`Content-Type`, `X-Telegram-Init-Data`, `X-Workspace-Id`; exposes `X-Correlation-Id`.

---

## 9. Rate Limiting

Not implemented: no `RateLimit` plugin is installed. Access is limited by the allow-list and by
Telegram's signature; a reverse proxy may add rate limits.

---

## 10. Security Checklist

- [ ] Bot token not in the repository (use env/secrets)
- [ ] `TELEGRAM_DEV_MODE=false` for any installation reachable from outside
- [ ] Allow-list configured (no "open to any Telegram user" `WARN` at start)
- [ ] initData is never logged in full
- [ ] External processes launched via argument list, not shell string
- [ ] HTTPS in production (via reverse proxy)
