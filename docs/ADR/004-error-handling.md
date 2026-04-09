# ADR-004: Error Handling Strategy

**Status**: Accepted  
**Date**: 2026-02-11  
**Authors**: Alex Elkin

---

## Context

We need to define an error handling strategy for:
- Domain layer
- DTO → Domain mapping
- Transport layer (HTTP)
- UI

Requirements:
- Type-safe (compiler helps ensure all errors are handled)
- Meaningful messages for the user
- Enough detail for debugging
- Consistency across layers

---

## Decision

### Domain Layer

Use **`Either<DomainError, T>`** from Arrow.

```kotlin
sealed interface DomainError {
    val message: String
    
    data class ValidationError(val field: String, override val message: String) : DomainError
    data class VideoUnavailable(val videoId: String, val reason: String) : DomainError
    data class RuleNotFound(val id: RuleId) : DomainError
    // ...
}

// Use case returns Either
suspend operator fun invoke(url: String, workspaceId: WorkspaceId): Either<DomainError, PreviewResult>
```

### DTO → Domain Mapping

Return **`Either<ValidationError, T>`**.

```kotlin
fun RuleMatchDto.toDomain(): Either<DomainError.ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.ChannelId -> 
        if (value.isBlank()) DomainError.ValidationError("value", "cannot be blank").left()
        else RuleMatch.ChannelId(value).right()
    // ...
}
```

### Transport Layer

Map `DomainError` to HTTP status + `ApiErrorDto`.

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
        val code: String,           // Stable code for the client
        val message: String,        // Human-readable message
        val correlationId: String,  // For tracing
        val details: JsonElement? = null,  // Additional data
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

Catch unexpected exceptions in Ktor `StatusPages`:

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

## Rationale

### Why Either instead of exceptions?

| Approach   | Pros                                         | Cons                                      |
|------------|----------------------------------------------|-------------------------------------------|
| **Either** | Type-safe, explicit, composable              | More code, requires Arrow                 |
| **Exceptions** | Familiar, less code                      | Implicit flow, easy to forget             |
| **Result** (stdlib) | Built-in, simple                    | Only one error type (Throwable)           |

**Choice**: Either — best balance of safety and expressiveness.

### Why sealed interface for DomainError?

- Exhaustive `when` — compiler checks all cases
- Each error type has its own fields
- Easy to add new error types

### Why correlationId?

- Links logs to a specific request
- Aids debugging
- Users can reference the ID in support requests

---

## Usage Patterns

### 1. Use Case with Either

```kotlin
class CreateJobUseCase(
    private val jobRepository: JobRepository,
    private val txRunner: TransactionRunner,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(request: CreateJobRequest): Either<DomainError, Job> =
        txRunner.inRwTransaction {
            either {
                // ensure = check with early exit
                ensure(request.source.url.value.isNotBlank()) {
                    DomainError.ValidationError("url", "cannot be blank")
                }

                // bind = extract from Either or exit early
                val activeJobs = jobRepository.findActive()
                    .filter { it.source.videoId == request.source.videoId }

                // raise = explicit exit with error
                if (activeJobs.isNotEmpty()) {
                    raise(DomainError.JobAlreadyExists(request.source.videoId, activeJobs.first().id))
                }

                // Success
                jobRepository.save(newJob).bind()
            }
        }
}
```

### 2. Route Handler

```kotlin
// Example: workspace-scoped endpoint
post<ApiV1.Workspaces.ById.Jobs> { res ->
    val request = call.receive<CreateJobRequestDto>()
    val domainRequest = request.toDomain().getOrElse { error ->
        call.respond(error.toHttpResponse(call.correlationId))
        return@post
    }
    
    when (val result = createJobUseCase(domainRequest)) {
        is Either.Left -> call.respond(result.value.toHttpResponse(call.correlationId))
        is Either.Right -> call.respond(HttpStatusCode.Created, result.value.toDto())
    }
}
```

### 3. Composition

```kotlin
suspend fun complexOperation(): Either<DomainError, Result> = either {
    val a = operationA().bind()
    val b = operationB(a).bind()
    val c = operationC(b).bind()
    Result(a, b, c)
}
```

---

## Error Codes

| Code                | HTTP | Description                      | Retryable |
|---------------------|------|----------------------------------|-----------|
| `VALIDATION_ERROR`  | 400  | Invalid input data               | No        |
| `INVALID_URL`       | 400  | Malformed URL                    | No        |
| `UNAUTHORIZED`      | 401  | Invalid initData                 | No        |
| `FORBIDDEN`         | 403  | User not in allowlist            | No        |
| `NOT_FOUND`         | 404  | Resource not found               | No        |
| `CONFLICT`          | 409  | Conflict (job already exists)    | No        |
| `VIDEO_UNAVAILABLE` | 422  | Video unavailable                | Maybe     |
| `DOWNLOAD_FAILED`   | 500  | Download error                   | Yes       |
| `INTERNAL_ERROR`    | 500  | Internal server error            | Yes       |

---

## Consequences

### Positive

- Compiler-checked error handling
- Explicit error flow
- Consistent format for clients
- Easy debugging via correlationId
- **Arrow Either — full KMP compatibility**: the strategy works identically in `commonMain` on JVM and JS

### Negative

- Dependency on Arrow
- More boilerplate
- Team learning curve

---

## References

- [Arrow Either](https://arrow-kt.io/docs/apidocs/arrow-core/arrow.core/-either/)
- [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/)
