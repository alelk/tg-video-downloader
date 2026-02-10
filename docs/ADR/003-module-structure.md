# ADR-003: Модульная архитектура

**Статус**: Принято  
**Дата**: 2026-02-11  
**Авторы**: Team

---

## Контекст

Проект включает:
- Backend (Ktor)
- Telegram Mini App (Compose Multiplatform)
- Shared DTO и domain модели

Нужно определить структуру Gradle-модулей для:
- Чёткого разделения ответственности
- Возможности переиспользования кода
- Независимой разработки и тестирования
- Контроля зависимостей

---

## Решение

### Структура модулей

```
tg-video-downloader/
├── domain/              # Чистая бизнес-логика
├── api-contract/        # DTO для API (shared)
├── api-mapping/         # Маппинг domain <-> DTO
├── api-client/          # HTTP клиент (KMP)
├── server-infra/        # Репозитории, внешние процессы
├── server-transport/    # Ktor routes, middleware
├── server-di/           # Koin модули
├── server-app/          # Entrypoint
└── tgminiapp/           # UI (Compose Multiplatform)
```

### Граф зависимостей

```
                    tgminiapp
                        │
                        ▼
                   api-client
                        │
            ┌───────────┴───────────┐
            │                       │
            ▼                       │
      api-contract                  │
            │                       │
            │           ┌───────────┘
            ▼           ▼
      api-mapping ──▶ domain
            │
            ▼
    server-transport
            │
            ▼
     server-infra
            │
            ▼
      server-di
            │
            ▼
      server-app
```

### Правила зависимостей

| Модуль             | Может зависеть от                               | НЕ может зависеть от        |
|--------------------|-------------------------------------------------|-----------------------------|
| `domain`           | —                                               | Всё остальное               |
| `api-contract`     | Kotlin stdlib, kotlinx.serialization            | domain, server-*            |
| `api-mapping`      | domain, api-contract                            | server-*, api-client        |
| `api-client`       | api-contract, Ktor Client                       | domain, server-*            |
| `server-infra`     | domain, api-contract                            | transport, di, app          |
| `server-transport` | domain, api-contract, api-mapping, server-infra | di, app                     |
| `server-di`        | Все серверные модули                            | app                         |
| `server-app`       | Все серверные модули                            | —                           |
| `tgminiapp`        | api-client, api-contract                        | server-*, domain (напрямую) |

---

## Обоснование

### Почему отдельный `domain`?

- **Независимость от фреймворков**: можно тестировать без Ktor/DB
- **Чистые типы**: sealed classes, value objects без аннотаций сериализации
- **Переиспользование**: потенциально между проектами

### Почему `api-contract` отдельно от `domain`?

- **Стабильность API**: контракт версионируется отдельно
- **Разные инварианты**: DTO может иметь nullable там, где domain требует non-null
- **Разные аннотации**: @Serializable, @SerialName

### Почему `api-mapping` отдельно?

- **Изоляция логики маппинга**: не загрязняет domain и DTO
- **Тестируемость**: легко тестировать преобразования отдельно
- **Валидация**: DTO → Domain может возвращать Either

### Почему `api-client` KMP?

- **Единый клиент**: используется в tgminiapp (JS) и потенциально в тестах (JVM)
- **Type-safe**: работает с теми же DTO, что и сервер
- **Ktor Client**: нативная поддержка всех платформ

### Почему разделение server-*?

| Модуль | Ответственность |
|--------|-----------------|
| `server-infra` | "Грязная" работа: DB, процессы, FS |
| `server-transport` | HTTP: роуты, middleware, валидация |
| `server-di` | Wiring зависимостей |
| `server-app` | Точка входа, конфигурация |

Это позволяет:
- Тестировать infra без HTTP
- Тестировать transport без реальной infra (моки)
- Менять DI-фреймворк в одном месте

---

## Альтернативы

### 1. Монолитный модуль

```
server/  # всё в одном
```

**Минусы**: Сложно тестировать, нет контроля зависимостей, долгая компиляция.

### 2. Слишком мелкое деление

```
domain-models/
domain-services/
domain-usecases/
domain-errors/
...
```

**Минусы**: Overhead, сложная навигация, циклические зависимости.

### 3. Feature-based модули

```
feature-preview/
feature-jobs/
feature-rules/
```

**Минусы**: Дублирование общего кода, сложнее для маленького проекта.

---

## Последствия

### Положительные

- Чёткие границы ответственности
- Контроль зависимостей на уровне Gradle
- Параллельная компиляция модулей
- Легко тестировать изолированно

### Отрицательные

- Начальный overhead на настройку
- Нужно следить за правилами зависимостей
- Больше файлов build.gradle.kts

### Миграция

При росте проекта можно:
- Выделить `core/` для общих утилит
- Разбить `server-infra` на `infra-db`, `infra-process`
- Добавить feature-модули для крупных фич

---

## Конфигурация Gradle

### settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

include(
    ":domain",
    ":api-contract",
    ":api-mapping",
    ":api-client",
    ":server-infra",
    ":server-transport",
    ":server-di",
    ":server-app",
    ":tgminiapp",
)
```

### Пример domain/build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    // Только Kotlin stdlib
    implementation(kotlin("stdlib"))
    
    // Опционально: Arrow для Either
    implementation(libs.arrow.core)
    
    // Тесты
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
}
```

### Пример api-contract/build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
```

---

## Ссылки

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Gradle Multi-project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)

