# TG Video Downloader — Документация

> **Версия**: 0.1.0-SNAPSHOT  
> **Статус**: В разработке (MVP)  
> **Последнее обновление**: 2026-02-25

---

## 📋 О проекте

**TG Video Downloader** — сервис для скачивания YouTube-видео с управлением через Telegram Mini App.

### Ключевые возможности

- 🎬 Скачивание видео через `yt-dlp`
- 🏷️ Автоматическое распознавание метаданных (исполнитель, название, сезон/серия)
- 🧠 Умное определение метаданных через LLM (Gemini/OpenAI) для новых каналов
- 📁 Умная раскладка по папкам на основе правил
- ✏️ Редактирование метаданных перед скачиванием
- 💾 Сохранение настроек как правило для будущих видео
- 🔄 Очередь задач с прогрессом и повторами
- 🌐 Поддержка HTTP/SOCKS5 прокси
- 🔐 Авторизация через Telegram initData

---

## 🗂️ Структура документации

| Документ                                  | Описание                                                  |
|-------------------------------------------|-----------------------------------------------------------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md)   | Модульная архитектура, зависимости, принципы              |
| [DOMAIN.md](docs/DOMAIN.md)               | Доменная модель: sealed-классы, value objects, инварианты |
| [API_CONTRACT.md](docs/API_CONTRACT.md)   | HTTP API: эндпоинты, DTO, сериализация, ошибки            |
| [DATABASE.md](docs/DATABASE.md)           | Схема PostgreSQL, миграции, индексы                       |
| [CONFIGURATION.md](docs/CONFIGURATION.md) | Параметры конфигурации                                    |
| [SECURITY.md](docs/SECURITY.md)           | Авторизация, Telegram initData, безопасность              |
| [TESTING.md](docs/TESTING.md)             | Стратегия тестирования, примеры                           |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md)       | Docker, docker-compose, CI/CD                             |
| [MAINTENANCE.md](docs/MAINTENANCE.md)     | Обслуживание, обновление yt-dlp                           |
| [ADR/](docs/ADR/)                         | Architecture Decision Records                             |

---

## 🛠️ Технологический стек

| Область                | Технология                    |
|------------------------|-------------------------------|
| Язык                   | Kotlin 2.3+                   |
| JVM                    | 21 LTS                        |
| Backend framework      | Ktor 3.x                      |
| DI                     | Koin 4.x                      |
| Сериализация           | kotlinx.serialization         |
| База данных            | PostgreSQL 16+                |
| ORM / SQL              | Exposed                       |
| Миграции               | Flyway                        |
| UI (Telegram Mini App) | Compose Multiplatform for Web |
| HTTP Client            | Ktor Client (KMP)             |
| Внешние процессы       | yt-dlp, ffmpeg                |
| Конфигурация           | Hoplite                       |
| Логирование            | kotlin-logging + Logback      |
| Тесты                  | Kotest, MockK, Testcontainers |

---

## 📦 Модули проекта

```
tg-video-downloader/
├── domain/              # Доменные модели, use-cases (чистый Kotlin)
├── api/
│   ├── contract/        # DTO, API-контракт (kotlinx.serialization, KMP)
│   ├── mapping/         # Маппинг domain <-> DTO
│   └── client/          # Ktor KMP HTTP-клиент
├── server/
│   ├── infra/           # Репозитории, DB, внешние процессы, LLM
│   ├── transport/       # Ktor routing, auth middleware
│   ├── di/              # Koin модули
│   └── app/             # Entrypoint, Application.kt
├── tgminiapp/           # Compose Multiplatform Web UI
└── docs/                # Эта документация
```

Подробнее: [ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## 🚀 Быстрый старт

### Требования

- JDK 21+
- Docker & Docker Compose
- yt-dlp (в PATH или указать путь)
- ffmpeg (в PATH или указать путь)

### Локальный запуск

```bash
# 1. Запустить PostgreSQL
docker compose up -d postgres

# 2. Применить миграции и запустить сервер
./gradlew :server:app:run

# 3. Запустить Mini App (dev server)
./gradlew :tgminiapp:jsBrowserDevelopmentRun
```

### Конфигурация

Создать `application-local.yaml`:

```yaml
telegram:
  botToken: "YOUR_BOT_TOKEN"
  allowedUserIds:
    - "123456789"
  devMode: true

db:
  url: "jdbc:postgresql://localhost:5432/tgvd"
  user: "tgvd"
  password: "secret"

storage:
  baseDirectories:
    - "/Users/you/Downloads/videos"
```

---

## 📐 Основные сценарии

### Сценарий 1: Скачивание музыкального видео

```
1. Пользователь открывает Mini App в Telegram
2. Вставляет ссылку: https://youtube.com/watch?v=dQw4w9WgXcQ
3. Сервис:
   - Извлекает videoId
   - Получает метаданные через yt-dlp
   - Находит правило по каналу "Rick Astley" → category=MUSIC_VIDEO
   - Распознаёт: artist="Rick Astley", title="Never Gonna Give You Up"
4. Пользователь видит preview, может отредактировать
5. Нажимает "Скачать"
6. Job создаётся, видео скачивается в:
   /media/Music Videos/Rick Astley/Never Gonna Give You Up.mp4
7. Пользователь видит статус DONE
```

### Сценарий 2: Скачивание эпизода сериала

```
1. Ссылка на видео канала "Kurzgesagt"
2. Правило: channel="Kurzgesagt" → category=SERIES
3. Метаданные: seriesName="Kurzgesagt", season="2024", episode="01", title="..."
4. Файл: /media/TV/Kurzgesagt/Season 2024/01 - Title.mp4
```

### Сценарий 3: Умное определение (LLM)

```
1. Ссылка на видео неизвестного канала "Cooking with Chef"
2. Правил нет. Включена интеграция с Gemini.
3. Сервис отправляет заголовок и описание в LLM.
4. LLM определяет:
   - Category: EDUCATIONAL
   - Series: "Cooking Basics"
   - Title: "How to chop onions"
5. Пользователь видит предложенные данные.
6. Может поставить галочку "Сохранить как правило" для этого канала.
```

---

## ✅ Definition of Done (MVP)

- [ ] Preview: ссылка → метаданные + план сохранения
- [ ] Create job: подтверждение/редактирование → очередь
- [ ] Job execution: скачивание → пост-обработка → финальный путь
- [ ] Progress: статусы отображаются (polling)
- [ ] Rules CRUD: создание/редактирование правил
- [ ] Auth: Telegram initData + allowlist
- [ ] Tests: unit + integration для критичных частей

---

## 📚 Дополнительно

- [Glossary](#глоссарий)
- [FAQ](#faq)
- [Contributing](#contributing)

---

## Глоссарий

| Термин               | Описание                                                         |
|----------------------|------------------------------------------------------------------|
| **VideoId**          | Идентификатор видео YouTube (`dQw4w9WgXcQ`)                      |
| **Rule**             | Правило обработки: матчинг канала/URL → категория, шаблоны путей |
| **Category**         | Тип контента: `MUSIC_VIDEO`, `SERIES`, `OTHER`                   |
| **ResolvedMetadata** | Распознанные метаданные для редактирования                       |
| **Job**              | Задача скачивания с прогрессом и статусом                        |
| **StoragePlan**      | Итоговые пути файлов после подстановки шаблонов                  |
| **initData**         | Строка авторизации Telegram Mini App                             |

---

## FAQ

**Q: Почему Compose Multiplatform для Web, а не React?**  
A: Единый стек Kotlin, переиспользование кода между платформами, type-safe API client.

**Q: Почему yt-dlp как внешний процесс?**  
A: Наиболее актуальная поддержка форматов и сайтов, простота обновления.

**Q: Можно ли добавить другие источники (не YouTube)?**  
A: Да, yt-dlp поддерживает множество сайтов. Поле `extractor` в `VideoSource` это предусматривает.

---

## Contributing

1. Читай документацию перед реализацией
2. Следуй принципам из [ARCHITECTURE.md](docs/ARCHITECTURE.md)
3. Пиши тесты согласно [TESTING.md](docs/TESTING.md)
4. При изменении поведения — обновляй документацию

