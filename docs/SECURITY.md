# Security

> **Purpose**: Authorization via Telegram initData, allowlist, and protection against common attacks.

---

## 1. Telegram initData

### 1.1 What It Is

`initData` — a string passed by Telegram to the Mini App. Contains:
- User data (user)
- Timestamp (auth_date)
- HMAC signature (hash)

### 1.2 Format

```
query_id=AAHdF6IQAAAAAN0XohDhrOrc
&user=%7B%22id%22%3A123456789%2C%22first_name%22%3A%22John%22%7D
&auth_date=1234567890
&hash=c501b71e775f74ce10e377dea85a7ea24ecd640b223ea86dfe453e0eaed2e2b2
```

URL-encoded parameters separated by `&`.

### 1.3 Validation Algorithm

```kotlin
class TelegramAuthValidator(
    private val botToken: String,
    private val devMode: Boolean = false,
    private val maxAgeSeconds: Long = 86400, // 24 hours
    private val clock: Clock = Clock.systemUTC(),
) {
    
    fun validate(initData: String): Either<AuthError, TelegramUser> {
        if (devMode && initData == "dev") {
            return TelegramUser(
                id = TelegramUserId(0),
                firstName = "Dev User",
                lastName = null,
                username = "dev",
            ).right()
        }
        
        val params = parseInitData(initData)
        val hash = params.remove("hash") 
            ?: return AuthError.MissingHash.left()
        
        // Validate auth_date
        val authDate = params["auth_date"]?.toLongOrNull()
            ?: return AuthError.InvalidAuthDate.left()
        
        val age = clock.instant().epochSecond - authDate
        if (age > maxAgeSeconds) {
            return AuthError.Expired.left()
        }
        
        // Compute expected hash
        val dataCheckString = params.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }
        
        val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
        val expectedHash = hmacSha256(secretKey, dataCheckString.toByteArray())
            .toHexString()
        
        // Timing-safe comparison
        if (!MessageDigest.isEqual(hash.toByteArray(), expectedHash.toByteArray())) {
            return AuthError.InvalidHash.left()
        }
        
        // Parse user
        val userJson = params["user"] 
            ?: return AuthError.MissingUser.left()
        
        return try {
            val user = json.decodeFromString<TelegramUserDto>(userJson)
            TelegramUser(
                id = TelegramUserId(user.id),
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
            ).right()
        } catch (e: Exception) {
            AuthError.InvalidUser.left()
        }
    }
    
    private fun parseInitData(initData: String): MutableMap<String, String> {
        return initData.split("&")
            .associate { 
                val (key, value) = it.split("=", limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }
            .toMutableMap()
    }
    
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }
    
    sealed interface AuthError {
        data object MissingHash : AuthError
        data object InvalidHash : AuthError
        data object InvalidAuthDate : AuthError
        data object Expired : AuthError
        data object MissingUser : AuthError
        data object InvalidUser : AuthError
    }
}
```

### 1.4 DTO

```kotlin
@Serializable
data class TelegramUserDto(
    val id: Long,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String? = null,
    val username: String? = null,
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("is_premium")
    val isPremium: Boolean? = null,
)
```

---

## 2. Ktor Auth Plugin

### 2.1 TelegramAuthPlugin

```kotlin
val TelegramAuthPlugin = createRouteScopedPlugin(
    name = "TelegramAuth",
    createConfiguration = ::TelegramAuthConfig,
) {
    val validator = pluginConfig.validator
    val allowedUsers = pluginConfig.allowedUserIds
    
    onCall { call ->
        val initData = call.request.headers["X-Telegram-Init-Data"]
        
        if (initData == null) {
            call.respond(HttpStatusCode.Unauthorized, ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "UNAUTHORIZED",
                    message = "Missing X-Telegram-Init-Data header",
                    correlationId = call.correlationId,
                )
            ))
            return@onCall
        }
        
        when (val result = validator.validate(initData)) {
            is Either.Left -> {
                call.respond(HttpStatusCode.Unauthorized, ApiErrorDto(
                    error = ApiErrorDto.ErrorDetail(
                        code = "UNAUTHORIZED",
                        message = "Invalid initData: ${result.value}",
                        correlationId = call.correlationId,
                    )
                ))
            }
            is Either.Right -> {
                val user = result.value
                
                // Check allowlist
                if (allowedUsers.isNotEmpty() && user.id.value !in allowedUsers) {
                    call.respond(HttpStatusCode.Forbidden, ApiErrorDto(
                        error = ApiErrorDto.ErrorDetail(
                            code = "FORBIDDEN",
                            message = "User not allowed",
                            correlationId = call.correlationId,
                        )
                    ))
                    return@onCall
                }
                
                // Store user in call attributes
                call.attributes.put(TelegramUserKey, user)
            }
        }
    }
}

class TelegramAuthConfig {
    lateinit var validator: TelegramAuthValidator
    var allowedUserIds: Set<Long> = emptySet()
}

val TelegramUserKey = AttributeKey<TelegramUser>("TelegramUser")

val ApplicationCall.telegramUser: TelegramUser
    get() = attributes[TelegramUserKey]

val ApplicationCall.telegramUserOrNull: TelegramUser?
    get() = attributes.getOrNull(TelegramUserKey)
```

### 2.2 Usage in Routing

```kotlin
fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            install(TelegramAuthPlugin) {
                validator = get<TelegramAuthValidator>()
                allowedUserIds = config.telegram.allowedUserIds.map { it.toLong() }.toSet()
            }

            workspaceRoutes()
            previewRoutes()
            jobRoutes()
            ruleRoutes()
            systemRoutes()
        }
    }
}
```

---

## 3. Two-Level Authorization

### 3.1 Level 1: Global Allowlist

Determines who can access the service at all.

```yaml
telegram:
  allowedUserIds:
    - "123456789"
    - "987654321"
```

- Empty list = **everyone is denied** (fail-safe)
- Valid initData, but user not in list → `403 FORBIDDEN`

### 3.2 Level 2: Workspace Membership

Determines which resources a user can access.

All domain resources (jobs, rules, preview) are scoped to a workspace via path:
`/api/v1/workspaces/{workspaceId}/...`

The server verifies that the current user is a member of the workspace.
If not — `403 WORKSPACE_ACCESS_DENIED`.

Roles:
- **OWNER** — can manage members (add/remove)
- **MEMBER** — full access to all workspace resources

See also: [ADR/006-workspaces.md](./ADR/006-workspaces.md)

---

## 4. Dev Mode

### 4.1 Configuration

```yaml
telegram:
  devMode: true  # LOCAL DEVELOPMENT ONLY!
```

### 4.2 Behavior

When `devMode = true`:
- `initData = "dev"` is accepted without validation
- A fake user with id=0 is returned

### 4.3 Safety Guard

```kotlin
init {
    if (devMode) {
        val logger = KotlinLogging.logger {}
        logger.warn { "⚠️ TelegramAuthValidator running in DEV MODE - DO NOT USE IN PRODUCTION" }
    }
}
```

In production:
- `devMode` must be `false`
- Consider adding an environment variable check as an additional guard

---

## 5. Path Security

### 5.1 Path Traversal Protection

```kotlin
fun validatePath(path: Path, allowedRoots: List<Path>): Either<DomainError, Path> {
    val normalized = path.normalize().toAbsolutePath()
    
    val isWithinAllowed = allowedRoots.any { root ->
        normalized.startsWith(root.normalize().toAbsolutePath())
    }
    
    return if (isWithinAllowed) {
        normalized.right()
    } else {
        DomainError.PathTraversalAttempt(path.toString()).left()
    }
}
```

### 5.2 Allowed Directory Configuration

```yaml
storage:
  baseDirectories:
    - "/media/Music Videos"
    - "/media/TV"
    - "/media/Videos"
  tempDirectory: "/tmp/tgvd"
```

### 5.3 Filename Sanitization

```kotlin
fun sanitizeFilename(name: String): String {
    return name
        // Remove forbidden characters
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        // Remove control characters
        .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
        // Collapse whitespace
        .replace(Regex("\\s+"), " ")
        // Trim
        .trim()
        // Limit length
        .take(180)
        // Prevent empty filename
        .ifBlank { "unnamed" }
}
```

---

## 6. External Process Security

### 6.1 Launching yt-dlp

```kotlin
class YtDlpRunner(
    private val ytDlpPath: String,
    private val timeout: Duration = 30.minutes,
) {
    
    suspend fun run(args: List<String>): ProcessResult {
        // Do NOT build the command as a string!
        // Always use a list of arguments to prevent shell injection
        val command = listOf(ytDlpPath) + args
        
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        
        return withTimeout(timeout) {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            ProcessResult(exitCode, output.takeLast(MAX_OUTPUT_SIZE))
        }
    }
    
    companion object {
        private const val MAX_OUTPUT_SIZE = 100_000  // 100KB
    }
}
```

### 6.2 Resource Limits

```kotlin
data class ProcessLimits(
    val maxOutputSize: Int = 100_000,
    val timeout: Duration = 30.minutes,
    val maxConcurrent: Int = 3,
)
```

---

## 7. Security Logging

### 7.1 What to Log

✅ Log:
- Successful and failed authorization attempts
- User ID on authentication
- Correlation ID
- Path traversal attempts

❌ Do NOT log:
- Full initData
- Bot token
- Full hash value

### 7.2 Example

```kotlin
// Good
logger.info { "Auth success: userId=${user.id}" }
logger.warn { "Auth failed: reason=InvalidHash, hashPrefix=${hash.take(8)}..." }

// Bad
logger.info { "Auth with initData=$initData" }  // ❌ Full initData exposed
```

---

## 8. Security Headers

```kotlin
fun Application.configureSecurityHeaders() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("X-XSS-Protection", "1; mode=block")
    }
}
```

---

## 9. Rate Limiting (optional)

```kotlin
val RateLimitPlugin = createRouteScopedPlugin("RateLimit") {
    val limiter = RateLimiter.create(10.0)  // 10 requests/sec per user
    
    onCall { call ->
        val userId = call.telegramUserOrNull?.id?.value ?: return@onCall
        
        if (!limiter.tryAcquire(userId)) {
            call.respond(HttpStatusCode.TooManyRequests, ApiErrorDto(
                error = ApiErrorDto.ErrorDetail(
                    code = "RATE_LIMIT",
                    message = "Too many requests",
                    correlationId = call.correlationId,
                )
            ))
        }
    }
}
```

---

## 10. Security Checklist

- [ ] Bot token not in the repository (use env/secrets)
- [ ] `devMode = false` in production
- [ ] Allowlist is configured
- [ ] initData is never logged in full
- [ ] Path traversal protection is active
- [ ] External processes launched via argument list, not shell string
- [ ] Timeout set on all external processes
- [ ] HTTPS in production (via reverse proxy)
