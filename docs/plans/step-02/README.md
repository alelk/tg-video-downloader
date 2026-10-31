---
status: stable
owner: Alex (alelk)
updated: 2026-10-31
related: [ ../step-02-ios-keyboard.md, 02.1-reproduce.md, 02.2-upstream-repair.md, 02.3-device-acceptance.md ]
---

# Step 02 — Stages

The owner authorized the first candidate on 2026-10-31 and deferred physical-device checks
until after deployment. The [overview execution amendment](../step-02-ios-keyboard.md)
supersedes the original reproduction-first prerequisites in the stage files.
| Stage                             | Purpose                                  | Status                   |
|-----------------------------------|------------------------------------------|--------------------------|
| [02.1](02.1-reproduce.md)         | Reproduce and identify tested artifacts  | Device evidence deferred     |
| [02.2](02.2-upstream-repair.md)   | Compose 1.12.2 and remove global bridge  | Implemented; bundle passed; full gate incomplete |
| [02.3](02.3-device-acceptance.md) | Physical-device acceptance and close-out | Waiting for deployment and owner |

## Executor protocol

1. Follow the approved execution amendment; device checks follow deployment. Read `CLAUDE.md`, this index,
   the overview, the stage file, and previous executor notes.
2. Use the repository's local skills and ADR-009. Commit only if the owner asks.
3. Record unexpected evidence and any new decision in executor notes. Do not implement an
   unapproved backport, native adapter or unrelated fix.
4. Preserve failing-test evidence. A build cannot replace a software-keyboard device check.
5. Each implementation stage requires `./gradlew build` green plus its own checks. Missing
   device access or failed gates leave the relevant stage waiting.
6. Update statuses and documentation to actual results. Set the phase back to None only after
   close-out; do not describe the incident as resolved before device acceptance.
