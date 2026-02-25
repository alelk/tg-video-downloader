# ADR-003: Модульная архитектура

**Статус**: Принято  
**Дата**: 2026-02-25  
**Авторы**: Alex Elkin

---

## Контекст

Проект включает:
- Backend (Ktor, JVM)
- Telegram Mini App (Compose Multiplatform, JS)
- Shared domain модели и DTO (KMP)
- Потенциально: desktop, Android, web-приложения

Нужно определить структуру Gradle-модулей для:
- Чёткого разделения ответственности
- Переиспользования кода через KMP
- Независимой разработки и тестирования
- Контроля зависимостей

---

## Решение

### Структура модулей

```
tg-video-downloader/
├── domain/              # Чистая бизнес-логика (KMP: jvm, js)
├── api/
│   ├── contract/        # DTO для API (KMP: jvm, js)
│   ├── mapping/         # Маппинг domain <-> DTO (KMP: jvm, js)
│   ├── client/          # HTTP клиент (KMP: jvm, js)
│   └── client/di/       # DI wiring клиента (KMP: jvm, js)
├── features/            # UI-компоненты, Compose Multiplatform (KMP: jvm, js)
├── tgminiapp/           # Telegram Mini App shell (JS only)
├── server/
│   ├── infra/           # Репозитории, внешние процессы, LLM (JVM only)
│   ├── transport/       # Ktor routes, middleware (JVM only)
│   ├── di/              # Koin модули (JVM only)
│   └── app/             # Entrypoint (JVM only)
└── docs/                # Документация
```

### Граф зависимостей

```
                    tgminiapp (JS)
                        │
                        ▼
                    features (KMP)
                        │
            ┌───────────┼────────────┐
            │           │            │
            ▼           ▼            ▼
      api:client     domain    api:mapping
            │                       │
      api:client:di          api:contract
                        │
          ┌─────────────┼─────────────┐
          │             │             │
          ▼             ▼             ▼
  server:transport  server:infra  server:di
          │             │             │
          └─────────────┼─────────────┘
                        │
                   server:app
```

### Правила зависимостей

| Модуль             | Может зависеть от                                      | НЕ может зависеть от           |
|--------------------|--------------------------------------------------------|--------------------------------|
| `domain`           | Kotlin stdlib, Arrow, kotlinx-datetime                 | Всё остальное                  |
| `api:contract`     | Kotlin stdlib, kotlinx.serialization                   | domain, server:*, features     |
| `api:mapping`      | domain, api:contract, Arrow                            | server:*, api:client, features |
| `api:client`       | api:contract, Ktor Client                              | domain, server:*, features     |
| `api:client:di`    | api:client, Koin, Ktor engine                          | domain, server:*, features     |
| `features`         | domain, api:client, api:mapping, Compose, Koin         | server:*, tgminiapp            |
| `tgminiapp`        | features, api:client:di                                | server:*, domain напрямую      |
| `server:infra`     | domain, api:contract                                   | transport, di, app, features   |
| `server:transport` | domain, api:contract, api:mapping, Ktor Server         | di, app, features              |
| `server:di`        | domain, server:infra, server:transport, Koin           | app, features                  |
| `server:app`       | Все серверные модули                                   | features, tgminiapp            |

---

## Обоснование

### Почему отдельный `domain` (KMP)?

- **Независимость от фреймворков**: чистый Kotlin, можно тестировать без Ktor/DB
- **Переиспользование**: одни и те же модели на сервере и клиенте
- **KMP**: sealed classes, enums — работают одинаково на JVM и JS

### Почему `api:contract` отдельно от `domain`?

- **Стабильность API**: контракт версионируется отдельно
- **Разные инварианты**: DTO может иметь nullable там, где domain требует non-null
- **Разные аннотации**: `@Serializable`, `@SerialName`

### Почему `api:mapping` — KMP?

- Маппинг нужен на сервере (`server:transport`) и на клиенте (`features`)
- Позволяет `features` работать с доменными моделями, а не DTO

### Почему `api:client:di` — отдельный модуль?

- Platform-specific engine selection (CIO для JVM, Js для браузера)
- `api:client` остаётся чистым — без знания о конкретном engine
- Koin wiring изолирован

### Почему `features` — отдельный KMP-модуль?

- Все Compose UI-компоненты переиспользуются между shell-приложениями
- `tgminiapp` — тонкая обёртка с Telegram-специфичным glue-кодом
- Добавление новой платформы = новый shell-модуль, зависящий от `features`

### Почему разделение `server:*`?

| Модуль             | Ответственность                               |
|--------------------|-----------------------------------------------|
| `server:infra`     | "Грязная" работа: DB, процессы, FS, LLM       |
| `server:transport` | HTTP: роуты, middleware, валидация             |
| `server:di`        | Wiring зависимостей                           |
| `server:app`       | Точка входа, конфигурация                     |

---

## Конфигурация Gradle

### settings.gradle.kts

```kotlin
rootProject.name = "tg-video-downloader"

// === Domain (KMP) ===
include(":domain")

// === API (KMP) ===
include(":api:contract")
include(":api:mapping")
include(":api:client")
include(":api:client:di")

// === Server (JVM only) ===
include(":server:infra")
include(":server:transport")
include(":server:di")
include(":server:app")

// === UI (KMP) ===
include(":features")
include(":tgminiapp")
```

### Пример domain/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.arrow.core)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions)
        }
        jvmTest.dependencies {
            implementation(libs.kotest.runner.junit5)
        }
    }
}
```

### Пример features/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(projects.domain)
            implementation(projects.api.client)
            implementation(projects.api.mapping)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
    }
}
```

### Пример server:infra/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(projects.domain)
    implementation(projects.api.contract)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
}
```

---

## Последствия

### Положительные

- Чёткие границы ответственности
- Контроль зависимостей на уровне Gradle
- Переиспользование кода между платформами (KMP)
- UI-компоненты пишутся один раз (`features`)
- Легко тестировать изолированно

### Отрицательные

- Начальный overhead на настройку KMP
- Больше файлов build.gradle.kts
- Не все тестовые инструменты поддерживают KMP (MockK → только jvmTest)

### Миграция

При росте проекта можно:
- Выделить `core/` для общих утилит
- Разбить `features` на feature-модули (`features:preview`, `features:jobs`)
- Добавить таргеты (iOS, macOS, Android) в KMP-модули

---

## Ссылки

- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Gradle Multi-project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [KMP Project Structure](https://kotlinlang.org/docs/multiplatform-discover-project.html)
