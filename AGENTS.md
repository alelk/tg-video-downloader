# Документация для Разработки Приложения: YouTube Video Downloader Service

> **Назначение документа**: это единый «источник правды» для людей и агентов (автогенераторов кода). По нему будет выполняться реализация.
>
> **Статус**: Draft (рабочая спецификация). Любые изменения поведения сервиса должны сопровождаться правками этого файла.

---

## 0. Коротко о продукте

Сервис принимает YouTube-ссылку (или видео-id), определяет канал/тип контента, подбирает правило (rule), 
предлагает пользователю подтверждение/редактирование метаданных и запускает загрузку через `yt-dlp` 
с последующей пост-обработкой (конвертация/тегирование/раскладка по папкам). 
Управление — через Telegram WebApp (Mini App). 
Доступ защищён проверкой `initData`.

Ключевые свойства:
- **Правила на уровне канала** (или другого матчинга) определяют категорию, шаблоны путей, параметры качества, поведение пост-обработки.
- **Предпросмотр метаданных** перед скачиванием: пользователь подтверждает/правит поля.
- **Очередь задач** с прогрессом и повторными попытками.
- **Надёжность и наблюдаемость**: статусы, логи, понятные ошибки, идемпотентность.

---

## 1. Термины и глоссарий

- **Link / URL** — ссылка на YouTube видео/playlist/shorts.
- **VideoId** — идентификатор видео YouTube (`dQw4w9WgXcQ`).
- **Rule (правило)** — запись, описывающая как обрабатывать контент (категория, шаблоны путей, качество, конвертация, теги).
- **Category (категория)** — тип контента (например `MUSIC_VIDEO`, `SERIES`, `OTHER`). Категория влияет на распознавание метаданных и шаблоны.
- **Resolved metadata (распознанные метаданные)** — набор полей, полученный из YouTube + эвристик + правил, который пользователь может отредактировать.
- **Job / Download (задача)** — конкретная операция скачивания видео и пост-обработки.
- **Storage plan** — результат подстановки метаданных в шаблоны путей: куда и в каком виде сохранять.
- **initData** — строка Telegram WebApp, используемая для аутентификации.

---

## 2. Цели, не-цели и ограничения

### 2.1 Цели (Goals)
- Стабильный сервис скачивания, управляемый через Telegram Mini App.
- Минимум ручных действий: распознавание канала/категории/метаданных.
- Гибкая настройка правил в БД.
- Предсказуемое сохранение файлов по шаблонам.
- Безопасная авторизация через Telegram initData.

### 2.2 Не-цели (Non-goals) на первом этапе
- Полноценный публичный web-сайт / много-пользовательская SaaS.
- Сложный ML/AI разбор названий.
- Обработка приватных видео с авторизацией Google.

### 2.3 Ограничения
- Сервис работает в домашней инфраструктуре.
- Загрузка выполняется через `yt-dlp` как внешний процесс.
- Хранилище — локальная файловая система.

---

## 3. Персоны и сценарии

### 3.1 Персоны
- **Owner (владелец сервера)**: один или несколько Telegram пользователей, которым разрешён доступ.
- **Viewer (опционально)**: user с read-only доступом (будущее расширение).

### 3.2 Основные сценарии (MVP)
1. Пользователь вставляет ссылку.
2. Сервис проверяет ссылку, извлекает `videoId`, получает базовые данные (title, channel, upload date, duration, thumbnails).
3. Сервис находит подходящее правило или предлагает создать.
4. Сервис формирует метаданные и план сохранения.
5. Пользователь подтверждает/правит.
6. Сервис создаёт job и начинает скачивание.
7. Пользователь видит прогресс/статус; по завершении — итоговые пути файлов.

### 3.3 Edge-сценарии
- Повторное добавление той же ссылки (идемпотентность).
- Видео недоступно/региональные ограничения.
- Скачивание прервалось — повтор.
- Изменение правил после создания job (должно быть определено поведение).

---

## 4. Архитектура (логическая)

### 4.1 Компоненты
- **Backend (Ktor)**
  - REST API для Mini App.
  - Модуль авторизации (Telegram initData validation).
  - Модуль правил (CRUD + матчинг).
  - Модуль метаданных (YouTube/yt-dlp extract + эвристики).
  - Модуль jobs (очередь, исполнение, ретраи, прогресс).
  - Модуль хранения (шаблоны путей, создание директорий, atomic move).
  - Модуль пост-обработки (ffmpeg/MP4Box и т.д.).
- **Frontend (Telegram Mini App)**
  - UI ввода ссылки.
  - UI редактирования метаданных.
  - UI правил.
  - UI списка задач/истории.

### 4.2 Потоки данных
- Frontend отправляет `initData` в каждом запросе.
- Backend валидирует, извлекает userId, проверяет allowlist.
- Backend отдаёт «проекты» данных: preview -> confirm -> job.

---

## 4.3 Модульная архитектура (Kotlin-идиоматично)

Цель — разделить **домен**, **контракт API**, **инфраструктуру** и **UI** так, чтобы:
- домен не зависел от Ktor/DB/JSON;
- контракт API был стабильным и версионируемым;
- маппинг между доменом и DTO был отдельным слоем;
- серверная часть могла развиваться независимо от клиента (KMP) и UI.

Рекомендуемая структура модулей (ориентир; допускаются небольшие отклонения):

- `domain`
  - доменные модели (data/sealed), доменные сервисы, use-cases, политики
  - доменные ошибки (sealed)
  - **никаких** зависимостей на Ktor, kotlinx.serialization JSON, БД, файловую систему

- `api/contract`
  - DTO для HTTP API (request/response)
  - правила версионирования и сериализации (discriminator `type` для sealed)
  - общие транспортные ошибки `ApiErrorDto`

- `api/mapping`
  - маппинг DTO <-> domain (чистые функции)
  - адаптация ошибок domain -> transport

- `api/client` (Ktor KMP client)
  - typed-клиент для `api/contract` (удобен для JS/Compose/других клиентов)
  - заботится о заголовках/`initData`, ретраях на уровне HTTP, (де)сериализации

- `server/infra`
  - БД (репозитории, транзакции, миграции)
  - работа с процессами (`yt-dlp`, `ffmpeg`), файловая система
  - адаптеры внешних систем

- `server/transport`
  - Ktor routing/controllers
  - transport-auth (Telegram initData)
  - превращение HTTP -> use-case input, и обратно

- `server/di`
  - wiring зависимостей (Koin/Dagger/ручное)

- `server/app`
  - entrypoint, конфигурация, сборка приложения

- `core` (опционально)
  - общее: utils, Result/Either, time abstraction, path utils
  - использовать только если реально помогает избежать циклических зависимостей

- `features` (Compose Multiplatform UI)
  - UI-экраны, presentation models, state

- `tgminiapp` (JS)
  - Telegram Mini App UI (Compose Multiplatform UI)

### 4.3.1 Правило зависимостей (Dependency rule)
- `domain` зависит ни от чего.
- `api/contract` зависит только от Kotlin stdlib + kotlinx.serialization (если выбрано).
- `api/mapping` зависит от `domain` и `api/contract`.
- `server/*` зависит от `domain` + `api/*` + infra libs.
- UI (`features`, `tgminiapp`) зависит от `dimain`, `api/client`.

### 4.3.2 Стабильность публичных API
- Всё, что попадает в HTTP, считается контрактом и живёт в `api/contract`.
- Изменения контрактов делаются через версионирование (`/api/v1/...` -> `/api/v2/...`) или через добавление новых полей с дефолтами.

---

## 5. Контракт: доменная модель

Ниже — «канонические» поля. Реализация может хранить часть в JSON, но API должно соответствовать.

### 5.1 Сущности

#### 5.1.1 Rule
**Назначение**: определяет логику обработки контента.

Поля:
- `id: UUID`
- `enabled: Boolean`
- `priority: Int` — порядок матчинга (больше = важнее)
- `match: RuleMatch` **(sealed, см. ниже)**
- `category: Category`
- `metadataTemplate: MetadataTemplate` — подсказки для распознавания
- `download: DownloadPolicy`
- `storage: StoragePolicy`
- `postProcess: PostProcessPolicy`
- `createdAt, updatedAt`

##### RuleMatch (DOMAIN, Kotlin sealed)
Задача RuleMatch — описать **один** критерий или композицию критериев (AND/OR/NOT) так, чтобы:
- его можно было сериализовать/хранить (jsonb),
- типы были явными,
- расширение было безопасным (новые варианты не ломают старый код).

**MVP-иерархия** (рекомендуемая):
- `RuleMatch.AllOf(matches: List<RuleMatch>)` — AND
- `RuleMatch.AnyOf(matches: List<RuleMatch>)` — OR
- `RuleMatch.ChannelId(value: String)`
- `RuleMatch.ChannelName(value: String)`
- `RuleMatch.TitleRegex(pattern: String)`
- `RuleMatch.UrlRegex(pattern: String)`

Нормы:
- `AllOf([])` запрещён (валидация на сохранение).
- `AnyOf([])` запрещён.
- Regex должны компилироваться при сохранении rule (fail fast).

**Алгоритм матчинга**:
- Матчинг выполняется на `VideoInfo` и/или `VideoSource`.
- `AllOf`: подходят все дочерние.
- `AnyOf`: подходит хотя бы один.
- `ChannelId`: точное совпадение.
- `ChannelName`: точное совпадение (MVP) + опционально case-insensitive (решить в реализации, но поведение задокументировать).
- `TitleRegex`/`UrlRegex`: стандартный Kotlin regex.

**Выбор правила среди нескольких совпавших**:
1) максимальный `priority`
2) если равны — более «специфичное» (предпочтения в порядке убывания): `ChannelId` > `ChannelName` > `UrlRegex` > `TitleRegex` > композиции
3) если всё ещё равны — минимальный `createdAt` (стабильность выбора) или `id` (лексикографически).

> Примечание: прежнее правило «подходит, если все заданные поля совпали» заменено sealed/композицией. Это Kotlin-идиоматичнее, проще расширять и легче валидировать.

#### 5.1.2 Category
MVP:
- `MUSIC_VIDEO`
- `SERIES`
- `OTHER`

#### 5.1.3 VideoSource
- `url: String`
- `videoId: String`
- `extractor: String` (обычно `youtube`)

#### 5.1.4 VideoInfo (сырое)
Получается из `yt-dlp --dump-json` или эквивалента.
Минимально нужные поля:
- `videoId`
- `title`
- `channelId`
- `channelName`
- `uploadDate` (YYYYMMDD)
- `durationSec`
- `webpageUrl`
- `thumbnails[]`

#### 5.1.5 ResolvedMetadata (DOMAIN, sealed)
Это то, что видит и редактирует пользователь. Тип метаданных зависит от `Category`.

**Общие требования**:
- В домене это sealed-иерархия, чтобы обязать код учитывать различия категорий.
- В API/DTO это тоже sealed-иерархия с discriminator `type`.
- На этапе `preview` сервер будет возвращать уже выбранный вариант metadata (и `category` должен с ним совпадать).

Рекомендуемая MVP-иерархия:
- `ResolvedMetadata.MusicVideo`
  - `artist: String` (обязателен)
  - `title: String` (обязателен)
  - `year: Int?`
  - `tags: List<String>` (может быть пустым)
  - `comment: String?`

- `ResolvedMetadata.SeriesEpisode`
  - `seriesName: String` (обязателен)
  - `season: String?` (рекомендуем string, допускается "S01" или "1")
  - `episode: String?` (string по тем же причинам)
  - `title: String` (обязателен)
  - `year: Int?`
  - `tags: List<String>`
  - `comment: String?`

- `ResolvedMetadata.Other`
  - `title: String` (обязателен)
  - `year: Int?`
  - `tags: List<String>`
  - `comment: String?`

**Инварианты/валидация (domain)**:
- `tags` нормализуются: trim, удаление пустых, дедупликация (case-insensitive, решение фиксировать).
- `year` в диапазоне 1800..2100 (или null).
- Пустые строки запрещены в обязательных полях (trim -> validation error).

**Связь с Category**:
- `MUSIC_VIDEO` <-> `MusicVideo`
- `SERIES` <-> `SeriesEpisode`
- `OTHER` <-> `Other`

> Важно: если в запросе на создание job приходит `category`, который не соответствует `metadata.type`, это `400 VALIDATION_ERROR`.

#### 5.1.6 StoragePlan (результат расчёта путей)
- `original: OutputTarget?`
- `converted: OutputTarget?`
- `additional: List<OutputTarget>`

`OutputTarget`:
- `path: String` (абсолютный путь на сервере)
- `container: String` (`mp4`, `mkv` ...)
- `kind: OutputKind` (`ORIGINAL`, `CONVERTED`, `AUDIO_ONLY`, ...)

#### 5.1.7 Job (Download)
- `id: UUID`
- `status: JobStatus`
- `source: VideoSource`
- `ruleId: UUID?`
- `category: Category`
- `rawInfo: VideoInfo` (или json)
- `metadata: ResolvedMetadata` (итоговая версия на момент старта)
- `storagePlan: StoragePlan`
- `progress: JobProgress` (nullable)
- `error: JobError?`
- `attempt: Int`
- `createdByTelegramUserId: String`
- `createdAt, updatedAt, startedAt, finishedAt`

`JobStatus` (MVP):
- `PREVIEW` — создана сущность preview (опционально как отдельная таблица)
- `QUEUED`
- `RUNNING`
- `POST_PROCESSING`
- `DONE`
- `FAILED`
- `CANCELLED`

`JobProgress`:
- `phase: String` (например `DOWNLOAD`, `MERGE`, `CONVERT`, `TAG`, `MOVE`)
- `percent: Int` (0..100)
- `message: String?`

`JobError`:
- `code: String` (стабильный код)
- `message: String` (человекочитаемо)
- `details: String?` (для логов, может быть скрыто в UI)

---

## 5.2 Контракт DTO и сериализация sealed (API contract)

Этот раздел фиксирует, как именно сериализуются sealed-классы в JSON.

### 5.2.1 Общий принцип
- Для любых polymorphic DTO (`RuleMatchDto`, `ResolvedMetadataDto`, возможно `JobErrorDto`) используется discriminator поле `type`.
- Поле `type` — строка, **стабильная**, используется в миграциях и в клиентах.
- Новые типы добавляются как новые значения `type`.

### 5.2.2 RuleMatchDto (API)
`
RuleMatchDto` повторяет доменную иерархию.

- Базовое поле: `type`
- Варианты (MVP):
  - `{ "type": "allOf", "matches": [RuleMatchDto, ...] }`
  - `{ "type": "anyOf", "matches": [RuleMatchDto, ...] }`
  - `{ "type": "channelId", "value": "UC...." }`
  - `{ "type": "channelName", "value": "Some Channel" }`
  - `{ "type": "titleRegex", "pattern": ".*Live.*" }`
  - `{ "type": "urlRegex", "pattern": "youtube\\.com/shorts/" }`

Валидация DTO:
- `matches` не пустой для `allOf`/`anyOf`.
- `value/pattern` не пустые.

### 5.2.3 ResolvedMetadataDto (API)
- Базовое поле: `type`
- Варианты (MVP):
  - `musicVideo`: `{ type, artist, title, year?, tags, comment? }`
  - `seriesEpisode`: `{ type, seriesName, season?, episode?, title, year?, tags, comment? }`
  - `other`: `{ type, title, year?, tags, comment? }`

Валидация DTO:
- обязательные поля не пустые.
- `tags` может отсутствовать -> трактуется как `[]`.

### 5.2.4 Принципы обратной/прямой совместимости
- Добавление **новых optional полей** допустимо в рамках v1.
- Удаление/переименование полей — только в новой версии API.
- Добавление новых `type` для sealed DTO в v1 возможно, но клиенты должны обрабатывать неизвестные варианты:
  - либо ошибок `UNSUPPORTED_TYPE`,
  - либо деградация в `other` (для metadata) — решение принять на этапе реализации и зафиксировать.

---

## 6. Контракт: шаблоны путей и безопасные имена

### 6.1 Шаблоны путей (Path templates)
Шаблон — строка с плейсхолдерами в виде `{variable}`.

Поддерживаемые переменные (MVP):
- `{artist}`
- `{title}`
- `{seriesName}`
- `{season}`
- `{episode}`
- `{year}`
- `{channelName}`
- `{uploadDate}` (YYYY-MM-DD)
- `{videoId}`

Примеры:
- Музыка оригинал: `/media/Music Videos/original/{artist}/{title} [{videoId}].mp4`
- Музыка конверт: `/media/Music Videos/converted/{artist}/{title}.mp4`
- Сериал: `/media/TV/{seriesName}/Season {season}/{episode} - {title}.mp4`

### 6.2 Нормализация имен файлов
Обязательные правила:
- Запрещённые символы заменять на пробел/подчёркивание: `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`.
- Убирать управляющие символы.
- Схлопывать множественные пробелы.
- Обрезать длину имени файла до безопасного лимита (например 180 символов) **после** подстановки.
- Защита от path traversal: итоговый путь должен оставаться внутри разрешённых `baseDirectories`.

---

## 7. Контракт: HTTP API (Backend)

> Примечание: конкретные пути/версии API можно менять, но структура сущностей и семантика должны сохраняться.

### 7.1 Общие правила
- Все запросы требуют Telegram initData.
- Ответы — JSON.
- Ошибки — единый формат `ApiError`.

`ApiError`:
- `error: { code: String, message: String, correlationId: String, details?: Any }`

`correlationId` генерируется на сервере на каждый запрос и пишется в логи.

### 7.2 Эндпоинты (MVP)

#### 7.2.1 POST `/api/v1/preview`
**Назначение**: принять ссылку, извлечь инфо, подобрать правило, вернуть распознанные метаданные и план (черновик).

Request:
- `url: String`

Response:
- `source: VideoSource`
- `videoInfo: VideoInfo`
- `matchedRule: Rule?` (или минимальная проекция)
- `category: Category`
- `metadata: ResolvedMetadata` (polymorphic/sealed)
- `storagePlan: StoragePlan`
- `warnings: List<String>`

Ошибки:
- `INVALID_URL`
- `VIDEO_UNAVAILABLE`
- `AUTH_REQUIRED` (если вдруг понадобится в будущем)
- `RULE_NOT_FOUND` (можно возвращать warning и category=OTHER)

#### 7.2.2 POST `/api/v1/jobs`
**Назначение**: создать job на основе preview + пользовательских правок и поставить в очередь.

Request:
- `source: VideoSource` (минимум `url` и `videoId`)
- `ruleId: UUID?`
- `category: Category`
- `metadata: ResolvedMetadata` (polymorphic/sealed)

Response:
- `job: Job`

Поведение:
- Должно быть **идемпотентно** по паре (`videoId`, `storagePlan`/`ruleId`) в разумных рамках. Если job уже `DONE` — вернуть существующий.

#### 7.2.3 GET `/api/v1/jobs?status=&limit=&offset=`
Список задач.

#### 7.2.4 GET `/api/v1/jobs/{id}`
Детали задачи.

#### 7.2.5 POST `/api/v1/jobs/{id}/cancel`
Отмена, если задача ещё не завершена.

#### 7.2.6 CRUD правил
- `GET /api/v1/rules`
- `POST /api/v1/rules`
- `GET /api/v1/rules/{id}`
- `PUT /api/v1/rules/{id}`
- `DELETE /api/v1/rules/{id}` (soft-delete или disable)

Минимальная валидация:
- нельзя сохранять rule без match (то есть `RuleMatch` не может быть пустым)
- storage templates должны содержать хотя бы `{title}` или `{videoId}` чтобы избежать постоянных коллизий

### 7.3 SSE/WebSocket (опционально)
Для прогресса:
- `GET /api/v1/jobs/{id}/events` (SSE)

События:
- `statusChanged`
- `progress`
- `logLine` (в dev)

MVP можно сделать поллингом.

---

## 8. Пайплайн обработки (Job execution)

### 8.1 Контракт выполнения
Вход: `Job` со статусом `QUEUED`.
Выход: `DONE` с заполненными путями или `FAILED` с `JobError`.

Шаги:
1. **Lock/idempotency**: убедиться, что два воркера не выполняют одну job.
2. **Prepare**: создать временную директорию (например `/tmp/tgvd/<jobId>/`).
3. **Download**:
   - вызвать `yt-dlp` (предпочтительно с параметрами для устойчивости: retries, fragments, continue).
   - сохранять в temp.
4. **Post-processing**:
   - если нужна конвертация: `ffmpeg` (или другой инструмент) -> temp.
   - теги: MP4Box/AtomicParsley/ffmpeg metadata (выбрать в реализации). Важно: сохранять корректный UTF-8.
5. **Move to final**:
   - создать директории.
   - переместить атомарно (где возможно): сначала в `.partial`, затем rename.
6. **Cleanup**: удалить temp.
7. **Persist**: обновить статус и финальные пути.

### 8.2 Парсинг прогресса yt-dlp
Рекомендуется запускать с `--newline --progress` и парсить stdout.
Сохранять агрегированный прогресс в `JobProgress`.

### 8.3 Повторы (Retry policy)
- По умолчанию `maxAttempts = 3`.
- Повторять при сетевых ошибках/429/5xx.
- Не повторять при `INVALID_URL` или если диск полон.

---

## 9. Интеграция с Telegram: авторизация и доступ

### 9.1 Валидация initData
Использовать алгоритм из официальной документации Telegram.
В проекте уже есть черновик `TelegramAuthValidator` (пример в предыдущей версии документа). Требования:
- постоянное сравнение hash (timing-safe compare желательно)
- поддержка dev-режима (с моковым hash) **только** при включённом флаге конфигурации

### 9.2 Allowlist пользователей
Конфигурация должна содержать список разрешённых `telegramUserId` (или правило "всем кто в чате X" — позже).

Поведение:
- initData валиден, но user не в allowlist -> `403 FORBIDDEN`.

---

## 10. База данных (PostgreSQL)

### 10.1 Общие требования
- миграции обязательны (Flyway/Liquibase — выбрать в реализации; Exposed migrations тоже возможно).
- время хранить в UTC.

### 10.2 Предлагаемая схема (MVP)

#### 10.2.1 `rules`
- `id uuid pk`
- `enabled boolean not null`
- `priority int not null default 0`
- `match jsonb not null` (сериализация RuleMatchDto с `type`)
- `category text not null`
- `metadata_template jsonb not null`
- `download_policy jsonb not null`
- `storage_policy jsonb not null`
- `post_process_policy jsonb not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`

Indexes:
- gin index на `match` (если удобно)

#### 10.2.2 `jobs`
- `id uuid pk`
- `status text not null`
- `video_id text not null`
- `source_url text not null`
- `rule_id uuid null`
- `category text not null`
- `raw_info jsonb not null`
- `metadata jsonb not null` (ResolvedMetadataDto с `type`)
- `storage_plan jsonb not null`
- `progress jsonb null`
- `error jsonb null`
- `attempt int not null default 0`
- `created_by_telegram_user_id text not null`
- `created_at timestamptz not null`
- `updated_at timestamptz not null`
- `started_at timestamptz null`
- `finished_at timestamptz null`

Constraints:
- уникальность (опционально) `video_id + rule_id + status in (QUEUED,RUNNING,POST_PROCESSING)` чтобы снизить дубли.

#### 10.2.3 `job_outputs` (опционально)
Если нужно хранить многофайловые результаты более нормализованно:
- `job_id uuid fk`
- `kind text`
- `path text`

MVP можно хранить в `storage_plan`.

---

## 11. Конфигурация

Рекомендуемый набор параметров (Hoplite/YAML):
- `server.port`
- `server.baseUrl` (для Mini App)
- `telegram.botToken`
- `telegram.devMode: Boolean`
- `telegram.allowedUserIds: ["123", ...]`
- `storage.baseDirectories` (список разрешённых корней)
- `storage.tempDirectory`
- `ytDlp.path` (путь к бинарнику)
- `ffmpeg.path`
- `postProcess.taggingTool` (enum)
- `jobs.maxConcurrentDownloads`
- `jobs.maxAttempts`
- `jobs.pollIntervalMs` (если есть scheduler)
- `db.*` (url, user, password)

---

## 12. Наблюдаемость и логирование

- Структурные логи (JSON желательно).
- `correlationId` на запрос.
- Логи job'ов должны содержать `jobId`.
- Метрики (опционально на MVP): число активных задач, среднее время скачивания, ошибки по кодам.

---

## 13. Безопасность

- Никогда не логировать `initData` целиком (можно логировать userId и первые N символов hash).
- Не хранить botToken в репозитории.
- Путь назначения должен быть внутри allowlisted baseDirectories.
- Ограничить размеры входных полей и timeouts на внешние процессы.
- Для внешних процессов:
  - экранировать аргументы, не строить команду строкой.
  - лимитировать stdout/stderr буфер и сохранять хвост.

---

## 14. Deployment

MVP: docker-compose (рекомендуется)
- `app` (Ktor)
- `postgres`
- volume mounts:
  - `/media/...` в контейнер
  - temp directory

Также возможен запуск напрямую на сервере.

---

## 15. Тестирование (обязательный минимум)

### 15.1 Unit tests
- `TelegramAuthValidator`:
  - валидный initData
  - неверный hash
  - devMode поведение
- Template engine:
  - подстановка переменных
  - нормализация имени
  - защита от path traversal
- Rule matching:
  - приоритеты
  - специфичность
  - композиции `AllOf/AnyOf`
- (новое) Sealed DTO serialization:
  - `RuleMatchDto` и `ResolvedMetadataDto` корректно сериализуются/десериализуются с `type`

### 15.2 Integration tests
- Ktor маршруты с test engine.
- DB: CRUD rules, создание jobs.
- Job runner: мок внешних процессов (`yt-dlp`, `ffmpeg`) через фейковый бинарник/скрипт.

### 15.3 Smoke tests
- "preview -> create job -> done" на одном публичном коротком видео (в ручном режиме).

---

## 16. Критерии готовности (Definition of Done) для MVP

- [ ] Можно вставить ссылку в Mini App и получить preview.
- [ ] Можно отредактировать метаданные и создать job.
- [ ] Job скачивает видео и кладёт файл(ы) в ожидаемые директории.
- [ ] Статусы/прогресс отображаются (polling достаточно).
- [ ] Правила можно создавать/редактировать.
- [ ] Авторизация через initData + allowlist работает.
- [ ] Есть unit + integration тесты для критичных частей.

---

## 17. Приложение: замечания по реализации (не спецификация)

- В примерах ранее фигурировал Compose Multiplatform для Telegram Mini App. В текущем репозитории есть `yt-downloader-example/` на React/Vite — это ок, спецификация API от UI не зависит.
- Если UI будет на React, контракт initData и API остаётся тем же.
- На первом этапе можно ограничиться `MUSIC_VIDEO` и `OTHER`, и добавить `SERIES` позже — но тогда это должно быть отражено в правилах/валидаторах.
