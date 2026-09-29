---
status: draft               # draft → stable (после ревью владельцем) → done
owner: Alex (alelk)
updated: 2026-09-29
related: [ step-01/README.md, ../project-status.md, ../ADR/009-engineering-skills-baseline.md ]
---

# Step 01 — Рефакторинг под engineering-ai-skills (обратно совместимый)

## Goal

Навести порядок в репозитории по правилам из `engineering-ai-skills` там, где они окупаются для
**домашнего** сервиса (один инстанс, 1–5 пользователей), не меняя ничего, что видит пользователь,
клиент или развёрнутая инсталляция. Сложность ради сложности не добавляем: часть правил скиллов
сознательно не применяется (см. Decisions и ADR-009).

Что не так сегодня (проверено по коду, коммит `2b2ebff`, локальная копия от 2026-09-29):

1. **Транзакции не управляются из одной точки.** Каждый метод каждого репозитория сам открывает
   транзакцию через `server/infra/.../db/dbQuery.kt`. `JobProcessor`, `SystemSettingsHolder` и роуты
   ходят в репозитории мимо `TransactionRunner` — там каждая запись идёт отдельной транзакцией.
   Внутри use-case'а `dbQuery` вызывается под `ExposedTransactionRunner` и, возможно, присоединяется
   к внешней транзакции; это ничем не проверено (эксперимент в 01.4, тест в 01.8).
   `SQLException` превращается в 500 через `StatusPages`, `catchingDb` нет; в запросах строковые
   литералы статусов, `now()` берётся не из внедрённого `Clock`.
2. **Жизненный цикл задачи ломается.**
   - Отмена не останавливает загрузку: `CancelJobUseCase` пишет `CANCELLED`, а `JobProcessor`
     продолжает и перезаписывает статус прогрессом/`COMPLETED` (`JobRepositoryImpl.updateStatus`
     пишет безусловно).
   - После рестарта задачи в `DOWNLOADING` висят вечно: `pollLoop` берёт только `PENDING`,
     восстановления нет. Поллер берёт снимок списка `PENDING`, атомарного захвата нет.
   - `ENTRYPOINT ["sh","-c","java …"]` без `exec` во всех трёх серверных Dockerfile — SIGTERM не
     доходит до JVM, graceful shutdown не выполняется; а если выполняется, ветка
     `CancellationException` в `processJob` помечает задачу `CANCELLED`.
   - yt-dlp/ffmpeg ждутся блокирующим `process.waitFor()` и не убиваются при отмене корутины.
   - `jobs.maxAttempts`, `jobs.retryDelayMs`, `logging.level/format` читаются из конфига, но нигде
     не используются.
3. **Слои перемешаны.** `server:transport` зависит от `server:infra` (`previewRoutes`,
   `systemRoutes` импортируют `SystemSettingsHolder`, `AudioTrackSelector`, `YtDlpConfig`…).
   В роутах бизнес-логика: `POST …/jobs` валидирует `mediaSelection` и оркестрирует `saveAsRule`
   в отдельной транзакции; проверка «ресурс принадлежит workspace» скопирована в каждый by-id роут;
   роуты инжектят репозитории; `RuleId(Uuid.parse(it))` и `!!` превращают плохой ввод в 500.
4. **Старт и тесты.** Flyway запускается внутри ленивого Koin-`single` при первом обращении к БД —
   HTTP уже принимает запросы, упавшая миграция не останавливает процесс. `/health` не проверяет БД.
   `monitor.subscribe` вместо `subscribeFor`. `configureApplication` нетестируем: **0 тестов роутов,
   0 тестов репозиториев** (есть ~233 теста domain, контракта и селекторов).
5. **Сборка.** Нет convention plugins: в каждом модуле `alias(...)`, `jvmToolchain(21)`, таргеты;
   корневой `subprojects {}` с репозиториями и `mavenLocal()`; Kotest Gradle plugin + KSP;
   Testcontainers core `2.0.3` vs postgresql `1.21.4`; `shadowJar` висит на `build`;
   нет Detekt/ktlint; Dockerfile'ы собирают образом `gradle:8.14`, а wrapper — Gradle 9.7.1.
6. **Доступ.** Пустые allow-list'ы = доступ любому пользователю Telegram, без предупреждения.
   `TELEGRAM_ALLOWED_USER_IDS`/`TELEGRAM_ALLOWED_USERNAMES` из `.env.example` до конфига **не доходят**:
   env-source Hoplite делит имя по `_` (получается `telegram.allowed.user.ids`), а `application.yaml`
   и inline-конфиг compose содержат литерал `allowedUserIds: []`. Инсталляция, где список задан
   только в `.env`, сейчас открыта всем.
7. **UI.** Экраны вызывают `TgVideoDownloaderClient` прямо из composable с `remember`-состоянием,
   клиент бросает `ApiException` (23 места `catch`/`runCatching` в `features`);
   `tgminiapp/Main.kt` (346 строк) содержит UI (`WorkspaceInitializer`); `PreviewScreen.kt` —
   1114 строк, `SettingsScreen.kt` — 841.
8. **Документация врёт.** `AGENTS.md` ссылается на несуществующие `api/client/di`,
   `GeminiLlmAdapter`, `./gradlew allTests`; `LlmPort` внедряется nullable с `TODO`.

**Result of the step:** все правила из Decisions выполняются, `./gradlew build` зелёный и включает
тесты роутов, репозиториев и жизненного цикла задач; при этом API-поверхность, JSON, схема БД,
ключи конфигурации и способ развёртывания не изменились — кроме явно перечисленного в G10 и Fork 5.

## How to use this plan

Обзор здесь; каждая стадия — отдельный исполняемый файл в [`step-01/`](step-01/README.md) с
таблицей статусов и протоколом исполнителя. **Ядро — стадии 01.1–01.9.** Стадии 01.10–01.12 можно
отложить без вреда; 01.13 закрывает шаг в любой точке остановки.

## Why this step now

Код растёт фичами (multi-audio, субтитры), а каждая новая фича копирует ближайший пример — то есть
бизнес-логику в роутах и транзакции в репозиториях. Сначала страховочная сетка и порядок, потом
фичи. Скиллы установлены локально в `.claude/skills/` и подгружаются агентами.

## End-to-end scenario

Пользователь открывает Mini App (закэшированный старый бандл), делает превью ссылки, создаёт задачу
с `saveAsRule`, отменяет другую задачу посреди загрузки, затем `docker compose pull && up -d`
(рестарт посреди третьей загрузки). Ожидаемо: превью и создание работают как раньше; отменённая
задача остаётся `CANCELLED`, процесс yt-dlp убит; прерванная рестартом задача возвращается в
`PENDING` и докачивается; данные, созданные до рефакторинга, читаются; пользователь не из
`TELEGRAM_ALLOWED_USER_IDS` получает 403.

## Forks

### Fork 1: где запускать миграции
Скилл `exposed-postgres`: отдельный provisioning-шаг под schema-owner ролью. **Chosen:** Flyway
внутри процесса при старте, но **жадно и до приёма HTTP**, с fail-fast. Одна инсталляция, одна роль —
отдельный migrate-job только добавит движущихся частей.

### Fork 2: задача, прерванная рестартом
Варианты: `FAILED` («перезапустите вручную») или `PENDING` (докачать). **Chosen (владелец,
2026-09-29):** `PENDING` без увеличения `attempt` — домашний сценарий «обновил контейнер» не должен
требовать ручной работы.

### Fork 3: состояние экранов
Скилл: androidx `ViewModel` + Navigation 3. **Chosen (владелец, 2026-09-29):** Voyager остаётся,
держатель состояния — Voyager `ScreenModel` (`voyager-koin` уже в зависимостях) с тем же паттерном
(`StateFlow` + `Channel`-эффекты + один `onEvent`). Миграция навигации ради домашнего UI не окупается.

### Fork 4: аутентификация
Скилл `telegram-miniapp` советует обменять `initData` на JWT-сессию. **Chosen:** валидировать
`initData` на каждый запрос, как сейчас. Других клиентов нет.

### Fork 5: переменные allow-list из `.env`
Варианты: оставить как есть (и исправить документацию) или подключить. **Chosen (владелец,
2026-09-29):** подключить `TELEGRAM_ALLOWED_USER_IDS`/`TELEGRAM_ALLOWED_USERNAMES` в
`application.yaml` и в inline-конфиг compose — это задокументированный контракт (`.env.example`,
`CONFIGURATION.md`, `DEPLOYMENT.md`). Следствие: там, где переменные заданы, доступ сузится до
перечисленных пользователей. Это исправление, коммит `fix:`, упомянуть в release notes.

## Decisions

| №   | Question | Decision | In plain words |
|-----|----------|----------|----------------|
| G1  | Что значит «обратно совместимо» | HTTP: набор роутов (метод+путь), JSON-поля/типы/дискриминаторы/дефолты, значения `code` и HTTP-статусы — без изменений, только добавления. БД: `V1…V8` побайтно неизменны, только новые `V9+`; хранимые строки (`status`, `category`, JSONB) читаются. Конфиг: каждый ключ и env-переменная сохраняют смысл и дефолт; новые ключи опциональны с дефолтом = текущее поведение. Деплой: имена образов, пути Dockerfile, порты, тома, `/health`, release-ассеты — те же. Исключения — только G10 и Fork 5. | Старый клиент, старая БД, старый конфиг и старый compose работают с новым сервером. |
| G2  | Миграции | Flyway в процессе, жадно, до установки роутинга; ошибка миграции → процесс завершается с ненулевым кодом. `baselineOnMigrate` не трогаем. Переопределяет правило «migrations separately» из `exposed-postgres`/`gradle-docker-build`. | Схема создаётся при старте, но падение видно сразу. |
| G3  | Роли БД и изоляция | Одна роль БД, без RLS. Изоляция по workspace — в use-case'ах. `postgres-multitenancy-rls` не применяется. | Никаких ролей и политик в Postgres. |
| G4  | Аутентификация | `initData` на каждый запрос, `devMode` как есть. `TELEGRAM_ALLOWED_USER_IDS`/`TELEGRAM_ALLOWED_USERNAMES` доходят до конфига (Fork 5), пустое значение = пустой список. Оба списка пусты — доступ всем, как сейчас, но громкий `WARN` при старте; `devMode=true` — тоже `WARN`. Без JWT/сессий (`ktor-auth-sessions` не применяется). | Allow-list работает так, как написано в `.env.example`; про «открыто всем» сервер говорит вслух. |
| G5  | Транзакции | Транзакцию открывает только `TransactionRunner`; репозитории работают в текущей транзакции; `dbQuery` удаляется. Долгий I/O (yt-dlp, ffmpeg, LLM, HTTP) — никогда внутри транзакции. | Одна точка управления транзакциями. |
| G6  | Статусы задач | Переходы — чистая таблица в domain; каждая запись статуса — compare-and-set `from → to`; захват `PENDING→DOWNLOADING` атомарный (`FOR UPDATE SKIP LOCKED`); при старте `DOWNLOADING/POST_PROCESSING → PENDING`; при остановке активные → `PENDING`; пользовательская отмена убивает процесс. Автоповторов нет; `maxAttempts`/`retryDelayMs` принимаются и документируются как неиспользуемые. Один инстанс сервера — условие развёртывания. | Отмена работает, рестарт ничего не теряет. |
| G7  | Где бизнес-логика | Роут = разбор → use-case → ответ. Проверка членства и принадлежности ресурса workspace — в use-case. Для чтений — query use-case'ы. Имена классов сохраняют суффикс `…UseCase` (без массовых переименований); новая команда живёт в файле своего use-case, существующие `*Request` остаются где лежат. | Transport только переводит, решает domain. |
| G8  | Опциональный LLM | `UnconfiguredLlmPort` вместо `LlmPort?`; поведение идентично (фолбэк на `MetadataResolver`). Реализация адаптеров — не в этом шаге. | Никаких `?.let` по опциональному сервису. |
| G9  | Время | `kotlin.time.Clock` внедряется везде (без дефолтов в конструкторах), DI биндит `Clock.System`; репозитории тоже берут время из `Clock`. | Время тестируемо. |
| G10 | Единственное намеренное изменение wire | Некорректный ввод (битый JSON, битый UUID в пути/теле, пустое значение value-класса) → `400 VALIDATION_ERROR` вместо `500 INTERNAL_ERROR`. Ответы, которые уже сейчас 400 (например, `POST /workspaces` с битым slug и телом `{"error": …}`), не меняются. Ошибки БД → `500 INTERNAL_ERROR`, как сейчас. Подтверждено владельцем 2026-09-29. | Клиент по-прежнему видит ошибку, просто честную. |
| G11 | Сборка | Included build `convention-plugins/`, префикс `tgvd.`; web-таргет остаётся `js(IR)` (старые Telegram WebView), wasm не вводим; Kotest на JVM через JUnit 5, JS-раннер тестов выключен, `compileTestKotlinJs` остаётся гейтом чистоты; Detekt + ktlint с baseline; `shadowJar` вне `build`. | Один `./gradlew build` как единственный гейт. |
| G12 | UI | Voyager и `TgvdTheme`/`MaterialTheme` остаются (без Navigation 3 и без токенов дизайн-системы). `ScreenModel` + Entry/Content — для Preview и Settings; остальные экраны — через храповик `KNOWN_*`. Клиент возвращает `Either<ApiError, T>`. | Порядок там, где больно; остальное не трогаем. |
| G13 | Что из скиллов сознательно НЕ берём | Клиентские id и идемпотентный create, колонки `version` и CAS для агрегатов (кроме статуса задачи), cursor-пагинация, outbox/события, переименование DTO/`@SerialName`, `@Resource`-перестройка URL, BuildKit secrets, SHA-пиннинг actions, `compose-design-system`, `compose-navigation3`, `compose-web-shell`, `llm-integration`. | Для домашнего масштаба это цена без выгоды. |
| G14 | Документация | Дерево `docs/` не переименовываем (без `00-…70-`); ADR продолжают нумерацию (`ADR-009`); планы — `docs/plans/`; frontmatter `status` добавляется только в документы, которые трогаем. Каждая стадия правит документы, факты в которых изменила. | Меньше churn, но без вранья. |
| G15 | Скиллы | Локальная копия в `.claude/skills/` (исключена через `.git/info/exclude`), источник правды — репозиторий `engineering-ai-skills`; исключения проекта — только в ADR-009, не в копии скиллов. | Скиллы не правим здесь. |

## Doc preconditions

- Этот план — `stable` (владелец меняет статус после ревью).
- ADR-009 создаётся и принимается в стадии 01.1; остальные стадии на него опираются.

## The mine

**Главная мина — «воскрешение» и потеря задач** (стадии 01.8–01.9). После переноса репозиториев
под `TransactionRunner` и введения CAS легко оставить путь записи статуса — колбэк прогресса,
`finally`, хук остановки — который либо выполняется вне транзакции (падение в рантайме в ветке,
которую юнит-тесты не проходят), либо пишет статус безусловно. Итог: отменённая задача становится
`COMPLETED`, прерванная рестартом висит в `DOWNLOADING`. По умолчанию это ничем не ловится — тестов
процессора нет.

Доказательство — тест на Testcontainers с фейковым `VideoDownloader`, управляемым из теста:
(1) отмена через роут посреди загрузки → итог `CANCELLED`, поток загрузчика отменён, `updated_at`
больше не меняется; (2) `stop()` посреди загрузки → `PENDING`, новый процессор доводит до
`COMPLETED`; (3) строка в `downloading` до старта → перезапрошена и завершена. Проверено красным:
замена CAS на безусловный `UPDATE` роняет (1).

**Вторичный риск — тихий слом совместимости** при переносе логики из роутов (другой дефолт поля,
пропавший ключ JSON, неразобранная старая JSONB-строка). Ловится сеткой из 01.4: снимок
API-поверхности, golden JSON и замороженные JSONB-фикстуры, созданные текущим кодом.

**Мина 01.5 — пустой allow-list из env.** Если пустая переменная превратится в список из одной
пустой строки, allow-list станет непустым и сервер ответит 403 всем, включая владельца. Закрывается
тестом привязки конфига в 01.5.

## Scope

### In
- Сборка: convention plugins, каталог, статанализ, единый гейт.
- Сервер: тестируемый `module()`, старт/остановка, health, fail-fast конфиг, allow-list из env,
  тонкий transport, транзакции, надёжная обработка задач, Docker `exec`.
- Тесты: роуты, репозитории, миграции, жизненный цикл задач, fitness-тесты правил.
- Клиент и UI: `Either`-клиент, тонкая оболочка `tgminiapp`, `ScreenModel` для двух экранов.
- Документы: ADR-009, `AGENTS.md`/`CLAUDE.md`/`PROJECT_CONTEXT.md`, `project-status.md`, правка
  фактов в затронутых `docs/*.md`.

### Not in
- Новые фичи, реализация LLM-адаптеров, авто-повторы задач.
- Исправление находки `saveAsRule.includeCategory` (поле принимается, но игнорируется: обёртка в
  `api:mapping` его теряет, в domain параметра нет) — это изменение поведения; фиксируется в
  project-status как известная проблема.
- Самостоятельное вступление в workspace по slug: `POST /workspaces` с занятым slug добавляет
  вызывающего как `MEMBER` (`CreateWorkspaceUseCase`). Клиент строит на этом переподключение;
  сохраняем как есть, фиксируем в project-status как известный риск.
- `VideoInfoCacheImpl.evictExpired` нигде не вызывается — не подключаем, фиксируем в project-status.
- Дефолт `TELEGRAM_DEV_MODE=true` в `docker-compose.yaml` и `.env.example` (compose для локальной
  разработки) — не меняем; в 01.5 добавляется `WARN` при старте и предупреждение в `DEPLOYMENT.md`.
- Разбиение `JobProcessor` на domain use-case'ы, пагинация в SQL, upsert вместо select-then-write.
- Всё из G13.

## Composition

### Domain
Таблица переходов `JobStatus`; `WorkspaceAccess` (проверка членства); команды и use-case'ы для
чтений и для создания задачи с валидацией и `saveAsRule`; `AudioTrackSelector`/`SubtitleSelector`
переезжают в domain вместе с доменной моделью настроек дорожек; порт системных настроек;
`Clock` без дефолтов; `NoopTransactionRunner` → `domain-test-fixtures`;
новые `DomainError`: `DatabaseFailed`, `JobStatusConflict`.

### Contract and mapping
Wire не меняется (кроме G10). Разбор строковых id и `saveAsRule.matchBy` — в `api:mapping` через
`Either`. Лишняя обёртка `buildSaveAsRuleRequest` в `api:mapping` удаляется.

### Infrastructure
Без `dbQuery`; `catchingDb` для `SQLException`; `Clock` в репозиториях; `claimNext`/`transition`
в `JobRepository`; жадный `DatabaseFactory`; убийство процессов при отмене; `SystemSettingsHolder`
реализует доменные порты; `UnconfiguredLlmPort`.

### API and UI
Роуты без репозиториев и без `server:infra`; `Application.module(config, eagerDatabase,
startBackgroundServices, overrides)`; `/health/live`, `/health/ready`; `400` для битого ввода;
allow-list из env; клиент `Either<ApiError, T>`; `WorkspaceInitializer` → `features`;
`ScreenModel` для Preview/Settings.

## Order of work

1. 01.1 — Решения и вход для агентов (только документы)
2. 01.2 — Сборка: convention plugins и каталог
3. 01.3 — Статический анализ (Detekt + ktlint, baseline)
4. 01.4 — Страховочная сетка (тестируемый `module()`, Testcontainers, снимки wire и JSONB)
5. 01.5 — Запуск, конфигурация и жизненный цикл сервера
6. 01.6 — Тонкий transport I: задачи и превью
7. 01.7 — Тонкий transport II: остальное, отвязка от infra, гигиена domain
8. 01.8 — Транзакции и репозитории
9. 01.9 — Надёжная обработка задач
10. 01.10 — Доставка: Docker и CI
11. 01.11 — API-клиент `Either` и тонкая оболочка `tgminiapp`
12. 01.12 — `ScreenModel` для Preview и Settings + храповик
13. 01.13 — Закрытие шага

Порядок 01.6–01.8 важен: `dbQuery` удаляется только после того, как роуты перестали ходить в
репозитории напрямую.

## Tests

### Pure
Таблица переходов статусов (каждая пара), валидация `CreateJob`, селекторы дорожек/субтитров
(перенесённые тесты), разбор `matchBy`/id в `api:mapping`, `validateConfig`, нормализация allow-list.

### On fakes
Use-case'ы с фейковыми репозиториями, честно соблюдающими контракт порта (чужой workspace → not
found), и `TestClock`.

### Testcontainers (`postgres:16-alpine`, как в `docker-compose.yaml`)
Миграции `V1…V8(+V9)` на пустой БД + `validate`; round-trip каждого репозитория; замороженные
JSONB-фикстуры; CAS и `claimNext` (две параллельные попытки → одна успешная); процессор задач
с фейковым загрузчиком (мина).

### Through the route
Снимок API-поверхности (`api-surface.txt`); golden JSON ответов; 401 без заголовка; 403 для
пользователя не из allow-list; 404 на ресурс чужого workspace; 400 на битый UUID (G10); отмена
посреди загрузки через роут; `/health/ready`.
