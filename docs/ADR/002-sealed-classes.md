# ADR-002: Sealed Classes for Polymorphic Types

**Status**: Accepted  
**Date**: 2026-02-11  
**Authors**: Alex Elkin

---

## Context

The domain model contains several polymorphic types:
- **RuleMatch**: matching criteria (ChannelId, ChannelName, TitleRegex, UrlRegex, CategoryEquals, AllOf, AnyOf)
- **ResolvedMetadata**: metadata for different categories (MusicVideo, SeriesEpisode, Other)
- **MetadataTemplate**: metadata extraction templates, mirrors ResolvedMetadata (MusicVideo, SeriesEpisode, Other)
- **UserOverrides**: user refinements, mirrors ResolvedMetadata by category (MusicVideo, SeriesEpisode, Other)
- **OutputFormat**: type and format of output files (OriginalVideo, ConvertedVideo, Audio, Thumbnail)
- **DomainError**: various error types

We need to decide:
1. How to model these types in Kotlin
2. How to serialize them to JSON for the API
3. How to store them in PostgreSQL

---

## Decision

### Domain Layer

Use **sealed interface/class** for all polymorphic types.

```kotlin
sealed interface RuleMatch {
    data class ChannelId(val value: String) : RuleMatch
    data class AllOf(val matches: List<RuleMatch>) : RuleMatch
    // ...
}

sealed interface ResolvedMetadata {
    data class MusicVideo(val artist: String, val title: String, ...) : ResolvedMetadata
    data class SeriesEpisode(val seriesName: String, ...) : ResolvedMetadata
    // ...
}
```

### API Layer (DTO)

Use **sealed interface + @SerialName** for kotlinx.serialization with `type` discriminator.

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchDto {
    @Serializable
    @SerialName("channel-id")
    data class ChannelId(val value: String) : RuleMatchDto
    // ...
}
```

JSON:
```json
{ "type": "channel-id", "value": "UC123" }
```

### Database

Store as **JSONB** using the same format as the API.

```sql
match JSONB NOT NULL  -- { "type": "channel-id", "value": "UC123" }
```

---

## Rationale

### Why sealed interface instead of enum + data?

| Approach                 | Pros                                              | Cons                                         |
|--------------------------|---------------------------------------------------|----------------------------------------------|
| **sealed interface**     | Type-safe, exhaustive when, nested structures     | Slightly more boilerplate                    |
| **enum + data class**    | Simpler for flat types                            | Does not support nesting (AllOf/AnyOf)       |
| **open class hierarchy** | Flexible                                          | Not exhaustive, easy to miss a case          |

**Choice**: sealed interface — best balance of safety and flexibility.

### Why `type` discriminator instead of `@type` or `class`?

- `type` — simple, clear name
- Compatible with most clients
- Does not conflict with reserved keywords

### Why JSONB for storage?

| Approach | Pros | Cons |
|--------|-------|--------|
| **JSONB** | Flexible, GIN indexes, single format everywhere | No FK constraints |
| **Normalized tables** | FK, strict schema | Complex JOINs, many tables |
| **PostgreSQL table inheritance** | Built-in support | Complexity, limitations |

**Choice**: JSONB — simpler, sufficient for MVP, can be migrated later.

---

## Implementation

### Domain ↔ DTO Mapping

Separate layer (`api:mapping`) with extension functions:

```kotlin
fun RuleMatch.toDto(): RuleMatchDto = when (this) {
    is RuleMatch.ChannelId -> RuleMatchDto.ChannelId(value)
    is RuleMatch.AllOf -> RuleMatchDto.AllOf(matches.map { it.toDto() })
    // exhaustive when
}

fun RuleMatchDto.toDomain(): Either<ValidationError, RuleMatch> = when (this) {
    is RuleMatchDto.ChannelId -> 
        if (value.isBlank()) ValidationError(...).left()
        else RuleMatch.ChannelId(value).right()
    // ...
}
```

### Validation

- **Domain level**: in `init {}` blocks (fail fast)
- **DTO level**: during mapping to domain (return Either)

---

## Consequences

### Positive

- Compiler enforces exhaustiveness
- Impossible to forget a case in `when`
- Single JSON format for API and DB
- Easy to add new types
- **Full KMP compatibility**: sealed classes work identically in `commonMain` on both JVM and JS

### Negative

- More boilerplate for mapping
- Domain and DTO hierarchies must be kept in sync

### Migrations

When adding a new type:
1. Add to domain sealed class
2. Add to DTO sealed class with `@SerialName`
3. Add mapping
4. Existing data in DB remains valid

---

## Full Flow Example

```kotlin
// 1. Receive JSON
val json = """{"type": "channel-id", "value": "UC123"}"""

// 2. Deserialize to DTO
val dto: RuleMatchDto = Json.decodeFromString(json)

// 3. Map to domain (with validation)
val domain: Either<ValidationError, RuleMatch> = dto.toDomain()

// 4. Use in business logic
domain.map { match ->
    match.matches(videoInfo)  // true/false
}

// 5. Save to DB as JSONB
RulesTable.insert {
    it[match] = domain.toDto()  // serialized back to JSON
}
```

---

## References

- [Kotlin Sealed Classes](https://kotlinlang.org/docs/sealed-classes.html)
- [kotlinx.serialization Polymorphism](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md)
