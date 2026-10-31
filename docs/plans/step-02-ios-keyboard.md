---
status: stable
owner: Alex (alelk)
updated: 2026-10-31
related: [ step-02/README.md, ../research/ios-telegram-keyboard.md, ../project-status.md, ../PROJECT_CONTEXT.md ]
---

# Step 02 — Reliable iPhone Telegram keyboard

## Goal and current status

An enabled editable field opens the software keyboard on the first deliberate tap in the
owner's iPhone Telegram Mini App, including after dismissing the keyboard or navigating back.
Typing, paste, selection and submission preserve the correct value.

**Implementation authorized by the owner on 2026-10-31.** Apply the first candidate (K1)
and prepare it for deployment; the owner will check the affected iPhone after deployment.
This instruction supersedes the pre-change device reproduction and diagnostic comparison
prerequisites in K2/K3 and stages 02.1/02.2. Those measurements have not been performed.
The existing owner report is the baseline symptom; no automated keyboard-visibility test
or physical-device result is claimed. Device acceptance remains pending in 02.3.

This execution amendment takes precedence over the original proposed sequence below.
K4–K8 and the repair scope remain unchanged. No backport or DOM adapter is authorized.

## Why this step now

Text entry is unusable by the owner's report. The October 30 workaround did not resolve it.
The declared Compose 1.12.1 lacks an iOS focus fix already available in stable 1.12.2, while
four application-wide DOM/event patches interfere with normal browser and Compose behavior.

## End-to-end scenario

Open the Mini App from its normal Telegram entry point → tap the URL field once → keyboard
appears → type/paste a valid URL → request preview → edit metadata → create a job. Dismiss the
keyboard, tap the same field, switch fields, navigate away/back, and repeat without extra taps.
Also exercise editable settings and modal/dialog fields, not just the first screen.

## Proposed decisions

K1 is approved for implementation; K2/K3 are deferred as described above.

| Ref | Question               | Proposed decision                                                                                                                                              |
|-----|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| K1  | First candidate        | Upgrade only the catalog's Compose version to 1.12.2 and remove the whole HTML keyboard bridge; verify resolved runtime/UI artifacts                           |
| K2  | Evidence before fixing | Reproduce on the affected iPhone with an ordinary native input control; capture a failing test case before changes                                             |
| K3  | Attribution            | Compare current build, bridge removed only, upgrade only in diagnostic isolation, and upgrade plus bridge removed; do not ship the upgrade-only bridge variant |
| K4  | Reopen regression      | Explicitly test CMP-10971; if present, select a proven published #3517 release or propose a narrow upstream backport before coding it                          |
| K5  | Target/shell           | Retain `js(IR)`, Kotlin and Telegram wrapper unless a documented dependency incompatibility requires a separate decision                                       |
| K6  | Compatibility          | No route, DTO, error, DB, runtime config, image name, port or volume changes                                                                                   |
| K7  | Diagnostics            | Local fixture/inspection only; no secret/text logging, production endpoint or persistent debug mode                                                            |
| K8  | Fallback               | Real DOM input adapter only after the stable upstream candidate fails and a separate design is approved                                                        |

Compose 1.13.0-beta01 is not the answer to K4: its tagged source lacks #3517. A release-branch
merge is insufficient evidence of availability in a published binary.

## Preconditions and open decisions

The owner approved implementation with device checks after deployment. Record actual
device/Telegram versions and deployment identity when those checks become available. If the phone predates Safari 27.0, #3466 remains a reasonable stable candidate but is not
an established explanation for that device; use the trace to identify the mismatch.

A fallback backport/DOM adapter is deliberately not executable from this draft. It requires a
concrete reviewed design covering artifact publication/provenance or platform UI integration.
Do not silently pick an unverified beta or modify another repository.

## The mine

**Shipping a fix that passes builds and opens once, but fails to reopen after dismissal because
the DOM input stayed focused.** Desktop tests and `activeElement` checks can miss it. A second
mine is testing a fresh local sibling build while deploying an older published wrapper/bundle.

Prove the affected-device reproduction fails before the change and passes after it. Acceptance
requires repeated cold/warm first taps, dismiss/reopen and correct text entry, with artifact
identity recorded. A missing device result leaves the stage waiting, not done.

## Scope and composition

The first implementation changes `gradle/libs.versions.toml` and
`tgminiapp/src/jsMain/resources/index.html`. It may add a narrowly justified regression check
or local reproduction fixture after the evidence identifies the failure. Server, domain,
contract and database remain outside this repair. No clipboard redesign, broad UI migration,
cache-policy redesign or unrelated build cleanup.

For a future native DOM fallback, UI remains in `features`; `tgminiapp` stays a shell. Resolve
the common/JVM/JS abstraction and existing fitness-test constraints in that separate design.
Apply local `telegram-miniapp`, `compose-multiplatform-ui`, `kmp-architecture` and testing skills,
with ADR-009 taking precedence.

## Order of work

1. [02.1 — Reproduction and provenance](step-02/02.1-reproduce.md): record red behavior and
   distinguish native-control, Compose and workaround failures.
2. [02.2 — Stable upstream repair](step-02/02.2-upstream-repair.md): verified 1.12.2 graph,
   bridge removal, build gate and focused device comparison.
3. [02.3 — Device acceptance and close-out](step-02/02.3-device-acceptance.md): complete matrix,
   matching packaged artifacts, evidence and documentation updates.

Status and execution protocol live in [the stage index](step-02/README.md).

## Validation

Use the physical-device reproduction as the failing test first. Preserve the fixture, procedure
and result; do not claim a desktop DOM test proves keyboard visibility. Add automated checks
only for a concrete owned behavior they can meaningfully protect, and demonstrate failure
against the previous behavior. Do not alter or weaken existing fitness tests.

Each implementation stage runs `./gradlew build`, the repository's only aggregate gate. The JS
test runner remains disabled under ADR-009; a local browser fixture is supplemental. Final
acceptance also requires the real iPhone in Telegram, as detailed in 02.3. If an environment
blocks the build, record the failure and leave the gate incomplete.
