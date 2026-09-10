# Core browser validation record

## Build environment

- `compileSdk`: 37 (Android API 37 / platform code 17)
- `targetSdk`: 36
- `minSdk`: 29
- AndroidX WebKit: 1.17.0
- Debug artifact: `app/build/outputs/apk/debug/app-debug.apk`
- APK package/version: `com.webshell.app`, `versionCode=24`, `versionName=0.1.23`
- APK SHA-256: `5B8F6140D698B50C032A1CB702A36E5F5F1FE1B6DEB5F92C26785B1F4FD42DF0`
- APK size: `95,852,104` bytes; `zipalign -c -v 4` passed

The runtime WebView provider/version and `MULTI_PROFILE` result are intentionally read from
`WebViewCapabilities.snapshot()` on the device. This workspace has no connected device or
emulator (`adb` is not installed and `connectedDebugAndroidTest` reports `No connected devices`),
so no provider/version is claimed here.

## Automated checks

| Check | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS (all modules, 189 tests; 0 failures/skips) |
| `./gradlew.bat :app:assembleDebug` | PASS |
| `./gradlew.bat :app:lintDebug` | PASS (0 errors; existing dependency/API warnings only) |
| `./gradlew.bat connectedDebugAndroidTest` | BLOCKED: no connected devices |

The unit suite includes URL scheme/Intent policy, local app path and encoded-URL boundaries,
Blob callback parsing and renderer-recovery gating, metadata private-IP and scheme validation,
new-window identity, DownloadManager header/file-name policy, WebViewPool protection composition,
pooled config merging, storage accounting, launcher geometry/gestures and existing
backup/migration coverage.

## Device/manual matrix

The following require a real Android System WebView and are not marked as passed without a device:

- Bilibili/OIDC (Google and Microsoft) login, cross-tab and saved-site cookie reuse;
- HTTP cleartext failure copy and HTTPS redirect behavior;
- true `target=_blank` tab interaction and OAuth `window.open` return;
- file chooser multi-select/capture/cancellation during tab switches;
- camera, microphone and geolocation prompts/denials by origin;
- Bilibili/video full-screen enter/exit and back behavior;
- authenticated DownloadManager and Blob download delivery;
- renderer crash/recovery, activity recreation and six-plus-session eviction;
- SPA title/thumbnail refresh, process-restart thumbnail fallback;
- shared-cache clearing without logout and explicit all-WebView-data logout warning;
- JavaScript `alert` / `confirm` / `prompt` / `beforeunload` dialogs on visible sessions;
- app-A local resource access to app-B paths.

Use the developer page's WebView capability snapshot and this matrix when a device becomes
available. No release signing material or generated APK was staged in the repository.
