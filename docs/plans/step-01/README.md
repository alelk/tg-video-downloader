---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ ../step-01-refactoring.md ]
---

# Step 01 — stages

> Исполняемые файлы стадий. Цель, решения и риски: [`../step-01-refactoring.md`](../step-01-refactoring.md)
> — **прочитай целиком перед своей стадией.** Правила кода — скиллы в `.claude/skills/`
> (локальная копия, в git не попадает); исключения проекта — `docs/ADR/009-engineering-skills-baseline.md`.

| Stage | File                                                              | Status      |
|-------|-------------------------------------------------------------------|-------------|
| 01.1  | [Решения и вход для агентов](01.1-decisions-and-agent-entry.md)    | done        |
| 01.2  | [Сборка: convention plugins](01.2-build-conventions.md)           | done        |
| 01.3  | [Статический анализ](01.3-static-analysis.md)                     | done        |
| 01.4  | [Страховочная сетка](01.4-safety-net.md)                          | done        |
| 01.5  | [Запуск и жизненный цикл](01.5-bootstrap-and-lifecycle.md)        | done        |
| 01.6  | [Тонкий transport I](01.6-thin-transport-jobs-preview.md)         | done        |
| 01.7  | [Тонкий transport II](01.7-thin-transport-rest.md)                | not started |
| 01.8  | [Транзакции и репозитории](01.8-transactions.md)                  | not started |
| 01.9  | [Надёжная обработка задач](01.9-job-processing.md)                | not started |
| 01.10 | [Доставка: Docker и CI](01.10-delivery.md)                        | not started |
| 01.11 | [Клиент и оболочка](01.11-client-and-shell.md)                    | not started |
| 01.12 | [ScreenModel для Preview/Settings](01.12-screen-models.md)        | not started |
| 01.13 | [Закрытие шага](01.13-close-out.md)                               | not started |

Statuses: `not started` → `done`; `waiting` — заблокировано вопросом к владельцу.

## Stage file layout

Read before starting · Stage decisions · Work · Checks · Done when · Executor notes.

## Executor protocol

1. Стадии строго по порядку; один исполнитель (одна сессия агента) на стадию.
2. Прочитай этот README, обзор шага, файл своей стадии, `CLAUDE.md` и **Executor notes всех
   предыдущих стадий**.
3. Не решай за пределами плана. Непонятно / противоречит коду / нужно решение, которого нет →
   стоп, вопрос в «Executor notes», статус `waiting`, продолжай то, что от вопроса не зависит.
4. Не чини попутно — находки вне стадии записывай в notes.
5. Инвариант совместимости G1 нарушать нельзя. Если тест из 01.4 (`ApiSurfaceTest`, golden JSON,
   JSONB-фикстуры, роут-тесты) краснеет — чинится код, а не тест. Разрешены только два вида
   изменений ожиданий: G10 (битый ввод 500 → 400) — в той стадии, что чинит соответствующий разбор
   (01.5 — `StatusPages`, 01.6–01.7 — ручной разбор в роутах); и добавление новых роутов в
   `api-surface.txt` (G1 разрешает добавления).
6. Списки `KNOWN_*` в fitness-тестах только сокращаются.
7. Файлы `server/infra/src/main/resources/db/migration/V1…V8` не редактируются никогда.
8. Стадия заканчивается зелёным `./gradlew build`, выполненными Checks, обновлённым статусом здесь
   и одной строкой в `docs/project-status.md`.
9. Коммит — только по просьбе владельца; Conventional Commits (`refactor:`, `test:`, `build:`,
   `docs:`, `fix:` для исправлений поведения из G6/G10/Fork 5); без `!` и `BREAKING CHANGE`
   (semantic-release поднимет major).
10. Для чтения состояния git в автоматизации используй `git --no-optional-locks status`, чтобы
    не оставлять `.git/index.lock` в песочнице.
