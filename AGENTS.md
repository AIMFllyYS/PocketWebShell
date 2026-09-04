# AGENTS.md

This file is the repository-level operating contract for coding agents. It applies to the entire repository. A nested `AGENTS.md` may add stricter module-specific rules; the nearest file wins. Explicit user instructions override this file.

## Mission

PocketWebShell is a Kotlin/Jetpack Compose Android application that turns websites into launcher-style entries with metadata discovery, fixed-capacity pages, folders, browser tabs, and managed WebView sessions.

Protect these product properties in every change:

1. The home grid has deterministic geometry at every supported width.
2. Drag visuals never participate in grid measurement.
3. Browser callbacks are attributed by sessionId and update that session's own per-tab state.
4. WebView and foreground-service claims match Android's actual lifecycle limits.
5. Release signing material never enters source control or logs.

## Repository map

- `app/`: composition root, Navigation 3, bottom navigation, session controller, foreground service integration.
- `core/model/`: cross-module domain models; keep Android dependencies out where practical.
- `core/data/`: Room entities/DAO, DataStore preferences, repositories, persistence migrations.
- `core/designsystem/`: Material 3 theme and reusable visual primitives.
- `core/webengine/`: WebView ownership, configuration, pooling, profiles, local asset loading, callbacks.
- `feature/home/`: launcher pages, folders, fixed grid, DragLayer and persisted ordering. Interaction layer split: `HomeInteractionState.kt` (centralized drag/menu/edit session state holder) and `HomeGestures.kt` (cell gesture detection, root drag session, blank-area long-press, pinch edit mode, edge-hover page-turn state machine); `HomeScreen.kt` keeps composition root, grid containers, menus/dialogs and overlays. Instrumented gesture tests: `app/src/androidTest/.../HomeGestureInstrumentedTest.kt`（`:app:connectedDebugAndroidTest`，真机/模拟器回归主页手势验收路径）。
- `feature/add/`: URL normalization, metadata parsing, icon discovery and app editing.
- `feature/browser/`: browser chrome, tabs and tab switcher.
- `feature/me/`: settings, Android permission/status surfaces and running-session presentation.
- `docs/`: maintained technical and release documentation.

Do not place production code in generated `build/` directories, screenshots, or release artifact folders.

## Setup and commands

Requirements: JDK 17 and Android SDK 37. Use the checked-in Gradle Wrapper; do not depend on a globally installed Gradle.

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat testDebugUnitTest :app:assembleDebug
```

macOS/Linux:

```bash
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest :app:assembleDebug
```

Do not run a release build for publication unless the release credential described in `docs/RELEASE.md` is available. Never replace it with the debug keystore.

## Architecture rules

- Use Kotlin for new production code and tests.
- Follow unidirectional data flow: immutable UI state flows down; user events flow up.
- ViewModels expose read-only `StateFlow` and launch asynchronous work in `viewModelScope`.
- Composables do not access DAO, DataStore, raw WebView, or foreground services directly.
- Repository classes own persistence decisions. DAO calls stay out of UI modules.
- Use Hilt constructor injection; do not introduce service locators or global mutable singletons.
- Keep Android framework objects out of long-lived state unless a dedicated lifecycle owner controls them.
- Use `collectAsStateWithLifecycle` for lifecycle-aware Compose collection.
- Prefer immutable models and explicit state transitions over loosely related Boolean flags.
- Network, HTML parsing and disk I/O must not run on the main thread.

## Compose and responsive UI

- Measure from the composable's available constraints, not physical-device assumptions.
- Every launcher cell has deterministic width/height. Remote image intrinsic size must never determine grid geometry.
- Keep interactive targets at least 48dp where practical and provide meaningful content descriptions.
- Modifier order is behavioral. Review constraint, input, semantics and drawing order whenever modifiers change.
- Hoist state when another component owns the decision. Keep transient drawing state local only when it cannot affect business persistence.
- Avoid recomposition-driven side effects. Use `LaunchedEffect`, `DisposableEffect`, `SideEffect`, or a lifecycle observer according to ownership.
- Do not add ornamental animation that changes layout bounds during interaction.

## Launcher invariants

Changes in `feature/home` must preserve all of the following:

- Page capacity is `gridRows * gridColumns`, minimum 1.
- Icon gestures are a single-channel `awaitEachGesture` state machine: tap and long-press are mutually exclusive; after the long-press menu opens, only accumulated movement beyond 16dp (`DRAG_START_THRESHOLD`, Launcher3 `deep_shortcuts_start_drag_threshold`) enters drag, and releasing within 16dp keeps the menu open.
- The Add cell occupies a real slot on the final page; a full final page creates a new page.
- A dragged source cell retains its measured place and only changes visual treatment.
- The floating drag icon is rendered outside `HorizontalPager`/`LazyVerticalGrid` measurement.
- The drag registration point remains stable; the icon must not jump its center under the finger.
- Pager gestures are disabled during an active drag.
- A normal drop reorders; the centered, timed hotspot creates/adds to a folder.
- Edge-hover page changes clear stale target state; hovering the last page's right edge or the first page's left edge (~900ms) inserts a temporary blank page on that side (dropping on the left one prepends a new first page via `HomePages.resolvePrependMove`, shifting every other entity's `homePage` by +1) that is discarded on drop/cancel if nothing lands on it.
- The drag tracking/drop session loop lives at the root container layer (`homeDragSession` in `HomeGestures.kt`), never on the cell composable: cells are disposed when their pager page leaves composition, and a gesture coroutine hosted on a cell would be cancelled mid-drag (state wipe + temp-page bounce-back).
- All interaction session state lives in `HomeInteractionState`; gesture lambdas/coroutines capture only that stable holder (no stale composition snapshots). After writing `tempPageSide`, wait for recomposition frames (`withFrameNanos`) before scrolling the pager — `pageCount` only takes effect after recomposition and a same-frame scroll is coerced back to the old range.
- The edge-hover page-turn state machine is a single internal loop observing `edgeDirection` via `snapshotFlow` (in `HomeDragEffects`), not a key-restarted `LaunchedEffect` — a synchronously re-armed direction is invisible to composition keys, so a restart-based effect never fires twice.
- Edit (jiggle) mode never shows the long-press menu: icons enter drag immediately once movement passes touchSlop, and a release without drag toggles the selection checkmark.
- The home screen supports two scroll modes switchable in settings (`homeScrollMode`): horizontal pager (default) and a single vertical scrolling list. Both modes share the same `(homePage, homeCellIndex)` data model (vertical mode maps flat indices back via `page = index / capacity`, `slot = index % capacity`) and the same drag invariants (floating layer, gesture mutual exclusion, drop-state cleanup).
- Drop/cancel clears every drag state value.
- Persisted `page/index` values are dense and deterministic after a move.

When changing paging or ordering, add/update unit tests in `feature/home/src/test` and manually test with at least two apps. For cross-page changes, test more than one full page.

## Browser and WebView invariants

- A WebView has one explicit owner. Detach listeners and views when ownership changes.
- Compose callbacks that outlive a recomposition must read current state (`rememberUpdatedState` or equivalent), never a stale tab/session ID.
- Update host listeners when the effective listener changes; do not bind only at initial creation.
- Preserve back/forward semantics and a reachable tab switcher on compact widths.
- Treat page titles, URLs, icons, HTML and JavaScript messages as untrusted input.
- Never silently call `SslErrorHandler.proceed()`. Any override must be an explicit, informed user action and must not be remembered globally.
- Do not disable certificate, hostname or Safe Browsing checks to make a site load.
- Restrict privileged JavaScript bridges. Do not expose Android objects to arbitrary remote origins.
- External schemes and downloads must be validated before handing them to another app.
- Clear or isolate data deliberately; understand that some CookieManager/WebView behaviors are process-global.
- WebView callbacks are attributed by sessionId and update that session's per-tab state; the ephemeral UI listener must not write another tab's state.

## Data and migrations

- Room schema changes require an explicit migration or a documented destructive-development exception.
- DataStore keys are compatibility contracts; renaming a key requires migration/fallback behavior.
- Preserve existing user ordering, folder membership and site settings when changing models.
- URL normalization must be deterministic and covered by tests.
- Do not log browsing contents, cookies, credentials, complete query strings, keystore paths or signing values.

## Android platform behavior

- Request runtime permissions through Android APIs and refresh status after returning from Settings.
- Foreground services must have a user-visible purpose and notification.
- Never promise permanent background execution. Doze, OEM policy, memory pressure and site throttling remain authoritative.
- Use current platform APIs with guarded fallbacks for supported API 29+ devices.
- Do not hardcode user-facing strings in new UI; add resources when extending production copy.

## Security and secrets

- Never commit `.jks`, `.keystore`, `.p12`, credentials, tokens, local signing properties or generated release artifacts.
- Release secrets are passed through process-scoped environment variables by `scripts/build-release.ps1` and cleared in `finally`.
- The canonical release keystore lives outside the repository. Losing it prevents signature-compatible upgrades; leaking it compromises every release.
- Public certificates and SHA-256 checksums may be published. Private keys and passwords may not.
- Before staging, inspect `git status --short` and staged diff for secrets and generated files.
- Report suspected vulnerabilities according to `SECURITY.md`; do not open a public issue for sensitive details.

## Testing matrix

Run the narrowest relevant checks during iteration, then the required final checks:

| Change area | Required checks |
|---|---|
| Documentation only | Link/path review; no Gradle build required unless commands changed |
| `feature/home` | `:feature:home:testDebugUnitTest :app:assembleDebug` |
| `feature/add` or parsing | `:feature:add:testDebugUnitTest :app:assembleDebug` |
| Browser/WebView/session | `testDebugUnitTest :app:assembleDebug` plus manual tab/session smoke test |
| Room/DataStore | Relevant unit tests, migration review, `testDebugUnitTest :app:assembleDebug` |
| Theme/layout | `:app:assembleDebug` plus compact-width and standard-width visual check |
| Release/signing | Full tests, signed release build, `apksigner verify --verbose --print-certs`, SHA-256 generation |

Do not claim success if a required check was skipped. State exactly what ran and what remains manual.

## Git workflow

- `main` is stable and releaseable. Do not develop directly on it after repository bootstrap.
- `dev` is the integration branch.
- Branch from `dev` using `feat/<topic>`, `fix/<topic>`, `refactor/<topic>`, `test/<topic>` or `docs/<topic>`.
- Use Conventional Commits: `type(scope): imperative summary`, with a summary under 72 characters.
- Keep one logical concern per commit. Do not mix formatting, generated artifacts and behavior changes.
- Never force-push `main`; do not bypass hooks; do not rewrite shared history unless the maintainer explicitly requests it.
- Pull requests target `dev`. Release PRs merge `dev` into `main` after required checks.
- Tags use semantic versions (`vMAJOR.MINOR.PATCH`) and point to a commit already on `main`.

## Release rules

Follow `docs/RELEASE.md` exactly. A release is gated on explicit user approval of a debug build carrying the same version number (`docs/VERSIONING.md` §5); never start this flow before that approval:

1. Update `versionCode`, `versionName` and `CHANGELOG.md`.
2. Ensure `dev` is clean and all required checks pass.
3. Merge/synchronize the release commit to `main` without rewriting history.
4. Build with the repository script and the canonical external credential.
5. Verify APK signatures and checksum before upload.
6. Create and push an annotated tag.
7. Create a GitHub Release whose notes cover every version since the previous published release, and attach APK, checksum and public certificate.
8. Verify remote branches, tag, release assets and local final branch.
9. Sync `dev` with `main`, push, and resume development on `dev`.

Never modify an APK after signing. Never publish an unsigned or debug-signed APK as a production Release asset.

## Definition of done

A change is complete only when:

- behavior and edge cases match the request;
- architecture and invariants above remain true;
- tests proportional to the change pass;
- documentation is updated when commands, behavior or constraints change;
- no secrets/generated build outputs are staged;
- the final summary names changed areas, checks run and known limitations.

## Project-specific specifications

The following documents are authoritative and apply to every change in their domain:

- `docs/DESIGN.md`: visual design baseline — Apple-style pure white/pure black color system, theme modes (system/light/dark/photo wallpaper), iOS Liquid Glass baseline for bottom navigation and floating layers, and the component-library selection principle (prefer mature open-source libraries, one unified library per capability, no reinventing the wheel).
- `docs/VERSIONING.md`: version numbering rules — counting restarted from 0.0.0 (versionCode 1); the default and ONLY automatic increment is the third digit +1, with no carry (0.1.1000 → 0.1.1001); the first and second digits change ONLY on explicit user instruction; every version change must sync `CHANGELOG.md` and the in-app update log page. Releases follow the debug-first workflow (§5): a release ships only after the user approves the debug build of that exact version, rejected debug iterations consume their numbers permanently, and the GitHub Release notes must aggregate every version since the previous published release.
- `docs/PERFORMANCE.md`: performance budgets — at most one live blur backdrop (the bottom bar), blur radius ≤ 24dp equivalent, glass/wallpaper layers never affect measurement, background-thread image decoding and palette extraction, graceful degradation on API 29–32.

## Normative references

- AGENTS.md open format: https://agents.md/
- Codex AGENTS.md discovery: https://developers.openai.com/codex/guides/agents-md
- Android app signing: https://developer.android.com/studio/publish/app-signing
- Android command-line release builds: https://developer.android.com/build/building-cmdline
- `apksigner`: https://developer.android.com/tools/apksigner
