# ADR-006: Workspace (Multi-tenant Resource Isolation)

**Status**: Accepted  
**Date**: 2026-03-01  
**Authors**: Alex Elkin

---

## Context

All resources (rules, jobs, storage settings) belong to a **workspace** — a group of users sharing a common working environment. Workspace provides multi-tenant isolation:
- Users in the same workspace see **all** group resources (rules, jobs)
- Different workspaces are completely isolated from each other
- A single user can belong to multiple workspaces and switch between them
- Files are downloaded to workspace-specific directories

---

## Decision

### Naming: Workspace

Options considered:
- **Group** — overloaded (conflicts with Telegram Groups)
- **Realm** — too abstract, not intuitive
- **Workspace** ✅ — intuitive, implies a shared working environment with resources (analogy: Slack, Notion, GitHub Organizations)

### Domain Model

```kotlin
@JvmInline value class WorkspaceId(val value: Uuid)

/**
 * Human-readable unique workspace identifier.
 * Used in URL paths and application configuration.
 * Examples: "personal", "my-team", "project-alpha-2"
 */
@JvmInline value class WorkspaceSlug(val value: String) // ^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$

data class Workspace(
    val id: WorkspaceId,      // UUID — internal technical key
    val slug: WorkspaceSlug,  // "my-team" — human-readable, used in URLs and config
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

### Resource Ownership

```kotlin
data class Rule(
    val workspaceId: WorkspaceId,
    ...
)

data class Job(
    val workspaceId: WorkspaceId,
    val createdBy: TelegramUserId, // audit — who created the job
    ...
)
```

### Workspace in URL (path-prefix)

All domain resources are nested under the workspace path:

```
/api/v1/workspaces/{workspaceId}/jobs
/api/v1/workspaces/{workspaceId}/rules
/api/v1/workspaces/{workspaceId}/preview
/api/v1/workspaces/{workspaceId}/members
```

Options considered:
- **Header `X-Workspace-Id`** — implicit context, not REST-idiomatic, not visible in URLs/logs, not cacheable
- **Path-prefix** ✅ — REST-idiomatic, type-safe via Ktor Resources, every URL is self-contained, impossible to forget workspaceId

### Auto-provisioning

On the user's first authentication (via Telegram auth):
1. A personal workspace "Personal" is automatically created
2. The user is added as OWNER

### Authorization

Two-level:
1. **Global allowlist** (`telegram.allowedUserIds`) — gate-keeping: who can use the service at all
2. **Workspace membership** — who has access to a specific workspace

### Roles

- **OWNER** — can manage members (add/remove)
- **MEMBER** — full access to all workspace resources (rules, jobs)

> All workspace members (OWNER and MEMBER) have **equal access to resources**.
> Roles differ only in workspace membership management.

---

## Consequences

### Positive
- Users in a group see all jobs and rules
- Complete isolation between groups
- A user can participate in multiple workspaces
- Type-safe workspace context via Ktor Resources — impossible to forget workspaceSlug
- `{workspaceSlug}` in URL — readable, bookmark- and log-friendly
- Slug is used in application configuration for per-workspace presets

### Negative
- Additional complexity: new tables, middleware
- UI must support workspace switching

### Out of scope for the first iteration
- Invite links for workspace invitations
- Granular permissions (read-only, admin)
- Transfer ownership
- Workspace settings (separate storage settings per workspace)

---

## API

### Workspace management
```
GET    /api/v1/workspaces                                         → WorkspaceListResponseDto
POST   /api/v1/workspaces                                         → WorkspaceDto (201)
GET    /api/v1/workspaces/{slug}/members                           → WorkspaceMemberListResponseDto
POST   /api/v1/workspaces/{slug}/members                           → WorkspaceMemberDto (201)
DELETE /api/v1/workspaces/{slug}/members/{userId}                  → 204
```

### Resource endpoints (scoped to workspace)
```
POST   /api/v1/workspaces/{slug}/preview                           → PreviewResponseDto
GET    /api/v1/workspaces/{slug}/jobs                              → JobListResponseDto
POST   /api/v1/workspaces/{slug}/jobs                              → JobDto (201)
GET    /api/v1/workspaces/{slug}/jobs/{id}                         → JobDto
POST   /api/v1/workspaces/{slug}/jobs/{id}/cancel                  → JobDto
GET    /api/v1/workspaces/{slug}/rules                             → RuleListResponseDto
POST   /api/v1/workspaces/{slug}/rules                             → RuleDto (201)
GET    /api/v1/workspaces/{slug}/rules/{id}                        → RuleDto
PUT    /api/v1/workspaces/{slug}/rules/{id}                        → RuleDto
DELETE /api/v1/workspaces/{slug}/rules/{id}                        → 204
```

### System-wide (not scoped to workspace)
```
GET    /api/v1/system/yt-dlp/status                                → YtDlpStatusDto
POST   /api/v1/system/yt-dlp/update                                → YtDlpUpdateResponseDto
```

---

## DB Schema

```sql
CREATE TABLE workspaces (
    id         UUID PRIMARY KEY,
    slug       TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$'),
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

The `workspace_id` column in `rules` and `jobs` tables is a FK to `workspaces(id)`.
The `created_by` column in the `jobs` table is for audit — who created the job.
