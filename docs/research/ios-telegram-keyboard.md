---
status: stable
owner: Alex (alelk)
updated: 2026-10-31
related: [ ../plans/step-02-ios-keyboard.md, ../project-status.md, ../PROJECT_CONTEXT.md ]
---

# iPhone Telegram Mini App: software keyboard investigation

Evidence snapshot as of 2026-10-31. **The incident remains open.** `stable` describes this
research record, not a verified fix. Implementation is proposed in the
[approved recovery plan](../plans/step-02-ios-keyboard.md).

## Symptom and confidence

The owner reports that tapping an editable field in Telegram on iPhone usually does not open
the software keyboard; sometimes it opens after about 20 taps. The October 30 workaround did
not resolve it. This blocks normal use of the application.

**Strongest explanation:** this project declares Compose 1.12.1, which predates a documented
fix for backing-input focus on iOS Safari 27.0. Its delayed second focus request can overwrite
the original gesture-associated request before WebKit displays the keyboard. The current
application also globally patches browser behavior in ways that conflict with Compose.

**Not yet established:** the affected phone's exact iOS/Telegram versions, the deployed
dependency graph and bundle identity, or a physical-device trace proving which mechanism is
responsible there. No iPhone was controlled during this investigation. In particular, the
iOS Safari 27.0 explanation must not be generalized to an older affected OS without evidence.

## Repository evidence and yesterday's changes

| Evidence                                         | Observed fact                                                                                                   |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `gradle/libs.versions.toml`                      | Kotlin 2.4.20; Compose 1.12.1; `io.github.alelk:tg-mini-app:1.1.2`                                              |
| `eeac23c`, October 27                            | Moved from Compose 1.10.0/Kotlin 2.3.10 to 1.12.1/2.4.20 and upstream Telegram wrapper 2.0.0                    |
| `ab4bae0`, October 30                            | Added keyboard bridge, restored fork 1.1.2 and sibling composite substitution; removed URL-field clipboard icon |
| `tgminiapp/src/jsMain/resources/index.html`      | Contains four global keyboard patches, with no iOS guard                                                        |
| `features/.../download/screen/UrlInputScreen.kt` | Ordinary single-line Material `OutlinedTextField`; no explicit synchronous DOM focus adapter                    |
| `ea506f1`, October 31                            | Added nginx `Cache-Control: no-cache` for ordinary web assets                                                   |

No separate explanation of this keyboard incident was found in the existing documentation.
Commit subjects and HTML comments recorded attempted explanations, not successful device tests.

The four bridge changes are:

1. Replace `Document.prototype.activeElement` with recursive Shadow DOM traversal. This changes
   standard DOM behavior for every consumer. Compose's newer `DomInputStrategy.isInputActive()`
   already checks the input's own root via `getRootNode().activeElement`.
2. Replace `Element.prototype.attachShadow` and inject `z-index: 10`, `pointer-events: auto`,
   `opacity: 0.001` into every shadow-root input/textarea. Compose's backing input is an IME
   implementation detail; raising it above the canvas can change hit testing and selection.
3. Make `TouchEvent.prototype.preventDefault()` a no-op for `touchstart` and `touchend`.
   This does not make a later focus request synchronous. It also defeats Compose's selective
   default cancellation used to avoid Safari blur/keyboard loss; see upstream [S6].
4. Capture and suppress mouse/click events for 600 ms after an intercepted touch cancellation.
   This can cancel native input activation and other controls. The precise contribution to
   this incident is a hypothesis requiring device comparison.

The comment claiming iOS 18 rejects negative-z-index inputs, citing WebKit bug 279904, is
**unverified**. The bug tracker returned HTTP 403 during research. Do not retain that claim as
an established Apple policy. Likewise, an unconditional RAF focus is not inherently a
self-rescheduling loop: distinguish one delayed request from repeated calls by the framework.

## Verified upstream mechanism

JetBrains PR [#3446][S1], backported as [#3466][S2], explains the change explicitly:

> Starting from iOS Safari 27, repeated focus requests override previous pending ones.
> If the new request happens not during user interaction (e.g. in rAF), all pending focus
> requests are ignored.

The referenced [WebKit change][S3] defers `ElementDidFocus` IPC until a rendering update,
including stored gesture state. Its stated motivation is reducing layout/IPC work, rather
than a newly tightened security rule. The longstanding distinction between DOM focus and
permission to present the software keyboard still matters.

The relevant Compose behavior, simplified from upstream source:

```kotlin
// 1.12.1: synchronous request can be overwritten by a later non-gesture request.
backingElement.focus()
window.requestAnimationFrame { backingElement.focus() }

// 1.12.2 (#3466): avoid replacing a successful pending focus.
if (!inputStrategy.isInputActive()) backingElement.focus()
window.requestAnimationFrame {
    if (!inputStrategy.isInputActive()) backingElement.focus()
}

// Follow-up #3517: also allow reopening when DOM focus survived dismissal.
backingElement.focus()
window.requestAnimationFrame {
    if (!inputStrategy.isInputActive()) backingElement.focus()
}
```

This is upstream pseudocode for explanation, not a JavaScript patch to paste into the app.
The fix belongs to Compose's backing input, not a global override of all DOM focus calls.

| Version/change    | Verified state on investigation date                                    | Implication                                                       |
|-------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------|
| Compose 1.12.1    | Project's declared version; lacks #3466                                 | Primary upgrade candidate                                         |
| Compose 1.12.2    | Stable release, October 15; release notes explicitly include #3466 [S4] | Smallest published stable candidate for the initial-focus problem |
| #3517 / CMP-10971 | Merged October 20; removes only the synchronous active-input guard [S5] | Covers tapping an already focused field after keyboard dismissal  |
| #3519             | Cherry-pick of #3517 into `release/1.13`, October 26 [S5]               | Merge alone does not prove a published artifact includes it       |
| 1.13.0-beta01     | Tagged source still has the synchronous guard                           | Do not upgrade to this beta expecting #3517                       |

`document.activeElement === input` is not proof that the software keyboard is visible.
Conversely, do not state that every programmatic focus is categorically blocked on iOS:
WebKit has host-script and hardware-keyboard exceptions [S7]. The app cannot configure
Telegram's native WKWebView policy through ordinary page JavaScript.

## Build and deployment provenance

`settings.gradle.kts` automatically substitutes `../tg-mini-app` if that directory exists.
It exists in this environment at clean commit `06865d25e24242d355c9f30aab7befe42aadf10a`, with
`compose.version=1.12.1`. Docker does not copy the sibling, so local and image builds can use
different wrapper sources. Record resolved dependencies rather than assuming the catalog is
the complete graph. Do not edit the sibling as an incidental fix in this repository.

The sibling's `telegramWebApp` calls default `ComposeViewport`, using `body`; the `#root` in
HTML is not evidence that Compose renders into it. A prospective listener scoped only to
`#root` could miss all Compose interaction. Verify the actual host before any adapter work.

Existing generated JS/source maps under `tgminiapp/build/` have old timestamps and describe
an earlier implementation. They are useful leads, not evidence of the deployed 1.12.1 runtime.
Use a fresh distribution and dependency report for comparisons.

The nginx change permits storing assets but requires revalidation; it does not disable caching
or prove that an already open Telegram page reloaded. Compare served HTML/JS hashes, response
headers through the real ingress, and the exact release/image. Do not alter API/config keys
to solve artifact identification.

## Proposed remedy and alternatives

**First candidate:** Compose 1.12.2 plus removal of the entire global bridge. Test both changes
independently on a diagnostic build to separate the upstream defect from workaround effects.
Keep Kotlin, Telegram wrapper, JS target, server and deployment contracts unchanged unless
dependency resolution proves a specific additional change necessary.

**If dismissal/reopen alone remains broken:** trace it against CMP-10971. Prefer a published
release verified to contain #3517. If none is suitable, document a narrow, reproducible Compose
backport as a separate decision before implementation. Do not rewrite minified output or
replace `HTMLElement.prototype.focus` globally.

**If the native control works but Compose still cannot meet acceptance:** investigate a real
DOM input/textarea adapter in `features`' platform-specific UI, behind a common abstraction
with a JVM implementation. It must own the editable value, selection, IME composition,
accessibility, geometry and lifecycle. A hidden proxy followed by asynchronous focus transfer
is not a reliable substitute. This is a larger fallback design, not the first repair.

No timeout retry, synthetic click, global touch cancellation bypass, target migration to Wasm,
or Telegram SDK version change has been shown to fix this incident.

## Reproduction and evidence still required

Record phone model, iOS version/build, Telegram version, launch path (bot/menu/attachment/link),
app image/release and asset hashes, and whether an external keyboard is connected. Compare a
visible ordinary HTML input, plain Compose field, and production field in the **same Telegram
WKWebView**. Safari is a useful control but does not replace Telegram testing.

Use a local diagnostic fixture, not a production route or a new runtime config key. Where
available, Telegram's inspectable WKWebView allows Safari Web Inspector [S8]. Otherwise collect
minimal diagnostics locally in that fixture. Trace event type, `isTrusted`, timestamps,
focus/blur, focus call stack, own-root active element, input geometry/connectedness, and
`visualViewport` changes. Capture `navigator.userActivation` only when supported; it is a clue,
not a keyboard guarantee. Viewport changes are supporting evidence, not definitive visibility.
With the current getter patch installed, obtain native active-element state from a pristine
baseline or the original getter, not the patched property alone.

Never capture `initData`, tokens, input values, clipboard data, URL query strings or request
headers. Focus diagnostics should identify controls with fixed anonymous labels.

The [plan's acceptance stage](../plans/step-02/02.3-device-acceptance.md) specifies repeated
first-tap, dismiss/reopen, navigation, IME and selection checks. A green Gradle build and desktop
WebKit automation cannot establish physical iPhone software-keyboard behavior.

## Validation of this documentation change

Only documentation changed during this investigation. Relative Markdown links, frontmatter
presence and whitespace were checked across all eight changed/new documents; checks passed.
An independent research agent reviewed the mechanism, version claims and proposed stages.

`./gradlew build --console=plain` was attempted but the Gradle daemon disappeared. A second
attempt with one worker, in-process Kotlin compilation and a 1 GiB Gradle heap reached
`kotlinNpmInstall` and remained there without further log output for several minutes; it was
explicitly interrupted. **The full build gate has not passed.** Logs for this session are
`/tmp/tgvd-ios-keyboard-build.log` and `/tmp/tgvd-ios-keyboard-build-retry.log`; these temporary
files are not committed evidence. No application change, device acceptance, commit or deployment
was performed.

## Authorized candidate implementation — 2026-10-31

The owner authorized Compose 1.12.2 plus full bridge removal, deferring device checks until
after deployment. The candidate is implemented; resolved JS UI/foundation/runtime and the
published UI sources were checked. See [02.2 executor notes](../plans/step-02/02.2-upstream-repair.md)
for artifact hashes, test/build results and limitations. The earlier planning-only results
above remain a historical record. The incident is still open: no affected-iPhone success or
deployment result has been recorded.

## Sources

Source links were checked through GitHub APIs/raw files on 2026-10-31. Apple/WebKit bug pages
and several documentation sites returned 403; the source commits below were accessible.

- [S1: Compose #3446, original focus fix][S1], commit `2c990232f1d052e03dad2d9b9902fdafd4e44240`.
- [S2: Compose #3466, stable backport][S2], commit `1642d773f518159b6b9a9dfb9b665064c8286631`; CMP-10825.
- [S3: WebKit defers ElementDidFocus IPC][S3], commit `64ee64bad41e5d511964c29b8ab00ee77269821a`.
- [S4: Compose 1.12.2 release notes][S4]; [tagged backing-input source][S9].
- [S5: Compose #3517, repeated-tap/reopen correction][S5], commit `6c5c8aae8db6fc0bb67db00f63c00798fde18e66`;
  [release-branch cherry-pick #3519](https://github.com/JetBrains/compose-multiplatform-core/pull/3519).
- [S6: Compose #3271, Safari touchstart/blur handling][S6];
  [#3232, conditional touchend cancellation](https://github.com/JetBrains/compose-multiplatform-core/pull/3232);
  [related #3276](https://github.com/JetBrains/compose-multiplatform-core/pull/3276).
- [S7: WebKit native-client script focus exception][S7], bug 243416; this is not a general page-JS keyboard API.
- [S8: Telegram iOS WebAppWebView source][S8]; host inspection depends on the installed Telegram build.

[S1]: https://github.com/JetBrains/compose-multiplatform-core/pull/3446

[S2]: https://github.com/JetBrains/compose-multiplatform-core/pull/3466

[S3]: https://github.com/WebKit/WebKit/commit/64ee64bad41e5d511964c29b8ab00ee77269821a

[S4]: https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2

[S5]: https://github.com/JetBrains/compose-multiplatform-core/pull/3517

[S6]: https://github.com/JetBrains/compose-multiplatform-core/pull/3271

[S7]: https://github.com/WebKit/WebKit/commit/2b0c968051a7552d2f54d39bf790e0970fce259d

[S8]: https://github.com/TelegramMessenger/Telegram-iOS/blob/279f89e4d4f63f17227bf877dc3d8990f22ecd58/submodules/WebUI/Sources/WebAppWebView.swift#L198

[S9]: https://github.com/JetBrains/compose-multiplatform-core/blob/5f017375d926c73f1937408601b6cb28c12a8812/compose/ui/ui/src/webMain/kotlin/androidx/compose/ui/platform/BackingDomInput.kt
