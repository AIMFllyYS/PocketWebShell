# Core-browser follow-up issues

This document is the issue-ready backlog for capabilities intentionally kept out of the
single-user/shared-profile critical path. Each item is independent and should be opened as a
GitHub issue with the labels shown below. The dependency anchor for every item is
`core-browser-session`.

## P2 · browser and webengine

| Issue | Labels | User value | Acceptance criteria | Architecture / privacy notes |
|---|---|---|---|---|
| Web Push and page notifications | `area: browser`, `area: webengine`, `priority: P2` | Receive useful site notifications | Per-origin permission, revocation, foreground/background delivery and notification-channel tests | Requires a service-worker policy; never grant globally |
| Web Bluetooth / NFC / USB / serial | `area: browser`, `area: webengine`, `priority: P2` | Use compatible hardware sites | Explicit user gesture, device/origin binding, cancellation and disconnect recovery | Android capability and privacy prompts must remain authoritative |
| Media Session and Picture-in-Picture | `area: browser`, `priority: P2` | Control audio/video outside the page | Metadata/action callbacks, lock-screen controls, PiP enter/exit and back handling | Must not keep an invisible session alive without a user-visible reason |
| Multi-window and cross-process restoration | `area: browser`, `area: webengine`, `priority: P2` | Restore work after process death | Restore every tab's URL/history/thumbnail and preserve shared cookies | Does not change the shared-profile policy |
| Download center and resumable downloads | `area: browser`, `area: webengine`, `priority: P2` | Find, pause and resume downloads | Persistent queue, headers/cookies, cancellation, retry and notification tests | Never log cookies or complete query strings |
| Print / save as PDF / reader mode / translation | `area: browser`, `priority: P2` | Read and export pages | Explicit action, progress, cancellation and output validation | PDF files must be shared through FileProvider |
| Content scripts, user scripts and extensions | `area: browser`, `area: webengine`, `priority: P2` | Customize compatible sites | Origin-scoped permissions, isolated worlds and uninstall cleanup | High-risk surface; no arbitrary Android bridge |
| Search-engine choice, sync and multiple accounts | `area: browser`, `area: storage`, `priority: P2` | Personalize and synchronize browsing | Separate account/profile lifecycle and migration tests | Requires an explicit expansion beyond one shared identity |
| Incognito and site-level temporary sessions | `area: browser`, `area: storage`, `priority: P2` | Browse without persistent site data | No cookie/history persistence, crash cleanup and clear UI state | Must never be emulated by deleting shared data |
| Profile import/export | `area: storage`, `area: webengine`, `priority: P2` | Move a browser profile | Encrypted export, versioning, migration and secret-handling tests | Explicitly separate from local-app file boundaries |

## P2 · preview enhancements

| Issue | Labels | Acceptance criteria |
|---|---|---|
| Full offline webpage snapshots | `area: preview`, `priority: P2` | Snapshot is bounded, origin-safe, restorable and clearly marked stale |
| Long screenshots and responsive preview sizes | `area: preview`, `priority: P2` | Memory-bounded capture, cancellation and deterministic dimensions |
| Authenticated/OG/video preview cards | `area: preview`, `priority: P2` | User opt-in, no credential leakage, stale-task version checks |

## P3 · experience and diagnostics

| Issue | Labels | Acceptance criteria |
|---|---|---|
| WebView compatibility report and update guidance | `area: browser`, `area: webengine`, `priority: P3` | Provider/version, feature matrix and actionable upgrade path |
| Network request and load-quality diagnostics | `area: webengine`, `priority: P3` | Redacted host/status/timing only; no cookies/query strings |
| Renderer/download failure telemetry | `area: webengine`, `priority: P3` | Opt-in, anonymous, bounded retention and local export |
| Device compatibility matrix and performance panel | `area: browser`, `priority: P3` | API/WebView matrix, memory/latency budgets and graceful degradation |

None of these issues should introduce a second implicit CookieManager or a per-site profile. Any
proposal that changes that invariant must first update `core-browser-session` and the privacy copy.
