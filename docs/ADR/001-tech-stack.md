# ADR-001: Технологический стек

**Статус**: Принято  
**Дата**: 2026-02-11  
**Авторы**: Alex Elkin

---

## Контекст

Нужно выбрать технологический стек для сервиса скачивания YouTube-видео с управлением через Telegram Mini App.

Требования:
- Единый язык для backend и frontend
- Современный, идиоматичный Kotlin
- Type-safe API между клиентом и сервером
- Простота деплоя
- Хорошая поддержка асинхронности

---

## Решение

### Backend

| Компонент         | Выбор                    | Альтернативы          | Обоснование                                       |
|-------------------|--------------------------|-----------------------|---------------------------------------------------|
| **Язык**          | Kotlin 2.3+              | Java, Scala           | Современный синтаксис, sealed classes, coroutines |
| **JVM**           | 21 LTS                   | 17                    | Performance improvements                          |
| **Framework**     | Ktor 3.x                 | Spring Boot           | Легковесный, Kotlin-first, coroutines-native      |
| **DI**            | Koin 4.x                 | Kodein, Dagger        | Простой DSL, хорошая интеграция с Ktor            |
| **Serialization** | kotlinx.serialization    | Jackson, Gson         | Compile-time, sealed classes support              |
| **Database**      | PostgreSQL 16            | MySQL, SQLite         | JSONB, надёжность, production-ready               |
| **ORM**           | Exposed                  | Ktorm, jOOQ           | Kotlin DSL, type-safe, активная разработка        |
| **Migrations**    | Flyway                   | Liquibase             | Простота, SQL-файлы                               |
| **Config**        | Hoplite                  | Konf, Typesafe Config | Kotlin DSL, env support, profiles                 |
| **Logging**       | kotlin-logging + Logback | Log4j2                | SLF4J совместимость, структурные логи             |

### Frontend (Telegram Mini App)

| Компонент       | Выбор                 | Альтернативы | Обоснование                            |
|-----------------|-----------------------|--------------|----------------------------------------|
| **UI**          | Compose Multiplatform | React, Vue   | Единый стек Kotlin, type-safe          |
| **Target**      | JS (Browser)          | Wasm         | Лучшая совместимость с Telegram WebApp |
| **HTTP Client** | Ktor Client           | Fetch API    | KMP, type-safe, переиспользование DTO  |

### Инфраструктура

| Компонент            | Выбор               | Альтернативы    | Обоснование                           |
|----------------------|---------------------|-----------------|---------------------------------------|
| **Video download**   | yt-dlp (subprocess) | youtube-dl, API | Лучшая поддержка, активная разработка |
| **Video processing** | ffmpeg              | —               | Стандарт индустрии                    |
| **Containerization** | Docker              | Podman          | Универсальность                       |

### Тестирование

| Компонент          | Выбор          | Альтернативы | Обоснование                       |
|--------------------|----------------|--------------|-----------------------------------|
| **Test framework** | Kotest         | JUnit 5      | BDD-стиль, property-based testing |
| **Mocking**        | MockK          | Mockito      | Kotlin-first, coroutines support  |
| **Integration**    | Testcontainers | Embedded DB  | Реальная БД, надёжность           |

---

## Последствия

### Положительные

- Единый язык Kotlin везде
- Type-safe API между клиентом и сервером через общие DTO
- Современные возможности языка (sealed classes, coroutines)
- Переиспользование кода между платформами
- Активное сообщество и развитие инструментов

### Отрицательные

- Compose Multiplatform for Web менее зрелый, чем React
- Меньше готовых UI-компонентов для web
- Размер JS bundle может быть больше
- Требуется знание специфики KMP

### Риски

- **Compose Web stability**: Следить за релизами, иметь fallback план
- **yt-dlp breaking changes**: Версионировать, тестировать после обновлений

---

## Ссылки

- [Ktor Documentation](https://ktor.io/docs/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)

