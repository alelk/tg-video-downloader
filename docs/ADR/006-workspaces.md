# ADR-006: Workspace (мультитенантная изоляция ресурсов)

**Статус**: Принято  
**Дата**: 2026-03-01  
**Авторы**: Alex Elkin

---

## Контекст

Все ресурсы (правила, задачи, настройки хранения) принадлежат **workspace** — группе пользователей
с общим рабочим пространством. Workspace обеспечивает мультитенантную изоляцию:
- Пользователи одного workspace видят **все** ресурсы группы (правила, задачи)
- Разные workspace полностью изолированы друг от друга
- Один пользователь может состоять в нескольких workspace и переключаться между ними
- Файлы скачиваются в директории, специфичные для workspace

---

## Решение

### Нейминг: Workspace

Рассмотренные варианты:
- **Group** — перегружен (конфликт с Telegram Groups)
- **Realm** — слишком абстрактен, не интуитивен
- **Workspace** ✅ — интуитивно понятен, подразумевает общее рабочее пространство с ресурсами (аналогия: Slack, Notion, GitHub Organizations)

### Доменная модель

```kotlin
@JvmInline value class WorkspaceId(val value: Uuid)

data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val createdAt: Instant,
)

enum class WorkspaceRole { OWNER, MEMBER }

data class WorkspaceMember(
    val workspaceId: WorkspaceId,
    val userId: TelegramUserId,
    val role: WorkspaceRole,
    val joinedAt: Instant,
)
```

### Принадлежность ресурсов

```kotlin
data class Rule(
    val workspaceId: WorkspaceId,
    ...
)

data class Job(
    val workspaceId: WorkspaceId,
    val createdBy: TelegramUserId, // для аудита — кто создал задачу
    ...
)
```

### Workspace в URL (path-prefix)

Все доменные ресурсы вложены в workspace path:

```
/api/v1/workspaces/{workspaceId}/jobs
/api/v1/workspaces/{workspaceId}/rules
/api/v1/workspaces/{workspaceId}/preview
/api/v1/workspaces/{workspaceId}/members
```

Рассмотренные варианты:
- **Header `X-Workspace-Id`** — implicit context, не REST-идиоматичен, не видно в URL/логах, не кешируемо
- **Path-prefix** ✅ — REST-идиоматичен, type-safe через Ktor Resources, каждый URL самодостаточен, невозможно забыть workspaceId

### Auto-provisioning

При первой аутентификации пользователя (через Telegram auth) автоматически:
1. Создаётся персональный workspace "Personal"
2. Пользователь добавляется как OWNER

### Авторизация

Двухуровневая:
1. **Глобальный allowlist** (`telegram.allowedUserIds`) — gate-keeping, кто вообще может использовать сервис
2. **Workspace membership** — кто имеет доступ к конкретному workspace

### Роли

- **OWNER** — может управлять участниками (добавлять/удалять)
- **MEMBER** — полный доступ ко всем ресурсам workspace (правила, задачи)

> Все участники workspace (OWNER и MEMBER) имеют **равный доступ к ресурсам**.
> Роли различаются только в управлении составом workspace.

---

## Последствия

### Положительные
- Пользователи в одной группе видят все задачи и правила
- Полная изоляция между группами
- Пользователь может участвовать в нескольких рабочих пространствах
- Type-safe workspace context через Ktor Resources — невозможно забыть workspaceId
- `{workspaceId}` / `{workspaceName}` доступен как placeholder в path templates

### Отрицательные
- Дополнительная сложность: новые таблицы, middleware
- UI должен поддерживать переключение workspace

### Что НЕ входит в первую итерацию
- Invite-ссылки для приглашения в workspace
- Гранулярные permissions (read-only, admin)
- Transfer ownership
- Workspace settings (отдельные настройки хранения per workspace)

---

## API

### Workspace management
```
GET    /api/v1/workspaces                                         → WorkspaceListResponseDto
POST   /api/v1/workspaces                                         → WorkspaceDto (201)
GET    /api/v1/workspaces/{workspaceId}/members                    → WorkspaceMemberListResponseDto
POST   /api/v1/workspaces/{workspaceId}/members                    → WorkspaceMemberDto (201)
DELETE /api/v1/workspaces/{workspaceId}/members/{userId}           → 204
```

### Resource endpoints (scoped to workspace)
```
POST   /api/v1/workspaces/{workspaceId}/preview                    → PreviewResponseDto
GET    /api/v1/workspaces/{workspaceId}/jobs                       → JobListResponseDto
POST   /api/v1/workspaces/{workspaceId}/jobs                       → JobDto (201)
GET    /api/v1/workspaces/{workspaceId}/jobs/{id}                  → JobDto
POST   /api/v1/workspaces/{workspaceId}/jobs/{id}/cancel           → JobDto
GET    /api/v1/workspaces/{workspaceId}/rules                      → RuleListResponseDto
POST   /api/v1/workspaces/{workspaceId}/rules                      → RuleDto (201)
GET    /api/v1/workspaces/{workspaceId}/rules/{id}                 → RuleDto
PUT    /api/v1/workspaces/{workspaceId}/rules/{id}                 → RuleDto
DELETE /api/v1/workspaces/{workspaceId}/rules/{id}                 → 204
```

### System-wide (не привязаны к workspace)
```
GET    /api/v1/system/yt-dlp/status                                → YtDlpStatusDto
POST   /api/v1/system/yt-dlp/update                                → YtDlpUpdateResponseDto
```

---

## DB Schema

```sql
CREATE TABLE workspaces (
    id         UUID PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace_members (
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id      BIGINT NOT NULL,
    role         TEXT NOT NULL DEFAULT 'member',
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_user ON workspace_members(user_id);
```

Столбец `workspace_id` в таблицах `rules` и `jobs` — FK на `workspaces(id)`.
Столбец `created_by` в таблице `jobs` — для аудита, кто создал задачу.
