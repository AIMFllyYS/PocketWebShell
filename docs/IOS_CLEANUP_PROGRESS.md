# 0.1.15 unattended implementation ledger

## Scope locked by user

- Clean obsolete UI, share production primitives and expose every family through the Playbook.
- Smaller page titles/settings text; fonts MiSans (default), system, Noto Sans SC; app size 90–130% in 5% steps, independent of website zoom.
- Browser top one-row menu/address/tabs. Reading gesture collapses global Dock to a 52dp glass orb. Tap orb/edge handle restores full Dock and address bar, no keyboard. No timers. Drag orb to edge to park it; Add/Me Dock unchanged.
- One live Haze surface max, no remeasurement from bottom chrome; preserve sessionId routing and home drag invariants.
- Remove Add website text-size UI but preserve stored textZoomPercent and existing runtime behavior; new sites default100.
- Build 0.1.15 / code16 Debug only, full tests, inspect pages/Playbook at compact/standard and API30/31/35 if available; document limitations honestly.

## Baseline and ownership

- Branch `refactor/ios-ui-cleanup`, baseline commit `335537b` preserves pre-existing 0.1.14 working tree.
- Root: app composition/Dock/Playbook host, single-site Shell cleanup, version/docs, build/device tests, final verification.
- Design/font worker: core/designsystem, additive data/model preferences, feature/me (except UpdateLog contents), font assets/notice.
- Home/add worker: feature/home + feature/add cleanup, shared component adapters and real catalog entries.
- Browser worker: feature/browser + core/webengine (reading/insets/lifecycle), chrome reducer and real catalog entries.

## Checkpoints

- [x] Read contracts and protected previous changes in local baseline commit.
- [x] Production cleanup, font configuration, browser state/host integration.
- [x] Full production-backed Playbook plus registry coverage tests, including localized catalog actions and viewport-aware UI automation.
- [x] Compile, unit tests, lint, API35 screenshots/gestures. The final API35 connected suite is green at 20/20, including the browser Orb edge-parking path.
- [ ] API30/31 fallback checks. No API30/31 device or emulator is available in this worktree environment, so this remains explicitly unverified.
- [x] Independent source review, fixes, version sync, Debug APK and SHA-256. Release signing and publication are intentionally out of scope; this is a debug-only iteration.

## Verification note

- Final source/build verification was performed from this worktree. `testDebugUnitTest`, `:feature:home:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug` passed. `:app:connectedDebugAndroidTest` passed 20/20 on `webshell-ios-qa-api35` (API 35); the browser edge-parking test also passed twice in isolation without a test-only pause.
- API30/31 fallback smoke checks remain unrun because no API30/31 device or emulator is available here; this is the only platform-matrix gap.
- The catalog and font-page test fixes are kept in the instrumented test harness because they improve the test's observation of the real production UI; they do not bypass production behavior.

## Debug artifact

- `build/ios15/PocketWebShell-0.1.15-debug.apk` — `96,116,542` bytes, package `com.webshell.app`, `versionName=0.1.15`, `versionCode=16`.
- SHA-256: `14FD8DF840586098A13903ACCC06F36FA3DD3241B7961A63B59C1E3E53A65EDE` (also written to the adjacent `.sha256` file).

Routine heartbeat updates remain quiet until completion, a material problem, or user action needed. No release build/main merge/push/tag is authorized by this task.
