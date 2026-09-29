---
status: stable
owner: Alex (alelk)
updated: 2026-09-29
related: [ PROJECT_CONTEXT.md, ../CLAUDE.md, plans/step-01-refactoring.md, plans/step-01/README.md ]
---

# Project status

> History of steps, newest first. Read before implementation work. A step gets a full entry
> (shipped / the mine / deliberately not built / open questions) when it closes; while it runs,
> each finished stage adds one line.

## Step 01 — Backward-compatible refactoring to the engineering-ai-skills baseline (`plans/step-01-refactoring.md`, in progress)

**Started 2026-09-29** from commit `2b2ebff` (+ plan in `7610c61`). Goal: order in build, layers,
transactions and job processing, a test safety net, and truthful agent docs — without changing
anything a user, client or deployed installation sees (except G10 and Fork 5,
[ADR-009](ADR/009-engineering-skills-baseline.md)). Stages: [`plans/step-01/README.md`](plans/step-01/README.md).

- 2026-09-29 — 01.1 done: ADR-009 (skills baseline, overrides, compatibility contract); `AGENTS.md`
  and `CLAUDE.md` rewritten; `docs/PROJECT_CONTEXT.md` and this file created; false facts fixed in
  `ARCHITECTURE.md` and `TESTING.md`.

## Known issues

Deliberately not fixed in Step 01 unless the stage is named.

- `saveAsRule.includeCategory` is accepted in `POST …/jobs` but ignored: the `api:mapping`
  wrapper drops it and the domain has no such parameter. Fixing it changes behaviour — out of
  Step 01.
- Joining a workspace by slug: `POST /workspaces` with a slug that is already taken adds the caller
  as `MEMBER` (`CreateWorkspaceUseCase`). The client relies on it to reconnect; kept as is, a known
  access risk.
- `VideoInfoCacheImpl.evictExpired` is never called — expired cache rows are not cleaned up.
- `TELEGRAM_DEV_MODE=true` is the default in `docker-compose.yaml` and `.env.example` (local-dev
  compose). Not changed; a startup `WARN` and a `DEPLOYMENT.md` warning come in 01.5.
- `TELEGRAM_ALLOWED_USER_IDS` / `TELEGRAM_ALLOWED_USERNAMES` never reach the config (Hoplite splits
  env names on `_`; `application.yaml` and the compose inline config hold literal `[]`). An
  installation that sets them only in `.env` is open to every Telegram user. **Closed in 01.5**
  (Fork 5, `fix:`).
- `jobs.maxAttempts`, `jobs.retryDelayMs`, `logging.level`, `logging.format` are read from the
  config but not used anywhere.
