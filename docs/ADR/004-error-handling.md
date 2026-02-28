# ADR-004: Error Handling Strategy

**Статус**: Принято  
**Дата**: 2026-02-11  
**Авторы**: Alex Elkin

---

## Контекст

Нужно определить стратегию обработки ошибок:
- В domain layer
- При маппинге DTO → Domain
- В transport layer (HTTP)
- В UI

Требования:
- Type-safe (компилятор помогает не забыть обработку)
- Понятные сообщения для пользователя
- Достаточно деталей для отладки
- Единообразие

---

## Решение

### Domain Layer

Использовать **`Either<DomainError, T>`** из Arrow.

```kotlin
sealed interface DomainError {
    val message: String
    
    data class ValidationError(val field: String, override val message: String) : DomainError
    data class VideoUnavailable(val videoId: String, val reason: String) : DomainError
    data class RuleNotFound(val id: RuleId) : DomainError
    // ...
}

// Use case возвращает Either
suspend fun PreviewUseCase.execute(url: String): Either<DomainError, PreviewResult>
```

### Маппинг DTO → Domain

Возвращать **`Either<ValidationError, T>`**.

```kotlin
fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.ChannelId -> 
        if (value.isBlank()) DomainError.ValidationError("value", "cannot be blank").left()
        else RuleMatch.ChannelId(value).right()
    // ...
}
```

### Transport Layer

Маппить `DomainError` в HTTP статус + `ApiErrorDto`.

```kotlin
fun DomainError.toHttpResponse(correlationId: String): Pair<HttpStatusCode, ApiErrorDto> = when (this) {
    is DomainError.ValidationError -> HttpStatusCode.BadRequest to apiError("VALIDATION_ERROR")
    is DomainError.RuleNotFound -> HttpStatusCode.NotFound to apiError("NOT_FOUND")
    is DomainError.VideoUnavailable -> HttpStatusCode.UnprocessableEntity to apiError("VIDEO_UNAVAILABLE")
    is DomainError.Unauthorized -> HttpStatusCode.Unauthorized to apiError("UNAUTHORIZED")
    is DomainError.Forbidden -> HttpStatusCode.Forbidden to apiError("FORBIDDEN")
    // ...
}
```

### API Error Format

```kotlin
@Serializable
data class ApiErrorDto(
    val error: ErrorDetail,
) {
    @Serializable
    data class ErrorDetail(
        val code: String,           // Стабильный код для клиента
        val message: String,        // Человекочитаемое сообщение
        val correlationId: String,  // Для трассировки
        val details: JsonElement? = null,  // Дополнительные данные
    )
}
```

JSON:
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Field 'url' cannot be blank",
    "correlationId": "abc-123",
    "details": { "field": "url" }
  }
}
```

### Exception Handling

Неожиданные исключения ловить в Ktor StatusPages:

```kotlin
install(StatusPages) {
    exception<Throwable> { call, cause ->
        logger.error(cause) { "Unhandled exception" }
        
        call.respond(HttpStatusCode.InternalServerError, ApiErrorDto(
            error = ApiErrorDto.ErrorDetail(
                code = "INTERNAL_ERROR",
                message = "Internal server error",
                correlationId = call.correlationId,
            )
        ))
    }
}
```

---

## Обоснование

### Почему Either, а не exceptions?

| Подход | Плюсы | Минусы |
|--------|-------|--------|
| **Either** | Type-safe, явный, composable | Больше кода, нужен Arrow |
| **Exceptions** | Привычно, меньше кода | Неявный flow, легко забыть |
| **Result** (stdlib) | Встроенный, простой | Только один тип ошибки (Throwable) |

**Выбор**: Either — лучший баланс безопасности и выразительности.

### Почему sealed interface для DomainError?

- Exhaustive when — компилятор проверяет все cases
- Каждый тип ошибки имеет свои поля
- Легко добавлять новые типы ошибок

### Почему correlationId?

- Связывает логи с запросом
- Помогает при отладке
- Пользователь может сообщить ID в support

---

## Паттерны использования

### 1. Use Case с Either

```kotlin
class CreateJobUseCase(...) {
    suspend fun execute(request: CreateJobRequest): Either<DomainError, Job> = either {
        // ensure = проверка с ранним выходом
        ensure(request.url.isNotBlank()) {
            DomainError.ValidationError("url", "cannot be blank")
        }
        
        // bind = извлечение из Either или ранний выход
        val videoInfo = videoInfoExtractor.extract(request.url).bind()
        
        // raise = явный выход с ошибкой
        val existing = jobRepository.findActive(videoInfo.videoId)
        if (existing != null) {
            raise(DomainError.JobAlreadyExists(videoInfo.videoId, existing.id))
        }
        
        // Успех
        jobRepository.save(newJob)
    }
}
```

### 2. Route Handler

```kotlin
// Пример: workspace-scoped endpoint
post<ApiV1.Workspaces.ById.Jobs> { res ->
    val request = call.receive<CreateJobRequestDto>()
    val domainRequest = request.toDomain().getOrElse { error ->
        call.respond(error.toHttpResponse(call.correlationId))
        return@post
    }
    
    when (val result = createJobUseCase.execute(domainRequest)) {
        is Either.Left -> call.respond(result.value.toHttpResponse(call.correlationId))
        is Either.Right -> call.respond(HttpStatusCode.Created, result.value.toDto())
    }
}
```

### 3. Композиция

```kotlin
suspend fun complexOperation(): Either<DomainError, Result> = either {
    val a = operationA().bind()
    val b = operationB(a).bind()
    val c = operationC(b).bind()
    Result(a, b, c)
}
```

---

## Коды ошибок

| Code                | HTTP | Описание                      | Retry    |
|---------------------|------|-------------------------------|----------|
| `VALIDATION_ERROR`  | 400  | Невалидные входные данные     | Нет      |
| `INVALID_URL`       | 400  | Некорректный URL              | Нет      |
| `UNAUTHORIZED`      | 401  | Невалидный initData           | Нет      |
| `FORBIDDEN`         | 403  | Пользователь не в allowlist   | Нет      |
| `NOT_FOUND`         | 404  | Ресурс не найден              | Нет      |
| `CONFLICT`          | 409  | Конфликт (job уже существует) | Нет      |
| `VIDEO_UNAVAILABLE` | 422  | Видео недоступно              | Возможно |
| `DOWNLOAD_FAILED`   | 500  | Ошибка скачивания             | Да       |
| `INTERNAL_ERROR`    | 500  | Внутренняя ошибка             | Да       |

---

## Последствия

### Положительные

- Compiler-checked error handling
- Явный flow ошибок
- Единый формат для клиента
- Удобная отладка через correlationId
- **Arrow Either — полная KMP-совместимость**: стратегия работает одинаково в `commonMain` на JVM и JS

### Отрицательные

- Зависимость от Arrow
- Больше boilerplate
- Нужно обучение команды

---

## Ссылки

- [Arrow Either](https://arrow-kt.io/docs/apidocs/arrow-core/arrow.core/-either/)
- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/)
