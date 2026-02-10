# Безопасность

> **Цель документа**: Описание авторизации через Telegram initData, allowlist, защиты от атак.

---

## 1. Telegram initData

### 1.1 Что это

`initData` — строка, которую Telegram передаёт в Mini App. Содержит:
- Данные пользователя (user)
- Timestamp (auth_date)
- HMAC-подпись (hash)

### 1.2 Формат

```
query_id=AAHdF6IQAAAAAN0XohDhrOrc
&user=%7B%22id%22%3A123456789%2C%22first_name%22%3A%22John%22%7D
&auth_date=1234567890
&hash=c501b71e775f74ce10e377dea85a7ea24ecd640b223ea86dfe453e0eaed2e2b2
```

URL-encoded параметры, разделённые `&`.

### 1.3 Алгоритм валидации

```kotlin
class TelegramAuthValidator(
    private val botToken: String,
    private val devMode: Boolean = false,
    private val maxAgeSeconds: Long = 86400, // 24 часа
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
        
        // Проверка auth_date
        val authDate = params["auth_date"]?.toLongOrNull()
            ?: return AuthError.InvalidAuthDate.left()
        
        val age = clock.instant().epochSecond - authDate
        if (age > maxAgeSeconds) {
            return AuthError.Expired.left()
        }
        
        // Вычисление expected hash
        val dataCheckString = params.entries
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }
        
        val secretKey = hmacSha256("WebAppData".toByteArray(), botToken.toByteArray())
        val expectedHash = hmacSha256(secretKey, dataCheckString.toByteArray())
            .toHexString()
        
        // Timing-safe сравнение
        if (!MessageDigest.isEqual(hash.toByteArray(), expectedHash.toByteArray())) {
            return AuthError.InvalidHash.left()
        }
        
        // Парсинг user
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
                
                // Проверка allowlist
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
                
                // Сохраняем user в call attributes
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

### 2.2 Использование в routing

```kotlin
fun Application.configureRouting() {
    routing {
        route("/api/v1") {
            install(TelegramAuthPlugin) {
                validator = get<TelegramAuthValidator>()
                allowedUserIds = config.telegram.allowedUserIds.map { it.toLong() }.toSet()
            }
            
            previewRoutes()
            jobRoutes()
            ruleRoutes()
        }
    }
}
```

---

## 3. Allowlist

### 3.1 Конфигурация

```yaml
telegram:
  botToken: "123456:ABC-DEF..."
  allowedUserIds:
    - "123456789"
    - "987654321"
```

### 3.2 Поведение

- Пустой список = **всем запрещено** (fail-safe)
- `["*"]` = всем разрешено (не рекомендуется в production)
- initData валиден, но user не в списке → `403 FORBIDDEN`

---

## 4. Dev Mode

### 4.1 Конфигурация

```yaml
telegram:
  devMode: true  # ТОЛЬКО для локальной разработки!
```

### 4.2 Поведение

Когда `devMode = true`:
- `initData = "dev"` принимается без валидации
- Возвращается фейковый user с id=0

### 4.3 Защита

```kotlin
init {
    if (devMode) {
        val logger = KotlinLogging.logger {}
        logger.warn { "⚠️ TelegramAuthValidator running in DEV MODE - DO NOT USE IN PRODUCTION" }
    }
}
```

В production:
- `devMode` должен быть `false`
- Можно добавить проверку через environment variable

---

## 5. Безопасность путей

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

### 5.2 Конфигурация allowed directories

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
        // Удаляем запрещённые символы
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        // Удаляем управляющие символы
        .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
        // Схлопываем пробелы
        .replace(Regex("\\s+"), " ")
        // Trim
        .trim()
        // Ограничиваем длину
        .take(180)
        // Не допускаем пустое имя
        .ifBlank { "unnamed" }
}
```

---

## 6. Безопасность внешних процессов

### 6.1 Запуск yt-dlp

```kotlin
class YtDlpRunner(
    private val ytDlpPath: String,
    private val timeout: Duration = 30.minutes,
) {
    
    suspend fun run(args: List<String>): ProcessResult {
        // НЕ строить команду через строку!
        // Использовать список аргументов
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

### 6.2 Лимитирование ресурсов

```kotlin
data class ProcessLimits(
    val maxOutputSize: Int = 100_000,
    val timeout: Duration = 30.minutes,
    val maxConcurrent: Int = 3,
)
```

---

## 7. Логирование безопасности

### 7.1 Что логировать

✅ Логировать:
- Успешные и неуспешные попытки авторизации
- User ID при авторизации
- Correlation ID
- Попытки path traversal

❌ НЕ логировать:
- Полный initData
- Bot token
- Полный hash

### 7.2 Пример

```kotlin
// Хорошо
logger.info { "Auth success: userId=${user.id}" }
logger.warn { "Auth failed: reason=InvalidHash, hashPrefix=${hash.take(8)}..." }

// Плохо
logger.info { "Auth with initData=$initData" }  // ❌ Полный initData
```

---

## 8. Headers безопасности

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

## 9. Rate Limiting (опционально)

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

## 10. Чек-лист безопасности

- [ ] Bot token не в репозитории (через env/secrets)
- [ ] `devMode = false` в production
- [ ] Allowlist настроен
- [ ] initData не логируется полностью
- [ ] Path traversal защита включена
- [ ] Внешние процессы запускаются через list, не string
- [ ] Timeout на внешние процессы
- [ ] HTTPS в production (через reverse proxy)

