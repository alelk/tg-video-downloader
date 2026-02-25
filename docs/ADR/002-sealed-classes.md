# ADR-002: Sealed Classes для полиморфных типов

**Статус**: Принято  
**Дата**: 2026-02-11  
**Авторы**: Alex Elkin

---

## Контекст

В доменной модели есть полиморфные типы:
- **RuleMatch**: критерии матчинга (ChannelId, ChannelName, TitleRegex, AllOf, AnyOf)
- **ResolvedMetadata**: метаданные разных категорий (MusicVideo, SeriesEpisode, Other)
- **DomainError**: различные типы ошибок

Нужно решить:
1. Как моделировать эти типы в Kotlin
2. Как сериализовать их в JSON для API
3. Как хранить в PostgreSQL

---

## Решение

### Доменный уровень

Использовать **sealed interface/class** для всех полиморфных типов.

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

### API уровень (DTO)

Использовать **sealed interface + @SerialName** для kotlinx.serialization с discriminator `type`.

```kotlin
@Serializable
@JsonClassDiscriminator("type")
sealed interface RuleMatchDto {
    @Serializable
    @SerialName("channelId")
    data class ChannelId(val value: String) : RuleMatchDto
    // ...
}
```

JSON:
```json
{ "type": "channelId", "value": "UC123" }
```

### База данных

Хранить как **JSONB** с тем же форматом, что и API.

```sql
match JSONB NOT NULL  -- { "type": "channelId", "value": "UC123" }
```

---

## Обоснование

### Почему sealed interface, а не enum + data?

| Подход                   | Плюсы                                           | Минусы                                    |
|--------------------------|-------------------------------------------------|-------------------------------------------|
| **sealed interface**     | Type-safe, exhaustive when, вложенные структуры | Немного больше кода                       |
| **enum + data class**    | Проще для плоских типов                         | Не поддерживает вложенность (AllOf/AnyOf) |
| **open class hierarchy** | Гибкость                                        | Не exhaustive, опасность забыть case      |

**Выбор**: sealed interface — лучший баланс безопасности и гибкости.

### Почему discriminator `type`, а не `@type` или `class`?

- `type` — простое, понятное имя
- Совместимость с большинством клиентов
- Не конфликтует с зарезервированными словами

### Почему JSONB для хранения?

| Подход | Плюсы | Минусы |
|--------|-------|--------|
| **JSONB** | Гибкость, GIN-индексы, один формат везде | Нет FK constraints |
| **Нормализованные таблицы** | FK, строгая схема | Сложные JOIN, много таблиц |
| **Наследование PostgreSQL** | Встроенная поддержка | Сложность, ограничения |

**Выбор**: JSONB — проще, достаточно для MVP, можно мигрировать позже.

---

## Реализация

### Маппинг Domain <-> DTO

Отдельный слой (`api-mapping`) с extension functions:

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

### Валидация

- **Domain level**: в `init {}` блоках (fail fast)
- **DTO level**: при маппинге в domain (возвращаем Either)

---

## Последствия

### Положительные

- Compiler проверяет exhaustiveness
- Невозможно забыть case в when
- Единый формат JSON для API и DB
- Легко добавлять новые типы

### Отрицательные

- Больше boilerplate для маппинга
- Нужно синхронизировать domain и DTO иерархии

### Миграции

При добавлении нового типа:
1. Добавить в domain sealed class
2. Добавить в DTO sealed class с @SerialName
3. Добавить маппинг
4. Существующие данные в DB остаются валидными

---

## Пример полного flow

```kotlin
// 1. Получаем JSON
val json = """{"type": "channelId", "value": "UC123"}"""

// 2. Десериализуем в DTO
val dto: RuleMatchDto = Json.decodeFromString(json)

// 3. Маппим в domain (с валидацией)
val domain: Either<ValidationError, RuleMatch> = dto.toDomain()

// 4. Используем в бизнес-логике
domain.map { match ->
    match.matches(videoInfo)  // true/false
}

// 5. Сохраняем в DB как JSONB
RulesTable.insert {
    it[match] = domain.toDto()  // сериализуется обратно в JSON
}
```

---

## Ссылки

- [Kotlin Sealed Classes](https://kotlinlang.org/docs/sealed-classes.html)
- [kotlinx.serialization Polymorphism](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/polymorphism.md)

