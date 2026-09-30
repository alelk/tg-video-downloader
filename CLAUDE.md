# CLAUDE.md

> **Start here.** Cross-agent guide (modules, commands, rules): [`AGENTS.md`](AGENTS.md). Project
> context: [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md). This file adds the current phase
> and what is specific to Claude Code.

## Current phase

**None.** Step 01 (backward-compatible refactoring to the engineering-ai-skills baseline) is closed —
summary, open questions for the owner and known issues in
[`docs/project-status.md`](docs/project-status.md). The next step starts with a new plan in
`docs/plans/` (`step-02-….md` + `step-02/` stages, the same executor protocol as
[`docs/plans/step-01/README.md`](docs/plans/step-01/README.md)) once the owner has approved it; until
then, work only on explicit requests.

Project history and known issues: [`docs/project-status.md`](docs/project-status.md) — read it
before implementation work (not needed for a doc edit or a point fix).

## Quick commands (Claude Code)

The gate is `./gradlew build` (see AGENTS.md). While iterating, narrower tasks are faster:

```bash
./gradlew :domain:jvmTest                 # domain use-cases and models (commonTest, run on JVM)
./gradlew :api:contract:jvmTest           # contract serialization tests
./gradlew :server:infra:test              # server infra tests (JVM)
./gradlew :domain:compileTestKotlinJs     # commonMain/commonTest purity (no java.*)
```

- A full build takes minutes: run it in the background with `--console=plain`, output to a log.
- Read git state with `git --no-optional-locks status` in automation (no stray `index.lock`).

## Skills

`.claude/skills/` loads by description; it is a local, git-ignored copy — never edit it (see
AGENTS.md → Skills). Project exceptions to the skills: [ADR-009](docs/ADR/009-engineering-skills-baseline.md).
Don't restate skill rules in prompts or docs — link the skill or the ADR.

## Doc-first workflow

1. Work only from `stable` documents (plan, stage file, ADR). 2. Implement the slice from the docs
+ skills. 3. `./gradlew build` green + the stage's Checks. 4. Code and docs disagree → stop and ask;
the doc changes first. 5. Commit only when asked; Conventional Commits.

## Domain names most often confused

`Channel` vs `ChannelId`, `MetadataTemplate` vs `ResolvedMetadata` vs `UserOverrides`, domain
`*Request` vs `*RequestDto`, job statuses — see
[PROJECT_CONTEXT §8](docs/PROJECT_CONTEXT.md#8-terminology-pitfalls).
