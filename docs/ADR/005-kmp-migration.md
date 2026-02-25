# ADR-005: Kotlin Multiplatform

**Статус**: Принято  
**Дата**: 2026-02-25  
**Авторы**: Alex Elkin

---

## Контекст

Проект включает:
- Backend-сервер (JVM)
- Telegram Mini App (JS, Browser)
- Потенциально: desktop (macOS/Windows), Android, web без Telegram

Доменные модели, DTO, маппинг и UI-компоненты можно переиспользовать между платформами вместо дублирования.

Требования:
- Единая доменная модель для сервера и клиентов
- Type-safe API клиент для всех платформ
- Переиспользуемые UI-компоненты (Compose Multiplatform)
- Возможность добавить новую платформу минимальными усилиями

---

## Решение

Перевести `domain`, `api:*` и `features` на **Kotlin Multiplatform (KMP)**, оставив `server:*` на JVM only.

### Классификация модулей

| Модуль            | Plugin           | Targets    | Обоснование                                     |
|-------------------|------------------|------------|-------------------------------------------------|
| `domain`          | `multiplatform`  | jvm, js    | Чистый Kotlin, шарится везде                    |
| `api:contract`    | `multiplatform`  | jvm, js    | DTO с kotlinx.serialization (KMP)               |
| `api:mapping`     | `multiplatform`  | jvm, js    | Нужен на сервере и в features                   |
| `api:client`      | `multiplatform`  | jvm, js    | Ktor Client (KMP-native)                        |
| `api:client:di`   | `multiplatform`  | jvm, js    | Platform-specific engine selection               |
| `features`        | `multiplatform`  | jvm, js    | Compose Multiplatform UI                        |
| `tgminiapp`       | `multiplatform`  | js only    | Telegram shell, только браузер                  |
| `server:*`        | `jvm`            | jvm only   | Exposed, Flyway, yt-dlp — JVM-only библиотеки  |

### KMP-совместимость ключевых зависимостей

| Зависимость           | KMP? | Замечания                               |
|-----------------------|------|-----------------------------------------|
| Arrow (Either)        | ✅    | Полная KMP-поддержка                    |
| kotlinx.serialization | ✅    | Полная KMP-поддержка                    |
| kotlinx-datetime      | ✅    | Замена `java.time.*`                    |
| kotlinx-coroutines    | ✅    | Полная KMP-поддержка                    |
| Ktor Client           | ✅    | CIO (JVM), Js (browser)                 |
| Koin                  | ✅    | koin-core — KMP, koin-compose — KMP     |
| Compose Multiplatform | ✅    | JVM (Desktop) + JS (Browser)            |
| `kotlin.uuid.Uuid`    | ✅    | Kotlin 2.0+, замена `java.util.UUID`    |
| `kotlin.text.Regex`   | ✅    | Уже KMP-совместим                       |
| Exposed               | ❌    | JVM only — используется только в server |
| Ktor Server           | ❌    | JVM only — используется только в server |
| Flyway                | ❌    | JVM only — используется только в server |

### Архитектурные решения для KMP

1. **UUID**: `kotlin.uuid.Uuid` вместо `java.util.UUID` в domain и API
2. **Время**: `kotlinx.datetime.Instant`, `LocalDate` вместо `java.time.*`
3. **Value classes**: Поддерживаются на JS с Kotlin 2.1+. Используем `value class` в `commonMain`
4. **Path**: В domain используем `String` для путей. `java.nio.file.Path` — только в `server:infra` (JVM)
5. **Logging**: `expect/actual` для логирования или KMP logging библиотека

---

## Альтернативы

### 1. Всё на JVM, UI на React

**Плюсы**: Проще setup, большая экосистема React.

**Минусы**: Два языка (Kotlin + TypeScript), дублирование DTO, нет type-safety между клиентом и сервером.

### 2. KMP только для domain и api:contract

**Плюсы**: Меньше KMP-поверхности, проще.

**Минусы**: UI-компоненты не шарятся, `api:mapping` дублируется.

### 3. Kotlin/Wasm вместо Kotlin/JS

**Плюсы**: Потенциально лучшая производительность.

**Минусы**: Менее зрелый, ограниченная совместимость с JS-библиотеками (Telegram WebApp API).

---

## Последствия

### Положительные

- Единая доменная модель и DTO на всех платформах
- Type-safe API клиент для каждой платформы
- UI-компоненты пишутся один раз в `features`
- Добавление новой платформы = создание тонкого shell-модуля
- Ошибки типизации ловятся на этапе компиляции

### Отрицательные

- Дополнительная сложность настройки Gradle (KMP boilerplate)
- Не все библиотеки поддерживают KMP
- `commonTest` не поддерживает MockK → ручные fake-реализации
- Compose Multiplatform for Web менее зрелый, чем React
- Больше время первичной сборки

### Риски

- **Compose Web stability**: Следить за релизами JetBrains, иметь fallback
- **Value class JS support**: В Kotlin 2.1+ стабильно, но следить за edge cases
- **Bundle size**: JS bundle от KMP может быть большим → настроить tree-shaking

---

## Тестирование в KMP

| Source set   | Фреймворк                                     | Что тестировать                   |
|--------------|-----------------------------------------------|-----------------------------------|
| `commonTest` | Kotest framework-engine + assertions          | Domain логика, маппинг, use-cases |
| `jvmTest`    | Kotest runner-junit5 + MockK + Testcontainers | Интеграционные тесты, DB          |
| `jsTest`     | Kotest framework-engine                       | JS-специфичные edge cases         |

> MockK не поддерживает JS. Для мокирования в `commonTest` — создавать fake-реализации интерфейсов.
> Kotest Gradle plugin + KSP обязательны для JS/Native тестов.

---

## Ссылки

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Ktor Client KMP](https://ktor.io/docs/client-create-multiplatform-application.html)
- [Arrow KMP](https://arrow-kt.io/docs/quickstart/)
- [kotlin.uuid.Uuid](https://kotlinlang.org/api/core/kotlin/-uuid/)

