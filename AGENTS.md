# AGENTS.md — Инструкции для AI-агентов

> **Назначение**: Это главный файл инструкций для AI-агентов, работающих с проектом.
> Люди тоже могут его читать, но основная документация находится в `docs/`.

---

## 🎯 Краткое описание проекта

**TG Video Downloader** — сервис для скачивания YouTube-видео с управлением через Telegram Mini App. Поддерживает умное определение метаданных через LLM (Gemini/OpenAI) и HTTP/SOCKS5 прокси.

**Стек**: Kotlin 2.3+, Ktor 3, Compose Multiplatform, PostgreSQL, yt-dlp.

---

## 📚 Где искать информацию

| Что нужно                        | Где смотреть                                       |
|----------------------------------|----------------------------------------------------|
| Обзор проекта                    | [`README.md`](./README.md)               |
| Архитектура и модули             | [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)   |
| Доменные модели (sealed classes) | [`docs/DOMAIN.md`](./docs/DOMAIN.md)               |
| HTTP API и DTO                   | [`docs/API_CONTRACT.md`](./docs/API_CONTRACT.md)   |
| База данных                      | [`docs/DATABASE.md`](./docs/DATABASE.md)           |
| Конфигурация                     | [`docs/CONFIGURATION.md`](./docs/CONFIGURATION.md) |
| Безопасность и авторизация       | [`docs/SECURITY.md`](./docs/SECURITY.md)           |
| Тестирование                     | [`docs/TESTING.md`](./docs/TESTING.md)             |
| Деплой                           | [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md)       |
| Архитектурные решения            | [`docs/ADR/`](./docs/ADR/)                         |

---

## 🏗️ Структура модулей

```
tg-video-downloader/
├── domain/              # Доменные модели, use-cases (чистый Kotlin, без фреймворков)
├── api/
│   ├── contract/        # DTO для HTTP API (kotlinx.serialization, KMP)
│   ├── mapping/         # Маппинг domain <-> DTO
│   └── client/          # Ktor KMP HTTP-клиент
├── server/
│   ├── infra/           # Репозитории, DB (Exposed), внешние процессы (yt-dlp, ffmpeg), LLM
│   ├── transport/       # Ktor routing, auth middleware
│   ├── di/              # Koin модули
│   └── app/             # Entrypoint, Application.kt
├── tgminiapp/           # Compose Multiplatform Web UI
└── docs/                # Документация
```

**Правило зависимостей**: `domain` → ни от чего; `api:contract` → только kotlinx.serialization; все остальные модули могут зависеть от domain.

---

## ⚡ Ключевые принципы

### 1. Kotlin-идиоматичность

- **Sealed classes** для полиморфных типов (`RuleMatch`, `ResolvedMetadata`, `DomainError`)
- **Value classes** для typesafe ID (`VideoId`, `RuleId`, `JobId`)
- **Data classes** для DTO и value objects
- **Either<Error, T>** для обработки ошибок (Arrow)
- **Coroutines** для асинхронности

### 2. Разделение слоёв

```
UI (tgminiapp) → api:client → api:contract
                                    ↓
            server:transport → api:mapping → domain
                    ↓
            server:infra (DB, yt-dlp, LLM, Proxy)
```

### 3. Contract-first

- Сначала DTO в `api:contract`, потом реализация
- Discriminator `type` для sealed DTO в JSON
- Версионирование API через `/api/v1/`, `/api/v2/`

---

## 📝 Инструкции для реализации

### При создании нового модуля

1. Добавь в `settings.gradle.kts`
2. Создай `build.gradle.kts` с правильными зависимостями
3. Следуй структуре пакетов из [ARCHITECTURE.md](./docs/ARCHITECTURE.md)

### При добавлении нового типа в sealed hierarchy

1. Добавь в domain (`domain/model/`)
2. Добавь в DTO (`api/contract/`) с `@SerialName`
3. Добавь маппинг в обе стороны (`api/mapping/`)
4. Добавь тесты
5. Обнови документацию при необходимости

### При создании нового endpoint

1. Опиши DTO в `api:contract`
2. Добавь route в `server:transport`
3. Реализуй use-case в `domain` (если нужна бизнес-логика)
4. Добавь тесты (unit + integration)
5. Обнови [API_CONTRACT.md](./docs/API_CONTRACT.md)

### При работе с ошибками

- В domain: возвращай `Either<DomainError, T>`
- В маппинге: возвращай `Either<ValidationError, T>`
- В transport: маппь `DomainError` → HTTP status + `ApiErrorDto`
- Никогда не используй exceptions для бизнес-ошибок

---

## 🔑 Важные детали реализации

### Telegram initData

- Проверка в `TelegramAuthPlugin` (server-transport)
- Алгоритм: HMAC-SHA256 с ключом = HMAC("WebAppData", botToken)
- Проверяй `auth_date` на свежесть
- Allowlist пользователей в конфигурации

### RuleMatch sealed hierarchy

```kotlin
sealed interface RuleMatch {
    data class AllOf(val matches: List<RuleMatch>) : RuleMatch  // AND
    data class AnyOf(val matches: List<RuleMatch>) : RuleMatch  // OR
    data class ChannelId(val value: String) : RuleMatch
    data class ChannelName(val value: String) : RuleMatch
    data class TitleRegex(val pattern: String) : RuleMatch
    data class UrlRegex(val pattern: String) : RuleMatch
}
```

### ResolvedMetadata sealed hierarchy

```kotlin
sealed interface ResolvedMetadata {
    data class MusicVideo(val artist: String, val title: String, ...) : ResolvedMetadata
    data class SeriesEpisode(val seriesName: String, val season: String?, ...) : ResolvedMetadata
    data class Other(val title: String, ...) : ResolvedMetadata
}
```

### MetadataSource enum

```kotlin
enum class MetadataSource { RULE, LLM, FALLBACK }
```

Указывает, откуда пришли метаданные: из правила, LLM или базового fallback.

### LlmPort (Optional)

```kotlin
interface LlmPort {
    suspend fun suggestMetadata(video: VideoInfo): Either<DomainError.LlmError, LlmSuggestion>
}
```

`LlmPort` — порт в `domain/port/`. Реализации (`GeminiLlmAdapter`, `OpenAiLlmAdapter`) — в `server:infra/llm/`.
Инжектится как nullable (`getOrNull()`). Если LLM не настроен (`provider=NONE`) — `null`, fallback на базовый `MetadataResolver`.

### Save as Rule

При создании job (`POST /api/v1/jobs`) можно передать `saveAsRule` с настройками, чтобы автоматически создать правило для этого канала из текущих метаданных.

### Proxy

Конфигурация прокси (`ProxyConfig`) используется в двух местах:
- `yt-dlp` — через аргумент `--proxy`
- LLM HTTP-клиент — через `Ktor Client` engine proxy config

### JSON сериализация sealed

Discriminator: `type`

```json
{ "type": "channelId", "value": "UC123" }
{ "type": "musicVideo", "artist": "...", "title": "..." }
```

---

## ✅ Чек-лист перед коммитом

- [ ] Код компилируется без warnings
- [ ] Тесты проходят (`./gradlew test`)
- [ ] Новый код покрыт тестами
- [ ] Документация обновлена (если изменено поведение)
- [ ] Нет hardcoded secrets
- [ ] Следует принципам из ADR

---

## 🚫 Чего НЕ делать

- ❌ Не добавлять зависимости на Ktor/DB в `domain`
- ❌ Не использовать exceptions для бизнес-ошибок
- ❌ Не хардкодить пути и конфигурацию
- ❌ Не логировать sensitive данные (botToken, initData полностью)
- ❌ Не создавать циклических зависимостей между модулями
- ❌ Не использовать `!!` без крайней необходимости

---

## 📎 Быстрые ссылки

- **Gradle команды**:
  - `./gradlew test` — все тесты
  - `./gradlew :server:app:run` — запуск сервера
  - `./gradlew :tgminiapp:jsBrowserDevelopmentRun` — запуск UI

- **Docker**:
  - `docker compose up -d postgres` — только БД
  - `docker compose up -d` — всё

- **Полезные файлы**:
  - `docs/ADR/` — архитектурные решения
  - `gradle/libs.versions.toml` — версии зависимостей
