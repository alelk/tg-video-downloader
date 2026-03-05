# AGENTS.md — Инструкции для AI-агентов

> **Назначение**: Это главный файл инструкций для AI-агентов, работающих с проектом.
> Люди тоже могут его читать, но основная документация находится в `docs/`.

---

## 🎯 Краткое описание проекта

**TG Video Downloader** — сервис для скачивания видео с различных платформ 
(YouTube, RuTube, VK Video и [1000+ других](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)) 
с управлением через Telegram Mini App. Поддерживает LLM (Gemini/OpenAI) для определения метаданных и HTTP/SOCKS5 прокси.

**Стек**: Kotlin 2.3+ (Multiplatform), Ktor 3, Compose Multiplatform, PostgreSQL, yt-dlp.

---

## 📚 Где искать информацию

| Что нужно                        | Где смотреть                                         |
|----------------------------------|------------------------------------------------------|
| Обзор проекта                    | [`README.md`](./README.md)                           |
| Архитектура, KMP и модули        | [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)     |
| Доменные модели (sealed classes) | [`docs/DOMAIN.md`](./docs/DOMAIN.md)                 |
| HTTP API и DTO                   | [`docs/API_CONTRACT.md`](./docs/API_CONTRACT.md)     |
| База данных                      | [`docs/DATABASE.md`](./docs/DATABASE.md)             |
| Конфигурация                     | [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md)   |
| Безопасность и авторизация       | [`docs/SECURITY.md`](./docs/SECURITY.md)             |
| Тестирование                     | [`docs/TESTING.md`](./docs/TESTING.md)               |
| Деплой                           | [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md)         |
| Архитектурные решения            | [`docs/ADR/`](./docs/ADR/)                           |

---

## 🏗️ Структура модулей

```
tg-video-downloader/
├── domain/              # Доменные модели, use-cases (KMP: jvm, js)
├── api/
│   ├── contract/        # DTO для HTTP API (KMP: jvm, js)
│   ├── mapping/         # Маппинг domain <-> DTO (KMP: jvm, js)
│   ├── client/          # Ktor HTTP-клиент (KMP: jvm, js)
│   └── client/di/       # Koin-модули для API-клиента (KMP: jvm, js)
├── features/            # UI-компоненты, Compose Multiplatform (KMP: jvm, js)
├── tgminiapp/           # Telegram Mini App shell (JS only)
├── server/
│   ├── infra/           # Репозитории, DB, yt-dlp, ffmpeg, LLM (JVM only)
│   ├── transport/       # Ktor routing, auth middleware (JVM only)
│   ├── di/              # Koin модули сервера (JVM only)
│   └── app/             # Entrypoint, Application.kt (JVM only)
└── docs/                # Документация
```

**KMP-правило**: `domain`, `api:*`, `features` — Kotlin Multiplatform (jvm + js). `server:*` — JVM only. `tgminiapp` — JS only.

---

## ⚡ Ключевые принципы

### 1. Kotlin Multiplatform

- Весь переиспользуемый код — в `commonMain` source set
- Platform-specific код — через `expect/actual`
- `java.util.UUID` → `kotlin.uuid.Uuid` (Kotlin 2.0+)
- `java.time.*` → `kotlin.time.Instant` (в stdlib с Kotlin 2.1.20+)
- НЕ использовать JVM-only классы в `commonMain`

### 2. Kotlin-идиоматичность

- **Sealed classes** для полиморфных типов (`RuleMatch`, `ResolvedMetadata`, `MetadataTemplate`, `UserOverrides`, `OutputFormat`, `DomainError`)
- **Value classes** для typesafe ID и value objects (`VideoId`, `RuleId`, `JobId`, `ChannelDirectoryEntryId`, `Tag`, `Url`, `FilePath`, `LocalDate`, `Extractor`)
- **Data classes** для DTO и value objects
- **Either<Error, T>** для обработки ошибок (Arrow)
- **Coroutines** для асинхронности
- **`val` (extension property)** для cheap computed values вместо `fun` без аргументов

### 3. Разделение слоёв

```
UI Shell (tgminiapp) → features (Compose)
                            ↓
              api:client → api:contract
                            ↓
              api:mapping → domain
                            ↓
              server:transport → server:infra (DB, yt-dlp, LLM, Proxy)
```

### 4. Contract-first & Workspace-scoped API

- Сначала DTO в `api:contract`, потом реализация
- Discriminator `type` для sealed DTO в JSON
- Все доменные ресурсы привязаны к workspace: `/api/v1/workspaces/{workspaceId}/...`
- Версионирование API через `/api/v1/`, `/api/v2/`

---

## 📝 Инструкции для реализации

### При создании нового KMP-модуля

1. Добавь в `settings.gradle.kts`
2. Используй `kotlin("multiplatform")` plugin
3. Объяви таргеты: `jvm()`, `js(IR) { browser() }`
4. Весь код — в `commonMain`, platform-specific — через `expect/actual`

### При добавлении нового типа в sealed hierarchy

1. Добавь в domain (`commonMain`)
2. Добавь в DTO (`api:contract`, `commonMain`) с `@SerialName`
3. Добавь маппинг (`api:mapping`, `commonMain`)
4. Добавь тесты (`commonTest`)
5. Обнови UI (`features`)

### При создании нового endpoint

1. Опиши DTO в `api:contract`
2. Добавь route в `server:transport`
3. Реализуй use-case в `domain` (если нужна бизнес-логика)
4. Добавь тесты
5. Обнови [API_CONTRACT.md](./docs/API_CONTRACT.md)

### При работе с ошибками

- В domain: возвращай `Either<DomainError, T>`
- В маппинге: возвращай `Either<ValidationError, T>`
- В transport: маппь `DomainError` → HTTP status + `ApiErrorDto`
- Никогда не используй exceptions для бизнес-ошибок

---

## 🔑 Важные детали реализации

### LlmPort (Optional)

```kotlin
// domain/metadata/LlmPort.kt (commonMain)
interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}
```

Реализации (`GeminiLlmAdapter`, `OpenAiLlmAdapter`) — в `server:infra/llm/`.
Инжектится как nullable (`getOrNull()`). Если LLM не настроен — `null`, fallback на `MetadataResolver`.

### Proxy

`ProxyConfig` используется в:
- `yt-dlp` → аргумент `--proxy`
- LLM HTTP-клиент → `Ktor Client` engine proxy config

### Save as Rule

При создании job (`POST /api/v1/jobs`) можно передать `saveAsRule`, 
чтобы автоматически создать правило для этого канала из текущих метаданных.

### features → tgminiapp

`features` содержит все Compose UI-компоненты. `tgminiapp` — тонкая shell-обёртка, которая:
- Инициализирует DI (Koin)
- Подключает `features` экраны
- Обеспечивает Telegram WebApp JS interop

---

## ✅ Чек-лист перед коммитом

- [ ] Код компилируется на всех таргетах (`./gradlew build`)
- [ ] Тесты проходят (`./gradlew allTests`)
- [ ] Новый код в `commonMain` не использует JVM-only классы
- [ ] Документация обновлена
- [ ] Нет hardcoded secrets
- [ ] Следует принципам из ADR

---

## 🚫 Чего НЕ делать

- ❌ Не добавлять JVM-only зависимости в `commonMain` KMP-модулей
- ❌ Не добавлять Ktor/DB зависимости в `domain`
- ❌ Не использовать exceptions для бизнес-ошибок
- ❌ Не хардкодить пути и конфигурацию
- ❌ Не логировать sensitive данные (botToken, initData)
- ❌ Не создавать циклических зависимостей между модулями
- ❌ Не размещать UI-компоненты в `tgminiapp` — только в `features`

---

## 📎 Быстрые ссылки

- **Gradle команды**:
  - `./gradlew build` — полная сборка всех модулей
  - `./gradlew check` — все тесты (commonTest + jvmTest + jsTest)
  - `./gradlew :server:app:run` — запуск сервера
  - `./gradlew :tgminiapp:jsBrowserDevelopmentRun` — запуск UI

- **Docker**:
  - `docker compose up -d postgres` — только БД
  - `docker compose up -d` — всё

- **Полезные файлы**:
  - `docs/ADR/` — архитектурные решения
  - `gradle/libs.versions.toml` — версии зависимостей
