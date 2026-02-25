# ADR-001: Технологический стек

**Статус**: Принято  
**Дата**: 2026-02-25  
**Авторы**: Alex Elkin

---

## Контекст

Нужно выбрать технологический стек для сервиса скачивания YouTube-видео с управлением через Telegram Mini App.

Требования:
- Единый язык для backend, shared-логики и frontend
- Kotlin Multiplatform для переиспользования кода
- Type-safe API между клиентом и сервером
- Простота деплоя
- Хорошая поддержка асинхронности
- Возможность добавить другие UI-платформы (desktop, Android, web)

---

## Решение

### Backend (JVM only)

| Компонент         | Выбор                    | Альтернативы          | Обоснование                                  |
|-------------------|--------------------------|-----------------------|----------------------------------------------|
| **Язык**          | Kotlin 2.3+ (KMP)        | Java, Scala           | KMP, sealed classes, coroutines              |
| **JVM**           | 21 LTS                   | 17                    | Performance improvements                     |
| **Framework**     | Ktor 3.x                 | Spring Boot           | Легковесный, Kotlin-first, coroutines-native |
| **DI**            | Koin 4.x                 | Kodein, Dagger        | Простой DSL, KMP-совместим                   |
| **Serialization** | kotlinx.serialization    | Jackson, Gson         | Compile-time, KMP, sealed classes support    |
| **Database**      | PostgreSQL 16            | MySQL, SQLite         | JSONB, надёжность, production-ready          |
| **ORM**           | Exposed                  | Ktorm, jOOQ           | Kotlin DSL, type-safe, активная разработка   |
| **Migrations**    | Flyway                   | Liquibase             | Простота, SQL-файлы                          |
| **Config**        | Hoplite                  | Konf, Typesafe Config | Kotlin DSL, env support, profiles            |
| **Logging**       | kotlin-logging + Logback | Log4j2                | SLF4J совместимость, структурные логи        |

### Shared (KMP: jvm + js)

| Компонент         | Выбор                    | Альтернативы          | Обоснование                                       |
|-------------------|--------------------------|-----------------------|---------------------------------------------------|
| **Domain**        | Pure Kotlin (KMP)        | —                     | Чистый Kotlin, без фреймворков                    |
| **Either**        | Arrow                    | kotlin.Result         | KMP, rich API, monad comprehensions               |
| **DateTime**      | kotlinx-datetime         | java.time             | KMP-совместим                                     |
| **UUID**          | kotlin.uuid.Uuid         | java.util.UUID        | KMP-совместим (Kotlin 2.0+)                       |
| **Coroutines**    | kotlinx-coroutines       | —                     | Стандарт для async в Kotlin                       |

### Frontend / UI

| Компонент       | Выбор                 | Альтернативы | Обоснование                                        |
|-----------------|-----------------------|--------------|----------------------------------------------------|
| **UI**          | Compose Multiplatform | React, Vue   | Единый стек Kotlin, type-safe, переиспользование   |
| **features**    | Compose KMP (jvm, js) | —            | UI-компоненты шарятся между shell-приложениями     |
| **tgminiapp**   | JS (Browser)          | Wasm         | Совместимость с Telegram WebApp API                |
| **HTTP Client** | Ktor Client (KMP)     | Fetch API    | KMP, type-safe, переиспользование DTO              |
| **DI (client)** | Koin Compose          | —            | KMP-совместим, интеграция с Compose                |

### Инфраструктура

| Компонент            | Выбор               | Альтернативы    | Обоснование                           |
|----------------------|---------------------|-----------------|---------------------------------------|
| **Video download**   | yt-dlp (subprocess) | youtube-dl, API | Лучшая поддержка, активная разработка |
| **Video processing** | ffmpeg              | —               | Стандарт индустрии                    |
| **Containerization** | Docker              | Podman          | Универсальность                       |

### Тестирование

| Компонент       | Выбор                     | Где          | Обоснование                      |
|-----------------|---------------------------|--------------|----------------------------------|
| **Framework**   | Kotest 6 framework-engine | commonTest   | KMP-совместим (jvm, js, native)  |
| **Assertions**  | Kotest assertions         | commonTest   | KMP-совместим, rich matchers     |
| **JVM runner**  | Kotest runner-junit5      | jvmTest      | IDE integration, BDD-стиль       |
| **Mocking**     | MockK                     | jvmTest only | Kotlin-first, coroutines support |
| **Integration** | Testcontainers            | jvmTest only | Реальная БД, надёжность          |

---

## Последствия

### Положительные

- Единый язык Kotlin на всех платформах (KMP)
- Type-safe API между клиентом и сервером через shared DTO
- UI-компоненты пишутся один раз в `features`
- Sealed classes, coroutines — работают одинаково в commonMain
- Добавление новой UI-платформы = создание тонкого shell-модуля

### Отрицательные

- Compose Multiplatform for Web менее зрелый, чем React
- Меньше готовых UI-компонентов для web
- KMP Gradle setup сложнее, чем чистый JVM
- MockK не работает в commonTest (только jvmTest)
- Kotest JS/Native engine имеет ограничения (нет annotation-based config)

### Риски

- **Compose Web stability**: Следить за релизами, иметь fallback план
- **yt-dlp breaking changes**: Версионировать, тестировать после обновлений
- **KMP bundle size**: JS bundle может быть большим — настроить tree-shaking

---

## Ссылки

- [Ktor Documentation](https://ktor.io/docs/)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Exposed Wiki](https://github.com/JetBrains/Exposed/wiki)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
