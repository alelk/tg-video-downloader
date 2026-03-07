# TG Video Downloader — Документация

> **Версия**: 0.1.0-SNAPSHOT  
> **Статус**: В разработке (MVP)  
> **Последнее обновление**: 2026-02-25

---

## 📋 О проекте

**TG Video Downloader** — сервис для скачивания видео с различных платформ 
(YouTube, RuTube, VK Video, и [1000+ других](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)) 
с управлением через Telegram Mini App. 
Поддерживает умное определение метаданных через LLM (Gemini/OpenAI) и HTTP/SOCKS5 прокси.

### Ключевые возможности

- 🎬 Скачивание видео через `yt-dlp` (YouTube, RuTube, VK Video, 1000+ сайтов)
- 🏷️ Автоматическое распознавание метаданных (исполнитель, название, сезон/серия)
- 🧠 Умное определение метаданных через LLM (Gemini/OpenAI) для новых каналов
- 📁 Умная раскладка по папкам на основе правил
- 📺 Справочник каналов — теги, per-channel metadata overrides, тег-матчинг в правилах
- ✏️ Редактирование метаданных перед скачиванием
- 💾 Сохранение настроек как правило для будущих видео
- 🔄 Очередь задач с прогрессом и повторами
- 🌐 Поддержка HTTP/SOCKS5 прокси
- 🔐 Авторизация через Telegram initData
- 📱 Kotlin Multiplatform — единая кодовая база для сервера и клиентов

---

## 🗂️ Структура документации

| Документ                                  | Описание                                                  |
|-------------------------------------------|-----------------------------------------------------------|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md)   | Модульная архитектура, KMP-стратегия, зависимости         |
| [DOMAIN.md](docs/DOMAIN.md)               | Доменная модель: sealed-классы, value objects, инварианты |
| [API_CONTRACT.md](docs/API_CONTRACT.md)   | HTTP API: эндпоинты, DTO, сериализация, ошибки            |
| [DATABASE.md](docs/DATABASE.md)           | Схема PostgreSQL, миграции, индексы                       |
| [CONFIGURATION.md](docs/CONFIGURATION.md) | Параметры конфигурации                                    |
| [SECURITY.md](docs/SECURITY.md)           | Авторизация, Telegram initData, безопасность              |
| [TESTING.md](docs/TESTING.md)             | Стратегия тестирования, KMP-тесты, примеры                |
| [DEPLOYMENT.md](docs/DEPLOYMENT.md)       | Docker, docker-compose, CI/CD                             |
| [MAINTENANCE.md](docs/MAINTENANCE.md)     | Обслуживание, обновление yt-dlp                           |
| [ADR/](docs/ADR/)                         | Architecture Decision Records                             |

---

## 🛠️ Технологический стек

| Область           | Технология                                 |
|-------------------|--------------------------------------------|
| Язык              | Kotlin 2.3+ (Multiplatform)                |
| JVM               | 21 LTS                                     |
| Backend framework | Ktor 3.x                                   |
| DI                | Koin 4.x                                   |
| Сериализация      | kotlinx.serialization                      |
| База данных       | PostgreSQL 16+                             |
| ORM / SQL         | Exposed + exposed-json                    |
| Миграции          | Flyway                                     |
| UI                | Compose Multiplatform                      |
| HTTP Client       | Ktor Client (KMP)                          |
| Внешние процессы  | yt-dlp, ffmpeg                             |
| Конфигурация      | Hoplite                                    |
| Логирование       | kotlin-logging + Logback                   |
| Тесты             | Kotest 6, MockK, Testcontainers            |

---

## 📦 Модули проекта

```
tg-video-downloader/
├── domain/              # Доменные модели, use-cases (KMP: jvm, js)
├── api/
│   ├── contract/        # DTO, API-контракт (KMP: jvm, js)
│   ├── mapping/         # Маппинг domain <-> DTO (KMP: jvm, js)
│   ├── client/          # Ktor HTTP-клиент (KMP: jvm, js)
│   └── client/di/       # Koin-модули для API-клиента (KMP: jvm, js)
├── features/            # UI-компоненты, Compose Multiplatform (KMP: jvm, js)
├── tgminiapp/           # Telegram Mini App shell (JS only)
├── server/
│   ├── infra/           # Репозитории, DB, yt-dlp, LLM (JVM only)
│   ├── transport/       # Ktor routing, auth (JVM only)
│   ├── di/              # Koin модули сервера (JVM only)
│   └── app/             # Entrypoint (JVM only)
└── docs/                # Документация
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

# 2. Запустить сервер
./gradlew :server:app:run

# 3. Запустить Telegram Mini App (dev server)
./gradlew :tgminiapp:jsBrowserDevelopmentRun
```

### Локальная проверка Mini App в Telegram (без HMR)

Если WebView в Telegram (особенно iOS) подвисает на dev-server, используйте production bundle:

```bash
# 1. Собрать production distribution Mini App
./gradlew :tgminiapp:jsBrowserDistribution

# 2. Отдавать статические файлы
npx serve tgminiapp/build/dist/js/productionExecutable -l 8081
```

Важно:
- Если на `http://localhost:8081/` приходит `404`, обычно указан не тот каталог для `serve`.
- Перед запуском убедитесь, что заполнен `tgminiapp/src/jsMain/resources/config.js` (например, `API_BASE_URL`).

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
   - Извлекает videoId и получает метаданные через yt-dlp
   - Находит правило по каналу "Rick Astley" → category=MUSIC_VIDEO
   - Распознаёт: artist="Rick Astley", title="Never Gonna Give You Up"
4. Пользователь видит preview с планом сохранения:
   - Оригинал: /media/Music Videos/original/Rick Astley/Never Gonna Give You Up [dQw4w9WgXcQ].webm
   - Конвертированный: /media/Music Videos/converted/Rick Astley/Never Gonna Give You Up.mp4
5. Пользователь может отредактировать метаданные и нажимает "Скачать"
6. Job выполняется:
   a. Скачивание оригинала в максимальном качестве → original/
   b. Конвертация в mp4 (формат задаётся в конфигурации) → converted/
   c. Вшивание метаданных и обложки в оба файла
7. Пользователь видит статус DONE
```

### Сценарий 2: Умное определение (LLM)

```
1. Ссылка на видео неизвестного канала
2. Правил нет. Включена интеграция с Gemini.
3. LLM определяет категорию, исполнителя/название.
4. Пользователь видит предложенные данные.
5. Может поставить галочку "Сохранить как правило" для этого канала.
```

### Сценарий 3: Bot -> Mini App кнопка (автоподстановка URL)

Теперь сервер может поднимать lightweight long-polling бота, который:
- получает сообщение с ссылкой;
- отправляет inline-кнопку `Open Mini App`;
- открывает Mini App через `startapp`, и поле URL в UI уже заполнено.

Минимальная конфигурация:

```yaml
telegram:
  botToken: "${TELEGRAM_BOT_TOKEN}"
  miniAppAutoReply:
    enabled: true
    botUsername: "your_bot_username"
    miniAppShortName: "miniapp"
    buttonText: "Open Mini App"
    replyText: "Got your link. Open Mini App to continue."
    onlyYoutubeLinks: false
```

Формат deep-link, который отправляет бот:

```text
https://t.me/<bot_username>/<mini_app_short_name>?startapp=<base64url(video_url)>
```

`tgminiapp` автоматически читает `start_param` / `tgWebAppStartParam` и подставляет ссылку в `Video URL`.

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

### Глоссарий

| Термин               | Описание                                                                         |
|----------------------|----------------------------------------------------------------------------------|
| **VideoId**          | Идентификатор видео на платформе-источнике (например, `dQw4w9WgXcQ` для YouTube) |
| **Workspace**        | Группа пользователей с общими ресурсами (правила, задачи, настройки)             |
| **Rule**             | Правило обработки: матчинг канала/URL/тега → категория, шаблоны путей             |
| **Category**         | Тип контента: `MUSIC_VIDEO`, `SERIES`, `OTHER`                                   |
| **Channel**          | Запись в справочнике каналов: платформа, теги, metadata overrides                 |
| **Tag**              | Тег для группировки каналов (lowercase, hyphens): `music-video`, `lofi`, `series` |
| **ResolvedMetadata** | Распознанные метаданные для редактирования                                       |
| **Job**              | Задача скачивания с прогрессом и статусом                                        |
| **StoragePlan**      | Итоговые пути файлов после подстановки шаблонов                                  |
| **initData**         | Строка авторизации Telegram Mini App                                             |
| **KMP**              | Kotlin Multiplatform — единая кодовая база для разных платформ                   |

### FAQ

**Q: Почему Kotlin Multiplatform?**  
A: Единый стек Kotlin везде. Domain и UI-компоненты шарятся между сервером (JVM) и клиентами (JS, будущие нативные).

**Q: Почему Compose Multiplatform, а не React?**  
A: Type-safe UI на том же языке, переиспользование компонентов между платформами.

**Q: Как добавить новый UI (desktop, Android)?**  
A: Создать shell-модуль, подключить `features` + `api:client:di`. Все экраны уже готовы.

**Q: Почему yt-dlp как внешний процесс?**  
A: Наиболее актуальная поддержка форматов и сайтов, простота обновления.

---

## Contributing

1. Читай документацию перед реализацией
2. Следуй принципам из [ARCHITECTURE.md](docs/ARCHITECTURE.md)
3. Пиши тесты согласно [TESTING.md](docs/TESTING.md)
4. При изменении поведения — обновляй документацию
